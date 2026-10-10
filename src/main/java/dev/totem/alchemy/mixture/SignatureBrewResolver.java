package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure, deterministic scheduling planner for special multi-material reactions.
 *
 * <p>Only reactions that are still pending may be reserved. The returned groups reference
 * existing {@link AlchemyMixtureState.Reaction#id() reaction IDs}, retaining their original
 * independent timers; resolving never advances reactions, consumes inputs, or applies
 * ordinary/special outputs. A later scheduling task must atomically persist reservations
 * and settle each group's result exactly once.</p>
 */
public final class SignatureBrewResolver {
    private static final double EPSILON = 1.0E-6D;

    private SignatureBrewResolver() {
    }

    /** Returns the highest-priority eligible reaction group, without mutating the mixture. */
    public static Optional<ReactionGroup> resolve(AlchemyMixtureState mixture, List<Signature> candidates) {
        return planGroups(mixture, candidates).stream().findFirst();
    }

    /**
     * Selects compatible candidate groups in priority/ID order. Each pending reaction ID
     * is reserved at most once, so overlapping signatures cannot double-spend materials.
     * Unreserved reactions remain available to the ordinary brewing scheduler.
     */
    public static List<ReactionGroup> planGroups(AlchemyMixtureState mixture, List<Signature> candidates) {
        if (mixture == null || mixture.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<AlchemyMixtureState.Reaction> pending = mixture.reactions().stream()
                .filter(reaction -> !reaction.complete())
                .sorted(Comparator.comparing(AlchemyMixtureState.Reaction::id))
                .toList();
        if (pending.isEmpty()) {
            return List.of();
        }

        Set<String> reserved = new HashSet<>();
        List<ReactionGroup> groups = new ArrayList<>();
        for (Signature signature : candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Signature::priority).reversed()
                        .thenComparing(candidate -> candidate.id().toString()))
                .toList()) {
            if (!signature.matchesLiquids(mixture)) {
                continue;
            }
            List<String> members = new ArrayList<>();
            boolean matched = true;
            for (Identifier required : signature.requiredIngredients().stream()
                    .sorted(Comparator.comparing(Identifier::toString))
                    .toList()) {
                String reactionId = pending.stream()
                        .filter(reaction -> reaction.ingredientId().equals(required.toString()))
                        .map(AlchemyMixtureState.Reaction::id)
                        .filter(id -> !reserved.contains(id) && !members.contains(id))
                        .findFirst()
                        .orElse(null);
                if (reactionId == null) {
                    matched = false;
                    break;
                }
                members.add(reactionId);
            }
            if (matched) {
                ReactionGroup group = new ReactionGroup(signature.id(), members);
                groups.add(group);
                reserved.addAll(group.memberReactionIds());
            }
        }
        return List.copyOf(groups);
    }

    /**
     * A signature's liquid requirements are conditions, not separately ticking inputs.
     * Required ingredients must each have an in-flight material reaction when planned.
     */
    public record Signature(
            Identifier id,
            int priority,
            Map<Identifier, Double> minimumLiquidFractions,
            Set<Identifier> requiredIngredients
    ) {
        public Signature {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(minimumLiquidFractions, "minimumLiquidFractions");
            Objects.requireNonNull(requiredIngredients, "requiredIngredients");
            if (requiredIngredients.isEmpty()) {
                throw new IllegalArgumentException("Signature must reserve at least one material reaction");
            }
            Map<Identifier, Double> liquids = new LinkedHashMap<>();
            minimumLiquidFractions.forEach((liquid, fraction) -> {
                Objects.requireNonNull(liquid, "liquid identifier");
                if (fraction == null || !Double.isFinite(fraction) || fraction <= 0.0D || fraction > 1.0D) {
                    throw new IllegalArgumentException("Invalid minimum liquid fraction for " + liquid);
                }
                liquids.put(liquid, fraction);
            });
            if (liquids.values().stream().mapToDouble(Double::doubleValue).sum() > 1.0D + EPSILON) {
                throw new IllegalArgumentException("Minimum liquid fractions exceed 100%");
            }
            requiredIngredients.forEach(ingredient -> Objects.requireNonNull(ingredient, "ingredient"));
            minimumLiquidFractions = Map.copyOf(liquids);
            requiredIngredients = Set.copyOf(requiredIngredients);
        }

        boolean matchesLiquids(AlchemyMixtureState mixture) {
            for (var liquid : minimumLiquidFractions.entrySet()) {
                if (mixture.liquidComposition().amount(liquid.getKey()) + EPSILON < liquid.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Immutable, unresolved reservation plan; does not yet change runtime reaction ownership. */
    public record ReactionGroup(Identifier signatureId, List<String> memberReactionIds) {
        public ReactionGroup {
            Objects.requireNonNull(signatureId, "signatureId");
            Objects.requireNonNull(memberReactionIds, "memberReactionIds");
            if (memberReactionIds.isEmpty()
                    || memberReactionIds.stream().anyMatch(id -> id == null || id.isBlank())
                    || new HashSet<>(memberReactionIds).size() != memberReactionIds.size()) {
                throw new IllegalArgumentException("Reaction group requires distinct, nonblank members");
            }
            memberReactionIds = memberReactionIds.stream().sorted().toList();
        }

        public boolean owns(String reactionId) {
            return memberReactionIds.contains(reactionId);
        }
    }
}
