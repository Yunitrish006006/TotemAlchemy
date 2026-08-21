package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/** Selects one shared independently rolled result set for every compatible bottle in a brewing-stand batch. */
public final class MultiOutcomeBrewing {
    private static final ThreadLocal<BatchOutcome> ACTIVE_BATCH = new ThreadLocal<>();
    private static final ThreadLocal<Integer> LEGACY_PROBABILITY_READS = ThreadLocal.withInitial(() -> 0);
    private static final Map<Item, OutcomePool> AWKWARD_POOLS = Map.ofEntries(
            Map.entry(Items.SPIDER_EYE, new OutcomePool(List.of(
                    outcome(Potions.POISON, "poison"), outcome(Potions.WEAKNESS, "weakness")))),
            Map.entry(Items.RED_MUSHROOM, new OutcomePool(List.of(
                    outcome(Potions.POISON, "poison"), outcome(AlchemyPotions.SATURATION, "saturation")))),
            Map.entry(Items.GLISTERING_MELON_SLICE, new OutcomePool(List.of(
                    outcome(Potions.HEALING, "healing"), outcome(AlchemyPotions.RESISTANCE, "resistance")))),
            Map.entry(Items.SUGAR, new OutcomePool(List.of(
                    outcome(Potions.SWIFTNESS, "swiftness"), outcome(Potions.SLOWNESS, "slowness"),
                    outcome(AlchemyPotions.SATURATION, "saturation")))),
            Map.entry(Items.RABBIT_FOOT, new OutcomePool(List.of(
                    outcome(Potions.LEAPING, "leaping"), outcome(Potions.SLOW_FALLING, "slow_falling"),
                    outcome(Potions.SWIFTNESS, "swiftness")))),
            Map.entry(Items.MAGMA_CREAM, new OutcomePool(List.of(
                    outcome(Potions.FIRE_RESISTANCE, "fire_resistance"), outcome(AlchemyPotions.RESISTANCE, "resistance"),
                    outcome(Potions.STRENGTH, "strength")))),
            Map.entry(Items.GOLDEN_CARROT, new OutcomePool(List.of(
                    outcome(Potions.NIGHT_VISION, "night_vision"), outcome(Potions.INVISIBILITY, "invisibility"),
                    outcome(Potions.HEALING, "healing")))),
            Map.entry(Items.BLAZE_POWDER, new OutcomePool(List.of(
                    outcome(Potions.STRENGTH, "strength"), outcome(Potions.FIRE_RESISTANCE, "fire_resistance"),
                    outcome(Potions.HARMING, "harming")))),
            Map.entry(Items.GHAST_TEAR, new OutcomePool(List.of(
                    outcome(Potions.REGENERATION, "regeneration"), outcome(Potions.HEALING, "healing"),
                    outcome(Potions.WEAKNESS, "weakness")))),
            Map.entry(Items.PUFFERFISH, new OutcomePool(List.of(
                    outcome(Potions.WATER_BREATHING, "water_breathing"), outcome(Potions.POISON, "poison"),
                    outcome(Potions.WEAKNESS, "weakness")))),
            Map.entry(Items.TURTLE_HELMET, new OutcomePool(List.of(
                    outcome(Potions.TURTLE_MASTER, "turtle_master"), outcome(Potions.WATER_BREATHING, "water_breathing"),
                    outcome(AlchemyPotions.RESISTANCE, "resistance")))),
            Map.entry(Items.PHANTOM_MEMBRANE, new OutcomePool(List.of(
                    outcome(Potions.SLOW_FALLING, "slow_falling"), outcome(Potions.INVISIBILITY, "invisibility"),
                    outcome(Potions.NIGHT_VISION, "night_vision")))),
            Map.entry(Items.BREEZE_ROD, new OutcomePool(List.of(
                    outcome(Potions.WIND_CHARGED, "wind_charged"), outcome(Potions.SLOW_FALLING, "slow_falling"),
                    outcome(Potions.LEAPING, "leaping")))),
            Map.entry(Items.SLIME_BLOCK, new OutcomePool(List.of(
                    outcome(Potions.OOZING, "oozing"), outcome(Potions.LEAPING, "leaping"),
                    outcome(Potions.SLOWNESS, "slowness")))),
            Map.entry(Items.STONE, new OutcomePool(List.of(
                    outcome(Potions.INFESTED, "infested"), outcome(AlchemyPotions.RESISTANCE, "resistance"),
                    outcome(Potions.SLOWNESS, "slowness")))),
            Map.entry(Items.COBWEB, new OutcomePool(List.of(
                    outcome(Potions.WEAVING, "weaving"), outcome(Potions.SLOWNESS, "slowness"),
                    outcome(Potions.WEAKNESS, "weakness")))),
            Map.entry(Items.FERMENTED_SPIDER_EYE, new OutcomePool(List.of(
                    outcome(Potions.WEAKNESS, "weakness"), outcome(Potions.HARMING, "harming"),
                    outcome(Potions.INVISIBILITY, "invisibility")))),
            Map.entry(Items.MELON_SLICE, new OutcomePool(List.of(
                    outcome(AlchemyPotions.SATURATION, "saturation"), outcome(Potions.HEALING, "healing")))),
            Map.entry(Items.APPLE, new OutcomePool(List.of(
                    outcome(AlchemyPotions.SATURATION, "saturation"), outcome(Potions.HEALING, "healing")))),
            Map.entry(Items.SWEET_BERRIES, new OutcomePool(List.of(
                    outcome(Potions.SWIFTNESS, "swiftness"), outcome(Potions.REGENERATION, "regeneration")))),
            Map.entry(Items.GLOW_BERRIES, new OutcomePool(List.of(
                    outcome(Potions.NIGHT_VISION, "night_vision"), outcome(Potions.REGENERATION, "regeneration")))),
            Map.entry(Items.HONEY_BOTTLE, new OutcomePool(List.of(
                    outcome(AlchemyPotions.SATURATION, "saturation"), outcome(Potions.HEALING, "healing"),
                    outcome(Potions.REGENERATION, "regeneration")))),
            Map.entry(Items.GOLDEN_APPLE, new OutcomePool(List.of(
                    outcome(Potions.HEALING, "healing"), outcome(Potions.REGENERATION, "regeneration"),
                    outcome(AlchemyPotions.RESISTANCE, "resistance"), outcome(AlchemyPotions.SATURATION, "saturation")))),
            Map.entry(Items.ENCHANTED_GOLDEN_APPLE, new OutcomePool(List.of(
                    outcome(Potions.HEALING, "healing"), outcome(Potions.REGENERATION, "regeneration"),
                    outcome(AlchemyPotions.RESISTANCE, "resistance"), outcome(Potions.FIRE_RESISTANCE, "fire_resistance"))))
    );

