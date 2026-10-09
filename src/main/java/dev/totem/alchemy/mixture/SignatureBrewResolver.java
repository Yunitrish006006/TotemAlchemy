package dev.totem.alchemy.mixture;

import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, data-oriented selection boundary for signature brews.
 *
 * <p>M11-T01 intentionally introduces no gameplay changes. M11-T02 will load
 * candidates from datapacks; later tasks will apply the winning signature.</p>
 */
public final class SignatureBrewResolver {
    private SignatureBrewResolver() {
    }

    public static Optional<Signature> resolve(AlchemyMixtureState mixture, List<Signature> candidates) {
        if (mixture == null || mixture.isEmpty() || candidates == null) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.matches(mixture))
                .sorted(Comparator.comparingInt(Signature::priority).reversed()
                        .thenComparing(candidate -> candidate.id().toString()))
                .findFirst();
    }

    /** Immutable signature candidate; conditions will be expanded by M11-T02. */
    public record Signature(Identifier id, int priority, String requiredProvenance) {
        public Signature {
            Objects.requireNonNull(id, "id");
            if (requiredProvenance == null || requiredProvenance.isBlank()) {
                throw new IllegalArgumentException("requiredProvenance must not be blank");
            }
        }

        public boolean matches(AlchemyMixtureState mixture) {
            return mixture != null && !mixture.isEmpty()
                    && mixture.hasProvenance(requiredProvenance);
        }
    }
}
