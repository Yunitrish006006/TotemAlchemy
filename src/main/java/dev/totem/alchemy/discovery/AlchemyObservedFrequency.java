package dev.totem.alchemy.discovery;

/** Player-visible frequency band derived from the player's own observed results. */
public enum AlchemyObservedFrequency {
    VERY_RARE("book.totem_alchemy.research.frequency.very_rare"),
    RARE("book.totem_alchemy.research.frequency.rare"),
    OCCASIONAL("book.totem_alchemy.research.frequency.occasional"),
    COMMON("book.totem_alchemy.research.frequency.common"),
    FREQUENT("book.totem_alchemy.research.frequency.frequent");

    private final String translationKey;

    AlchemyObservedFrequency(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static AlchemyObservedFrequency classify(double observedRate) {
        if (observedRate < 0.10D) {
            return VERY_RARE;
        }
        if (observedRate < 0.25D) {
            return RARE;
        }
        if (observedRate < 0.45D) {
            return OCCASIONAL;
        }
        if (observedRate < 0.65D) {
            return COMMON;
        }
        return FREQUENT;
    }
}
