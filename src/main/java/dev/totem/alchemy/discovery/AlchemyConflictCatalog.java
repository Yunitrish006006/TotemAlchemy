package dev.totem.alchemy.discovery;

import dev.totem.alchemy.mixture.AlchemyMixtureState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical opposing-effect rules shared by chemistry resolution, discovery, and the Totem manual. */
public final class AlchemyConflictCatalog {
    public static final String DISCOVERY_PREFIX = "reaction_conflict:";

    private static final List<Entry> ENTRIES = List.of(
            new Entry("speed_slowness",
                    List.of("minecraft:speed"), "minecraft:slowness",
                    "effect.minecraft.speed", "effect.minecraft.slowness"),
            new Entry("healing_harming",
                    List.of("minecraft:instant_health"), "minecraft:instant_damage",
                    "effect.minecraft.instant_health", "effect.minecraft.instant_damage"),
            new Entry("strength_weakness",
                    List.of("minecraft:strength", "deadrecall:firefly_strength", "totem:alchemy/firefly_strength"),
                    "minecraft:weakness",
                    "book.totem_alchemy.reaction.strength_family", "effect.minecraft.weakness"),
            new Entry("regeneration_poison",
                    List.of("minecraft:regeneration"), "minecraft:poison",
                    "effect.minecraft.regeneration", "effect.minecraft.poison")
    );

    private AlchemyConflictCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static String relationKey(Entry entry) {
        return DISCOVERY_PREFIX + entry.id();
    }

    public static String resolutionKey(Entry entry, Resolution resolution) {
        return relationKey(entry) + ":" + resolution.id();
    }

    /** Detect conflicts created by combining two real mixture states and classify the post-neutralization result. */
    public static List<Observation> observe(
            AlchemyMixtureState first,
            AlchemyMixtureState second,
            AlchemyMixtureState resolved
    ) {
        Map<String, AlchemyMixtureState.EffectDose> raw = new LinkedHashMap<>();
        mergeEffects(raw, first == null ? Map.of() : first.effects());
        mergeEffects(raw, second == null ? Map.of() : second.effects());
        return observeRaw(raw, resolved == null ? Map.of() : resolved.effects());
    }

    /** Detect conflicts inside a raw independently-selected effect set using the same chemistry resolver as gameplay. */
    public static List<Observation> observeRaw(Map<String, AlchemyMixtureState.EffectDose> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        AlchemyMixtureState synthetic = new AlchemyMixtureState(1);
        synthetic.addEffects(raw);
        return observeRaw(raw, synthetic.effects());
    }

    public static List<Observation> observeRaw(
            Map<String, AlchemyMixtureState.EffectDose> raw,
            Map<String, AlchemyMixtureState.EffectDose> resolved
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, AlchemyMixtureState.EffectDose> finalEffects = resolved == null ? Map.of() : resolved;
        List<Observation> observations = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            boolean rawPositive = entry.positiveEffectIds().stream().anyMatch(raw::containsKey);
            boolean rawNegative = raw.containsKey(entry.negativeEffectId());
            if (!rawPositive || !rawNegative) {
                continue;
            }
            boolean positiveRemains = entry.positiveEffectIds().stream().anyMatch(finalEffects::containsKey);
            boolean negativeRemains = finalEffects.containsKey(entry.negativeEffectId());
            Resolution resolution;
            if (positiveRemains && !negativeRemains) {
                resolution = Resolution.POSITIVE_REMAINS;
            } else if (negativeRemains && !positiveRemains) {
                resolution = Resolution.NEGATIVE_REMAINS;
            } else {
                resolution = Resolution.COMPLETE;
            }
            observations.add(new Observation(entry, resolution));
        }
        return List.copyOf(observations);
    }

    public static void mergeEffects(
            Map<String, AlchemyMixtureState.EffectDose> target,
            Map<String, AlchemyMixtureState.EffectDose> additions
    ) {
        if (target == null || additions == null) {
            return;
        }
        additions.forEach((id, dose) -> {
            if (id != null && !id.isBlank() && dose != null && dose.potencyTicks() > 0.0001D) {
                target.merge(id, dose, AlchemyMixtureState.EffectDose::merge);
            }
        });
    }

    public record Entry(
            String id,
            List<String> positiveEffectIds,
            String negativeEffectId,
            String positiveNameKey,
            String negativeNameKey
    ) {
        public Entry {
            positiveEffectIds = List.copyOf(positiveEffectIds);
        }
    }

    public record Observation(Entry entry, Resolution resolution) {
    }

    public enum Resolution {
        COMPLETE("complete"),
        POSITIVE_REMAINS("positive_remains"),
        NEGATIVE_REMAINS("negative_remains");

        private final String id;

        Resolution(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
