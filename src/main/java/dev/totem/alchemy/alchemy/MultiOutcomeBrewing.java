package dev.totem.alchemy.alchemy;

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

/** Selects one shared weighted result for every compatible bottle in a brewing-stand batch. */
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

    public static void beginBatch(RandomSource random, ItemStack ingredient, Iterable<ItemStack> inputs) {
        clearBatch();
        if (!containsPotion(inputs, Potions.AWKWARD)) {
            return;
        }
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        if (pool == null) {
            return;
        }
        ACTIVE_BATCH.set(new BatchOutcome(ingredient.getItem(), pool.choose(ingredient.getItem(), random.nextFloat())));
    }

    public static void clearBatch() {
        ACTIVE_BATCH.remove();
    }

    public static Outcome activeOutcome() {
        BatchOutcome batch = ACTIVE_BATCH.get();
        return batch == null ? null : batch.outcome();
    }

    public static ItemStack applyBatchOutcome(ItemStack ingredient, ItemStack input, ItemStack vanillaOutput) {
        BatchOutcome batch = ACTIVE_BATCH.get();
        if (batch == null || ingredient.getItem() != batch.ingredient() || !hasPotion(input, Potions.AWKWARD)) {
            return vanillaOutput;
        }
        return PotionContents.createItemStack(vanillaOutput.getItem(), batch.outcome().potion());
    }

    public static Outcome chooseOutcome(ItemStack ingredient, ItemStack input, float roll) {
        if (!hasPotion(input, Potions.AWKWARD)) {
            return null;
        }
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? null : pool.choose(ingredient.getItem(), roll);
    }

    public static int outcomeCount(ItemStack ingredient, ItemStack input) {
        if (!hasPotion(input, Potions.AWKWARD)) {
            return 0;
        }
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
        return pool == null ? 0 : pool.outcomes().size();
    }

    public static List<Outcome> outcomesFor(ItemStack ingredient, ItemStack input) {
        if (!hasPotion(input, Potions.AWKWARD)) {
            return List.of();
        }
        OutcomePool pool = AWKWARD_POOLS.get(ingredient.getItem());
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

    private static boolean containsPotion(Iterable<ItemStack> inputs, Holder<Potion> potion) {
        for (ItemStack input : inputs) {
            if (hasPotion(input, potion)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPotion(ItemStack stack, Holder<Potion> potion) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion);
    }

    public record Outcome(Holder<Potion> potion, String messageKey) {
    }

    private record OutcomePool(List<Outcome> outcomes) {
        private Outcome choose(Item ingredient, float roll) {
            double total = outcomes.stream()
                    .mapToDouble(outcome -> BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D))
                    .sum();
            double target = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll)) * total;
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
            double total = outcomes.stream()
                    .mapToDouble(outcome -> BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D))
                    .sum();
            if (total <= 0.0D) {
                return -1.0D;
            }
            for (Outcome outcome : outcomes) {
                if (BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString().equals(potionId)) {
                    return BrewingOutcomeWeights.weight(ingredient, outcome.potion(), 1.0D) / total;
                }
            }
            return -1.0D;
        }
    }

    private record BatchOutcome(Item ingredient, Outcome outcome) {
    }
}
