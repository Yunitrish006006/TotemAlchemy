package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.IngredientReaction;
import dev.totem.alchemy.reaction.ReactionOutcome;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/** Selects one shared independently rolled result set for every compatible bottle in a brewing-stand batch. */
public final class MultiOutcomeBrewing {
    private static final ThreadLocal<BatchOutcome> ACTIVE_BATCH = new ThreadLocal<>();
    private static final ThreadLocal<Integer> LEGACY_PROBABILITY_READS = ThreadLocal.withInitial(() -> 0);

    private MultiOutcomeBrewing() {}

    private static OutcomePool poolFor(ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        return BrewingReactionContext.resolveLegacyActivated(ingredient)
                .map(MultiOutcomeBrewing::registryPool)
                .orElse(null);
    }

    private static OutcomePool poolFor(ItemStack ingredient, Iterable<ItemStack> inputs) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        return BrewingReactionContext.resolveFirst(inputs, ingredient)
                .map(BrewingReactionContext::reaction)
                .map(MultiOutcomeBrewing::registryPool)
                .orElseGet(() -> poolFor(ingredient));
    }

    private static OutcomePool registryPool(IngredientReaction reaction) {
        List<Outcome> outcomes = new java.util.ArrayList<>();
        Map<String, Double> probabilities = new java.util.LinkedHashMap<>();
        for (ReactionOutcome configured : reaction.outcomes()) {
            Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(configured.resultPotionId().toString());
            if (potion == null) {
                throw new IllegalStateException(
                        "Unknown potion outcome " + configured.resultPotionId()
                                + " in reaction " + reaction.id()
                );
            }
            outcomes.add(outcome(potion, outcomeMessageKey(configured.resultPotionId())));
            probabilities.put(configured.resultPotionId().toString(), configured.chance());
        }
        return new OutcomePool(List.copyOf(outcomes), Map.copyOf(probabilities));
    }

    static List<Outcome> chooseRegistryOutcomes(IngredientReaction reaction, float... rolls) {
        OutcomePool pool = registryPool(reaction);
        if (rolls == null || rolls.length < pool.outcomes().size()) {
            throw new IllegalArgumentException(
                    "Independent outcome selection requires " + pool.outcomes().size() + " rolls"
            );
        }
        int[] cursor = {0};
        return pool.rollAll(Items.AIR, () -> rolls[cursor[0]++]);
    }

    static double registryOutcomeProbability(IngredientReaction reaction, String potionId) {
        return registryPool(reaction).probability(Items.AIR, potionId);
    }

    private static String outcomeMessageKey(Identifier potionId) {
        String path = potionId.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static Outcome outcome(Holder<Potion> potion, String key) {
        return new Outcome(potion, "message.totem.alchemy.outcome." + key);
    }

    public static void beginBatch(RandomSource random, ItemStack ingredient, Iterable<ItemStack> inputs) {
        clearBatch();
        LEGACY_PROBABILITY_READS.set(0);
        OutcomePool pool = poolFor(ingredient, inputs);
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
        OutcomePool pool = poolFor(ingredient);
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
        OutcomePool pool = poolFor(ingredient);
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
        OutcomePool pool = poolFor(ingredient);
        return pool == null ? List.of() : pool.rollAll(ingredient.getItem(), random::nextFloat);
    }

    public static boolean isOutcomeIngredient(ItemStack ingredient) {
        return poolFor(ingredient) != null;
    }

    public static int outcomeCount(ItemStack ingredient, ItemStack input) {
        if (!isPotionContainer(input)) return 0;
        OutcomePool pool = poolFor(ingredient);
        return pool == null ? 0 : pool.outcomes().size();
    }

    public static List<Outcome> outcomesFor(ItemStack ingredient, ItemStack input) {
        return isPotionContainer(input) ? outcomesForIngredient(ingredient) : List.of();
    }

    public static List<Outcome> outcomesForIngredient(ItemStack ingredient) {
        OutcomePool pool = poolFor(ingredient);
        return pool == null ? List.of() : pool.outcomes();
    }

    public static double outcomeProbability(String ingredientId, String potionId) {
        Item ingredient = itemById(ingredientId);
        if (ingredient == null) return -1.0D;
        OutcomePool pool = poolFor(new ItemStack(ingredient));
        if (pool == null) return -1.0D;

        int legacyReads = LEGACY_PROBABILITY_READS.get();
        if (legacyReads > 0) {
            LEGACY_PROBABILITY_READS.set(legacyReads - 1);
            return pool.legacyFallbackProbability(ingredient, potionId);
        }
        return pool.probability(ingredient, potionId);
    }

    public static double noEffectProbability(String ingredientId) {
        Item ingredient = itemById(ingredientId);
        if (ingredient == null) return -1.0D;
        OutcomePool pool = poolFor(new ItemStack(ingredient));
        return pool == null ? -1.0D : pool.noEffectProbability(ingredient);
    }

    private static Item itemById(String ingredientId) {
        Identifier id = Identifier.tryParse(ingredientId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || !BuiltInRegistries.ITEM.getKey(item).equals(id)) return null;
        return item;
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

    private record OutcomePool(List<Outcome> outcomes, Map<String, Double> probabilities) {
        private OutcomePool {
            outcomes = List.copyOf(outcomes);
            probabilities = Map.copyOf(probabilities);
        }

        private List<Outcome> rollAll(Item ingredient, DoubleSupplier rolls) {
            List<Outcome> selected = new java.util.ArrayList<>();
            for (Outcome outcome : outcomes) {
                if (normalizedRoll(rolls.getAsDouble()) < configuredProbability(ingredient, outcome)) selected.add(outcome);
            }
            return List.copyOf(selected);
        }

        private Outcome chooseWeighted(Item ingredient, float roll) {
            if (outcomes.isEmpty()) return null;
            double total = totalWeight(ingredient);
            if (total <= 0.0D) return outcomes.getFirst();
            double target = normalizedRoll(roll) * total;
            double cumulative = 0.0D;
            for (Outcome outcome : outcomes) {
                cumulative += outcomeWeight(ingredient, outcome);
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
                    fallbackShare = outcomeWeight(ingredient, outcome) / total;
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
            return outcomes.stream().mapToDouble(outcome -> outcomeWeight(ingredient, outcome)).sum();
        }

        private double outcomeWeight(Item ingredient, Outcome outcome) {
            String potionId = BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString();
            return probabilities.getOrDefault(potionId, 0.0D) * 100.0D;
        }

        private double configuredProbability(Item ingredient, Outcome outcome) {
            String potionId = BuiltInRegistries.POTION.getKey(outcome.potion().value()).toString();
            return clampProbability(probabilities.getOrDefault(potionId, 0.0D));
        }

        private static double clampProbability(double probability) {
            if (!Double.isFinite(probability)) return 0.0D;
            return Math.max(0.0D, Math.min(1.0D, probability));
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