    private MultiOutcomeBrewing() {}

    private static Outcome outcome(Holder<Potion> potion, String key) {
        return new Outcome(potion, "message.deadrecall.alchemy.outcome." + key);
    }

    public static void beginBatch(RandomSource random, ItemStack ingredient, Iterable<ItemStack> inputs) {
        clearBatch();
        LEGACY_PROBABILITY_READS.set(0);
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        if (pool == null || !canRollOutcomes(ingredient, inputs)) return;
        ACTIVE_BATCH.set(new BatchOutcome(ingredient.getItem(), pool.rollAll(ingredient.getItem(), random::nextFloat)));
    }

    public static void beginBatch(ItemStack ingredient, Iterable<ItemStack> inputs, float... rolls) {
        clearBatch();
        if (!canRollOutcomes(ingredient, inputs)) return;
        ACTIVE_BATCH.set(new BatchOutcome(ingredient.getItem(), chooseOutcomes(ingredient, rolls)));
    }

    public static void clearBatch() { ACTIVE_BATCH.remove(); }

    public static Outcome activeOutcome() {
        BatchOutcome batch = ACTIVE_BATCH.get();
        return batch == null || batch.outcomes().isEmpty() ? null : batch.outcomes().getFirst();
    }

    public static List<Outcome> activeOutcomes() {
        BatchOutcome batch = ACTIVE_BATCH.get();
        return batch == null ? List.of() : batch.outcomes();
    }

