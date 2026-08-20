package dev.totem.alchemy.discovery;

/** Research confidence shown to players. True probabilities are never exposed by this enum. */
public enum AlchemyResearchTier {
    NOVICE("book.totem_alchemy.research.tier.novice", 0),
    BEGINNER("book.totem_alchemy.research.tier.beginner", 1),
    FAMILIAR("book.totem_alchemy.research.tier.familiar", 2),
    SKILLED("book.totem_alchemy.research.tier.skilled", 3),
    EXPERT("book.totem_alchemy.research.tier.expert", 4),
    MASTER("book.totem_alchemy.research.tier.master", 5);

    private final String translationKey;
    private final int rank;

    AlchemyResearchTier(String translationKey, int rank) {
        this.translationKey = translationKey;
        this.rank = rank;
    }

    public String translationKey() {
        return translationKey;
    }

    public static AlchemyResearchTier classify(int samples, double absoluteError) {
        AlchemyResearchTier byError = absoluteError <= 0.01D ? MASTER
                : absoluteError <= 0.05D ? EXPERT
                : absoluteError <= 0.10D ? SKILLED
                : absoluteError <= 0.20D ? FAMILIAR
                : absoluteError <= 0.35D ? BEGINNER
                : NOVICE;

        int sampleCap = samples >= 40 ? MASTER.rank
                : samples >= 20 ? EXPERT.rank
                : samples >= 12 ? SKILLED.rank
                : samples >= 6 ? FAMILIAR.rank
                : samples >= 3 ? BEGINNER.rank
                : NOVICE.rank;
        int finalRank = Math.min(byError.rank, sampleCap);
        for (AlchemyResearchTier tier : values()) {
            if (tier.rank == finalRank) {
                return tier;
            }
        }
        return NOVICE;
    }
}
