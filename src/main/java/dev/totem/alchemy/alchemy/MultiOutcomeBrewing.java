package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
    private static final Map<Item, OutcomePool> AWKWARD_POOLS = Map.ofEntries(
            Map.entry(Items.SPIDER_EYE, new OutcomePool(List.of(
                    new Outcome(Potions.POISON, "message.deadrecall.alchemy.outcome.poison"),
                    new Outcome(Potions.WEAKNESS, "message.deadrecall.alchemy.outcome.weakness")
            ))),
            Map.entry(Items.RED_MUSHROOM, new OutcomePool(List.of(
                    new Outcome(Potions.POISON, "message.deadrecall.alchemy.outcome.poison"),
                    new Outcome(AlchemyPotions.SATURATION, "message.deadrecall.alchemy.outcome.saturation")
            ))),
            Map.entry(Items.GLISTERING_MELON_SLICE, new OutcomePool(List.of(
                    new Outcome(Potions.HEALING, "message.deadrecall.alchemy.outcome.healing"),
                    new Outcome(AlchemyPotions.RESISTANCE, "message.deadrecall.alchemy.outcome.resistance")
            ))),
            Map.entry(Items.SUGAR, new OutcomePool(List.of(
                    new Outcome(Potions.SWIFTNESS, "message.deadrecall.alchemy.outcome.swiftness"),
                    new Outcome(Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness"),
                    new Outcome(AlchemyPotions.SATURATION, "message.deadrecall.alchemy.outcome.saturation")
            ))),
            Map.entry(Items.RABBIT_FOOT, new OutcomePool(List.of(
                    new Outcome(Potions.LEAPING, "message.deadrecall.alchemy.outcome.leaping"),
                    new Outcome(Potions.SLOW_FALLING, "message.deadrecall.alchemy.outcome.slow_falling"),
                    new Outcome(Potions.SWIFTNESS, "message.deadrecall.alchemy.outcome.swiftness")
            ))),
            Map.entry(Items.MAGMA_CREAM, new OutcomePool(List.of(
                    new Outcome(Potions.FIRE_RESISTANCE, "message.deadrecall.alchemy.outcome.fire_resistance"),
                    new Outcome(AlchemyPotions.RESISTANCE, "message.deadrecall.alchemy.outcome.resistance"),
                    new Outcome(Potions.STRENGTH, "message.deadrecall.alchemy.outcome.strength")
            ))),
            Map.entry(Items.GOLDEN_CARROT, new OutcomePool(List.of(
                    new Outcome(Potions.NIGHT_VISION, "message.deadrecall.alchemy.outcome.night_vision"),
                    new Outcome(Potions.INVISIBILITY, "message.deadrecall.alchemy.outcome.invisibility"),
                    new Outcome(Potions.HEALING, "message.deadrecall.alchemy.outcome.healing")
            ))),
            Map.entry(Items.BLAZE_POWDER, new OutcomePool(List.of(
                    new Outcome(Potions.STRENGTH, "message.deadrecall.alchemy.outcome.strength"),
                    new Outcome(Potions.FIRE_RESISTANCE, "message.deadrecall.alchemy.outcome.fire_resistance"),
                    new Outcome(Potions.HARMING, "message.deadrecall.alchemy.outcome.harming")
            ))),
            Map.entry(Items.GHAST_TEAR, new OutcomePool(List.of(
                    new Outcome(Potions.REGENERATION, "message.deadrecall.alchemy.outcome.regeneration"),
                    new Outcome(Potions.HEALING, "message.deadrecall.alchemy.outcome.healing"),
                    new Outcome(Potions.WEAKNESS, "message.deadrecall.alchemy.outcome.weakness")
            ))),
            Map.entry(Items.PUFFERFISH, new OutcomePool(List.of(
                    new Outcome(Potions.WATER_BREATHING, "message.deadrecall.alchemy.outcome.water_breathing"),
                    new Outcome(Potions.POISON, "message.deadrecall.alchemy.outcome.poison"),
                    new Outcome(Potions.WEAKNESS, "message.deadrecall.alchemy.outcome.weakness")
            ))),
            Map.entry(Items.TURTLE_HELMET, new OutcomePool(List.of(
                    new Outcome(Potions.TURTLE_MASTER, "message.deadrecall.alchemy.outcome.turtle_master"),
                    new Outcome(Potions.WATER_BREATHING, "message.deadrecall.alchemy.outcome.water_breathing"),
                    new Outcome(AlchemyPotions.RESISTANCE, "message.deadrecall.alchemy.outcome.resistance")
            ))),
            Map.entry(Items.PHANTOM_MEMBRANE, new OutcomePool(List.of(
                    new Outcome(Potions.SLOW_FALLING, "message.deadrecall.alchemy.outcome.slow_falling"),
                    new Outcome(Potions.INVISIBILITY, "message.deadrecall.alchemy.outcome.invisibility"),
                    new Outcome(Potions.NIGHT_VISION, "message.deadrecall.alchemy.outcome.night_vision")
            ))),
            Map.entry(Items.BREEZE_ROD, new OutcomePool(List.of(
                    new Outcome(Potions.WIND_CHARGED, "message.deadrecall.alchemy.outcome.wind_charged"),
                    new Outcome(Potions.SLOW_FALLING, "message.deadrecall.alchemy.outcome.slow_falling"),
                    new Outcome(Potions.LEAPING, "message.deadrecall.alchemy.outcome.leaping")
            ))),
            Map.entry(Items.SLIME_BLOCK, new OutcomePool(List.of(
                    new Outcome(Potions.OOZING, "message.deadrecall.alchemy.outcome.oozing"),
                    new Outcome(Potions.LEAPING, "message.deadrecall.alchemy.outcome.leaping"),
                    new Outcome(Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness")
            ))),
            Map.entry(Items.STONE, new OutcomePool(List.of(
                    new Outcome(Potions.INFESTED, "message.deadrecall.alchemy.outcome.infested"),
                    new Outcome(AlchemyPotions.RESISTANCE, "message.deadrecall.alchemy.outcome.resistance"),
                    new Outcome(Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness")
            ))),
            Map.entry(Items.COBWEB, new OutcomePool(List.of(
                    new Outcome(Potions.WEAVING, "message.deadrecall.alchemy.outcome.weaving"),
                    new Outcome(Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness"),
                    new Outcome(Potions.WEAKNESS, "message.deadrecall.alchemy.outcome.weakness")
            ))),
            Map.entry(Items.FERMENTED_SPIDER_EYE, new OutcomePool(List.of(
                    new Outcome(Potions.WEAKNESS, "message.deadrecall.alchemy.outcome.weakness"),
                    new Outcome(Potions.HARMING, "message.deadrecall.alchemy.outcome.harming"),
                    new Outcome(Potions.INVISIBILITY, "message.deadrecall.alchemy.outcome.invisibility")
            )))
    );

    private MultiOutcomeBrewing() {
    }

    /**
     * A configured outcome material may be layered onto any potion-bearing batch. Base validity is handled
     * by the mixture stability system rather than by refusing the recipe at this stage.
     */
    public static void beginBatch(RandomSource random, ItemStack ingredient, Iterable<ItemStack> inputs) {
        clearBatch();
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        if (pool == null || !canRollOutcomes(ingredient, inputs)) {
            return;
        }
        ACTIVE_BATCH.set(new BatchOutcome(ingredient.getItem(), pool.rollAll(ingredient.getItem(), random::nextFloat)));
    }

    /** Deterministic batch setup used by validation and scripted chemistry. */
    public static void beginBatch(ItemStack ingredient, Iterable<ItemStack> inputs, float... rolls) {
        clearBatch();
        if (!canRollOutcomes(ingredient, inputs)) {
            return;
        }
        List<Outcome> outcomes = chooseOutcomes(ingredient, rolls);
        if (!outcomes.isEmpty()) {
            ACTIVE_BATCH.set(new BatchOutcome(ingredient.getItem(), outcomes));
        }
    }

    public static void clearBatch() {
        ACTIVE_BATCH.remove();
    }

    public static Outcome activeOutcome() {
        BatchOutcome batch = ACTIVE_BATCH.get();
        return batch == null || batch.outcomes().isEmpty() ? null : batch.outcomes().getFirst();
    }

    /** The immutable effect set selected once for the active Brewing Stand batch. */
    public static List<Outcome> activeOutcomes() {
        BatchOutcome batch = ACTIVE_BATCH.get();
        return batch == null ? List.of() : batch.outcomes();
    }

    public static ItemStack applyBatchOutcome(ItemStack ingredient, ItemStack input, ItemStack vanillaOutput) {
        BatchOutcome batch = ACTIVE_BATCH.get();
        if (batch == null || ingredient.getItem() != batch.ingredient() || !isPotionContainer(input)) {
            return vanillaOutput;
        }
        Item outputItem = isPotionContainer(vanillaOutput) ? vanillaOutput.getItem() : input.getItem();
        return PotionContents.createItemStack(outputItem, batch.outcomes().getFirst().potion());
    }

    public static Outcome chooseOutcome(ItemStack ingredient, ItemStack input, float roll) {
        if (!isPotionContainer(input)) {
            return null;
        }
        return chooseOutcome(ingredient, roll);
    }

    public static Outcome chooseOutcome(ItemStack ingredient, float roll) {
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? null : pool.choose(ingredient.getItem(), roll);
    }

    /**
     * Independently rolls every configured effect. The first {@code outcomeCount} values are the Bernoulli
     * rolls in configured order; when all miss, the following value selects the guaranteed weighted fallback.
     */
    public static List<Outcome> chooseOutcomes(ItemStack ingredient, ItemStack input, float... rolls) {
        if (!isPotionContainer(input)) {
            return List.of();
        }
        return chooseOutcomes(ingredient, rolls);
    }

    /** Deterministic overload used by validation and other non-Brewing-Stand chemistry paths. */
    public static List<Outcome> chooseOutcomes(ItemStack ingredient, float... rolls) {
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        if (pool == null) {
            return List.of();
        }
        int required = pool.outcomes().size() + 1;
        if (rolls == null || rolls.length < required) {
            throw new IllegalArgumentException("Independent outcome selection requires " + required + " rolls");
        }
        int[] cursor = {0};
        return pool.rollAll(ingredient.getItem(), () -> rolls[cursor[0]++]);
    }

    /** Independently rolls every configured outcome using the supplied random source. */
    public static List<Outcome> chooseOutcomes(ItemStack ingredient, RandomSource random) {
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? List.of() : pool.rollAll(ingredient.getItem(), random::nextFloat);
    }

    public static boolean isOutcomeIngredient(ItemStack ingredient) {
        return ingredient != null && !ingredient.isEmpty() && AWKWARD_POOLS.containsKey(ingredient.getItem());
    }

    public static int outcomeCount(ItemStack ingredient, ItemStack input) {
        if (!isPotionContainer(input)) {
            return 0;
        }
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? 0 : pool.outcomes().size();
    }

    public static List<Outcome> outcomesFor(ItemStack ingredient, ItemStack input) {
        if (!isPotionContainer(input)) {
            return List.of();
        }
        return outcomesForIngredient(ingredient);
    }

    public static List<Outcome> outcomesForIngredient(ItemStack ingredient) {
        OutcomePool pool = ingredient == null || ingredient.isEmpty() ? null : AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? List.of() : pool.outcomes();
    }

    /** Server-side only research comparator. The manual receives only the resulting tier, never this probability. */
    public static double outcomeProbability(String ingredientId, String potionId) {
        for (Map.Entry<Item, OutcomePool> entry : AWKWARD_POOLS.entrySet()) {
            if (BuiltInRegistries.ITEM.getKey(entry.getKey()).toString().equals(ingredientId)) {
                return entry.getValue().probability(entry.getKey(), potionId);
            }
        }
        return -1.0D;
    }

    private static boolean canRollOutcomes(ItemStack ingredient, Iterable<ItemStack> inputs) {
        boolean foundPotion = false;
        boolean foundActivatedBase = false;
        for (ItemStack input : inputs) {
            if (!isPotionContainer(input)) {
                continue;
            }
            foundPotion = true;
            if (AlchemyMixtureBottle.fromPotion(input).baseActivated()) {
                foundActivatedBase = true;
            }
        }
        return foundPotion && (!BrewingMaterialSettings.isStarter(ingredient.getItem()) || foundActivatedBase);
    }

    private static boolean isPotionContainer(ItemStack stack) {
        return stack != null && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION));
    }

    public record Outcome(Holder<Potion> potion, String messageKey) {
    }

    private record OutcomePool(List<Outcome> outcomes) {
        private List<Outcome> rollAll(Item ingredient, DoubleSupplier rolls) {
            double total = totalWeight(ingredient);
            if (total <= 0.0D) {
                return List.of(outcomes.getFirst());
            }
            List<Outcome> selected = new java.util.ArrayList<>();
            for (Outcome outcome : outcomes) {
                double probability = BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D) / total;
                if (normalizedRoll(rolls.getAsDouble()) < probability) {
                    selected.add(outcome);
                }
            }
            if (selected.isEmpty()) {
                selected.add(choose(ingredient, (float) normalizedRoll(rolls.getAsDouble())));
            }
            return List.copyOf(selected);
        }

        private Outcome choose(Item ingredient, float roll) {
            double total = totalWeight(ingredient);
            if (total <= 0.0D) {
                return outcomes.getFirst();
            }
            double target = normalizedRoll(roll) * total;
            double cumulative = 0.0D;
            for (Outcome outcome : outcomes) {
                cumulative += BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D);
                if (target < cumulative) {
                    return outcome;
                }
            }
            return outcomes.getLast();
        }

        private double probability(Item ingredient, String potionId) {
            double total = totalWeight(ingredient);
            if (total <= 0.0D) {
                return -1.0D;
            }
            double allMissProbability = 1.0D;
            for (Outcome outcome : outcomes) {
                double share = BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D) / total;
                allMissProbability *= 1.0D - share;
            }
            for (Outcome outcome : outcomes) {
                if (BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString().equals(potionId)) {
                    double share = BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D) / total;
                    return Math.min(1.0D, share + allMissProbability * share);
                }
            }
            return -1.0D;
        }

        private double totalWeight(Item ingredient) {
            return outcomes.stream()
                    .mapToDouble(outcome -> BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D))
                    .sum();
        }

        private static double normalizedRoll(double roll) {
            if (!Double.isFinite(roll)) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        }
    }

    private record BatchOutcome(Item ingredient, List<Outcome> outcomes) {
        private BatchOutcome {
            outcomes = List.copyOf(outcomes);
        }
    }
}