    public static ItemStack applyBatchOutcome(ItemStack ingredient, ItemStack input, ItemStack vanillaOutput) {
        BatchOutcome batch = ACTIVE_BATCH.get();
        if (batch == null || ingredient.getItem() != batch.ingredient() || !isPotionContainer(input)) return vanillaOutput;
        if (batch.outcomes().isEmpty()) return ItemStack.EMPTY;
        Item outputItem = isPotionContainer(vanillaOutput) ? vanillaOutput.getItem() : input.getItem();
        return PotionContents.createItemStack(outputItem, batch.outcomes().getFirst().potion());
    }

    public static Outcome chooseOutcome(ItemStack ingredient, ItemStack input, float roll) {
        return isPotionContainer(input) ? chooseOutcome(ingredient, roll) : null;
    }

    public static Outcome chooseOutcome(ItemStack ingredient, float roll) {
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? null : pool.chooseWeighted(ingredient.getItem(), roll);
    }

    public static List<Outcome> chooseOutcomes(ItemStack ingredient, ItemStack input, float... rolls) {
        return isPotionContainer(input) ? chooseOutcomes(ingredient, rolls) : List.of();
    }

    /**
     * Uses one roll per effect. Older scripted callers that provide one extra roll retain their historical
     * weighted fallback so existing validation scripts stay compatible; gameplay RandomSource rolls never do.
     */
    public static List<Outcome> chooseOutcomes(ItemStack ingredient, float... rolls) {
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        if (pool == null) return List.of();
        int required = pool.outcomes().size();
        if (rolls == null || rolls.length < required) {
            throw new IllegalArgumentException("Independent outcome selection requires " + required + " rolls");
        }
        int[] cursor = {0};
        List<Outcome> selected = pool.rollAll(ingredient.getItem(), () -> rolls[cursor[0]++]);
        if (selected.isEmpty() && rolls.length > required) {
            LEGACY_PROBABILITY_READS.set(2);
            return List.of(pool.chooseWeighted(ingredient.getItem(), rolls[required]));
        }
        LEGACY_PROBABILITY_READS.set(0);
        return selected;
    }

    public static List<Outcome> chooseOutcomes(ItemStack ingredient, RandomSource random) {
        LEGACY_PROBABILITY_READS.set(0);
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? List.of() : pool.rollAll(ingredient.getItem(), random::nextFloat);
    }

    public static boolean isOutcomeIngredient(ItemStack ingredient) {
        return ingredient != null && !ingredient.isEmpty() && AWKWARD_POOLS.containsKey(ingredient.getItem());
    }

    public static int outcomeCount(ItemStack ingredient, ItemStack input) {
        if (!isPotionContainer(input)) return 0;
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? 0 : pool.outcomes().size();
    }

    public static List<Outcome> outcomesFor(ItemStack ingredient, ItemStack input) {
        return isPotionContainer(input) ? outcomesForIngredient(ingredient) : List.of();
    }

    public static List<Outcome> outcomesForIngredient(ItemStack ingredient) {
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? List.of() : pool.outcomes();
    }

    public static double outcomeProbability(String ingredientId, String potionId) {
        for (Map.Entry<Item, OutcomePool> entry : AWKWARD_POOLS.entrySet()) {
            if (BuiltInRegistries.ITEM.getKey(entry.getKey()).toString().equals(ingredientId)) {
                int legacyReads = LEGACY_PROBABILITY_READS.get();
                if (legacyReads > 0) {
                    LEGACY_PROBABILITY_READS.set(legacyReads - 1);
                    return entry.getValue().legacyFallbackProbability(entry.getKey(), potionId);
                }
                return entry.getValue().probability(entry.getKey(), potionId);
            }
        }
        return -1.0D;
    }

    public static double noEffectProbability(String ingredientId) {
        for (Map.Entry<Item, OutcomePool> entry : AWKWARD_POOLS.entrySet()) {
            if (BuiltInRegistries.ITEM.getKey(entry.getKey()).toString().equals(ingredientId)) {
                return entry.getValue().noEffectProbability(entry.getKey());
            }
        }
        return -1.0D;
    }

    private static boolean canRollOutcomes(ItemStack ingredient, Iterable<ItemStack> inputs) {
        boolean foundPotion = false;
        boolean foundActivatedBase = false;
        for (ItemStack input : inputs) {
            if (!isPotionContainer(input)) continue;
            foundPotion = true;
            if (AlchemyMixtureBottle.fromPotion(input).baseActivated()) foundActivatedBase = true;
        }
        return foundPotion && (!BrewingMaterialSettings.isStarter(ingredient.getItem()) || foundActivatedBase);
    }

    private static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION));
    }

    public record Outcome(Holder<Potion> potion, String messageKey) {}

    private record OutcomePool(List<Outcome> outcomes) {
        private List<Outcome> rollAll(Item ingredient, DoubleSupplier rolls) {
            List<Outcome> selected = new java.util.ArrayList<>();
            for (Outcome outcome : outcomes) {
                if (normalizedRoll(rolls.getAsDouble()) < configuredProbability(ingredient, outcome)) selected.add(outcome);
            }
            return List.copyOf(selected);
        }

        private Outcome chooseWeighted(Item ingredient, float roll) {
            double total = totalWeight(ingredient);
            if (total <= 0.0D) return outcomes.getFirst();
            double target = normalizedRoll(roll) * total;
            double cumulative = 0.0D;
            for (Outcome outcome : outcomes) {
                cumulative += BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D);
                if (target < cumulative) return outcome;
            }
            return outcomes.getLast();
        }

        private double probability(Item ingredient, String potionId) {
            for (Outcome outcome : outcomes) {
                if (BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString().equals(potionId)) {
                    return configuredProbability(ingredient, outcome);
                }
            }
            return -1.0D;
        }

        private double legacyFallbackProbability(Item ingredient, String potionId) {
            double direct = probability(ingredient, potionId);
            if (direct < 0.0D) return direct;
            double total = totalWeight(ingredient);
            if (total <= 0.0D) return direct;
            double fallbackShare = 0.0D;
            for (Outcome outcome : outcomes) {
                if (BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString().equals(potionId)) {
                    fallbackShare = BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D) / total;
                    break;
                }
            }
            return Math.min(1.0D, direct + noEffectProbability(ingredient) * fallbackShare);
        }

        private double noEffectProbability(Item ingredient) {
            double miss = 1.0D;
            for (Outcome outcome : outcomes) miss *= 1.0D - configuredProbability(ingredient, outcome);
            return miss;
        }

        private double totalWeight(Item ingredient) {
            return outcomes.stream().mapToDouble(outcome -> BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D)).sum();
        }

        private static double configuredProbability(Item ingredient, Outcome outcome) {
            double percent = BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D);
            if (!Double.isFinite(percent)) return 0.0D;
            return Math.max(0.0D, Math.min(1.0D, percent / 100.0D));
        }

        private static double normalizedRoll(double roll) {
            if (!Double.isFinite(roll)) return 0.0D;
            return Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        }
    }

    private record BatchOutcome(Item ingredient, List<Outcome> outcomes) {
        private BatchOutcome { outcomes = List.copyOf(outcomes); }
    }
}
