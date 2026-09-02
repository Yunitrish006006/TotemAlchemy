package dev.totem.alchemy.mixture;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.alchemy.AlchemyHandler;
import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyCauldronBlockEntity;
import dev.totem.core.api.v1.migration.LegacyItemMigrationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adapts the module's named cauldron recipes to the same persistent reaction model as mixed potions.
 *
 * <p>The recipe identity and accepted ingredient steps live in mixture provenance. Actual processing is carried
 * by {@link AlchemyMixtureState.Reaction}, so bottling an unfinished drink preserves every timer and pouring it
 * back resumes from that exact point. Solid-result recipes use the same model but cannot be bottled.</p>
 */
public final class AlchemyCompoundBrewing {
    private static final String RECIPE_MARKER = "compound:recipe:";
    private static final String INPUT_MARKER = "compound:input:";
    private static final String READY_MARKER = "compound:ready:";
    private static final String RESULT_MARKER = "compound:result:";
    private static final String REACTION_PREFIX = "compound:";

    private AlchemyCompoundBrewing() {
    }

    public static Match findMatch(Level level, BlockPos pos, ItemStack stack, boolean dropped) {
        if (level == null || stack == null || stack.isEmpty()) {
            return null;
        }
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(AlchemyBlocks.ALCHEMY_CAULDRON)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AlchemyCauldronBlockEntity cauldron)
                    || !cauldron.hasMixture()) {
                return null;
            }
            AlchemyMixtureState mixture = cauldron.mixtureSnapshot();
            AlchemyCauldronRecipe recipe = activeRecipe(mixture);
            if (recipe == null || isReady(mixture)) {
                return null;
            }
            AlchemyCauldronRecipe.IngredientStep ingredient = recipe.findIngredient(stack, dropped);
            return ingredient != null && !hasInput(mixture, recipe, ingredient)
                    ? new Match(recipe, ingredient)
                    : null;
        }

        for (AlchemyCauldronRecipe recipe : AlchemyCauldronRecipes.all()) {
            if (!recipe.usesMixtureSystem()
                    || recipe.requiresLitCampfire() && !AlchemyHandler.hasLitCampfireBelow(level, pos)
                    || !recipe.canStartFrom(blockState)) {
                continue;
            }
            AlchemyCauldronRecipe.IngredientStep ingredient = recipe.findIngredient(stack, dropped);
            if (ingredient != null && ingredient.canStartRecipe()) {
                return new Match(recipe, ingredient);
            }
        }
        return null;
    }

    public static AlchemyMixtureState initialState(AlchemyCauldronRecipe recipe) {
        if (recipe == null) {
            return AlchemyMixtureState.empty();
        }
        AlchemyMixtureState state = switch (recipe.startState()) {
            case FULL_WATER_CAULDRON -> AlchemyMixtureBrewing.waterState(recipe.initialLevel());
            case EMPTY_CAULDRON -> new AlchemyMixtureState(recipe.initialLevel());
        };
        state.addProvenance(recipeMarker(recipe));
        if (recipe.startState() == AlchemyCauldronRecipe.StartState.EMPTY_CAULDRON) {
            state.addProvenance("compound:base:milk");
        }
        return state;
    }

    public static boolean schedule(
            AlchemyMixtureState state,
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient,
            ItemStack actualIngredient
    ) {
        return schedule(state, recipe, ingredient, actualIngredient, 0);
    }

    public static boolean schedule(
            AlchemyMixtureState state,
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient,
            ItemStack actualIngredient,
            int elapsedTicks
    ) {
        if (state == null || state.isEmpty() || recipe == null || ingredient == null
                || actualIngredient == null || actualIngredient.isEmpty()
                || activeRecipeId(state) == null || !recipe.id().equals(activeRecipeId(state))
                || hasInput(state, recipe, ingredient)) {
            return false;
        }
        String actualIngredientId = BuiltInRegistries.ITEM.getKey(actualIngredient.getItem()).toString();
        int processingTicks = BrewingMaterialSettings.processingTicks(actualIngredient.getItem());
        String reactionId = reactionId(recipe, ingredient);
        state.addProvenance(inputMarker(recipe, ingredient));
        state.setCanonicalPotionId(null);
        state.addReaction(new AlchemyMixtureState.Reaction(
                reactionId,
                actualIngredientId,
                Math.max(0, Math.min(processingTicks, elapsedTicks)),
                processingTicks,
                state.volumeUnits(),
                null,
                null,
                Map.of(),
                Map.of()
        ));
        return true;
    }

    /** Marks an ingredient already completed by a pre-unification saved cauldron. */
    public static void restoreCompletedInput(
            AlchemyMixtureState state,
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
        if (state != null && recipe != null && ingredient != null) {
            state.addProvenance(inputMarker(recipe, ingredient));
        }
    }

    /** Applies a named bottled result once every captured reaction has completed. */
    public static AlchemyCauldronRecipe completeIfReady(AlchemyMixtureState state) {
        AlchemyCauldronRecipe recipe = activeRecipe(state);
        if (recipe == null || isReady(state) || !hasAllInputs(state, recipe) || hasRecipeReaction(state, recipe)) {
            return null;
        }

        if (recipe.result().potionId() != null) {
            Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(recipe.result().potionId().toString());
            if (potion != null) {
                AlchemyMixtureState oneDose = AlchemyMixtureBottle.fromPotion(
                        PotionContents.createItemStack(Items.POTION, potion));
                Map<String, AlchemyMixtureState.EffectDose> scaled = new LinkedHashMap<>();
                oneDose.effects().forEach((id, dose) ->
                        scaled.put(id, dose.scale(Math.max(1, state.volumeUnits()))));
                state.replaceEffects(scaled);
                state.setCanonicalPotionId(recipe.result().potionId().toString());
                state.setBaseActivated(true);
            }
        }

        state.addProvenance(readyMarker(recipe));
        Identifier resultId = BuiltInRegistries.ITEM.getKey(recipe.result().item());
        if (resultId != null) {
            state.addProvenance(RESULT_MARKER + resultId);
        }
        return recipe;
    }

    public static AlchemyCauldronRecipe activeRecipe(AlchemyMixtureState state) {
        Identifier id = activeRecipeId(state);
        return id == null ? null : AlchemyCauldronRecipes.get(id);
    }

    public static Identifier activeRecipeId(AlchemyMixtureState state) {
        if (state == null) {
            return null;
        }
        for (String marker : state.provenance()) {
            if (marker.startsWith(RECIPE_MARKER)) {
                return Identifier.tryParse(marker.substring(RECIPE_MARKER.length()));
            }
        }
        return null;
    }

    public static boolean hasActiveRecipe(AlchemyMixtureState state) {
        return activeRecipeId(state) != null;
    }

    public static boolean isReady(AlchemyMixtureState state) {
        Identifier id = activeRecipeId(state);
        return id != null && state.hasProvenance(READY_MARKER + id);
    }

    public static boolean isSolidProcess(AlchemyMixtureState state) {
        AlchemyCauldronRecipe recipe = activeRecipe(state);
        return recipe != null && recipe.result().type() == AlchemyCauldronRecipe.ResultType.DROP_ITEM;
    }

    /** Compound portions may only be recombined when they came from equivalent recipe stages. */
    public static boolean canMerge(AlchemyMixtureState left, AlchemyMixtureState right) {
        Identifier leftId = activeRecipeId(left);
        Identifier rightId = activeRecipeId(right);
        if (leftId == null && rightId == null) {
            return true;
        }
        boolean bothReady = isReady(left) && isReady(right);
        return Objects.equals(leftId, rightId)
                && leftId != null
                && isReady(left) == isReady(right)
                && (bothReady || inputMarkers(left, leftId).equals(inputMarkers(right, rightId)));
    }

    /** Returns the old custom drink item for a completed named recipe; unfinished states stay vanilla bottles. */
    public static ItemStack bottledResult(AlchemyMixtureState state) {
        AlchemyCauldronRecipe recipe = activeRecipe(state);
        if (!isReady(state) || recipe == null
                || recipe.result().type() != AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM) {
            return ItemStack.EMPTY;
        }
        Item item = resultItem(state);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack output = new ItemStack(item);
        AlchemyMixtureBottle.writeState(output, state);
        return output;
    }

    public static Item resultItem(AlchemyMixtureState state) {
        if (state == null) {
            return null;
        }
        for (String marker : state.provenance()) {
            if (!marker.startsWith(RESULT_MARKER)) {
                continue;
            }
            Identifier id = Identifier.tryParse(marker.substring(RESULT_MARKER.length()));
            return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        }
        return null;
    }

    /** Gives legacy hot-cocoa/cherry-brew stacks a complete modern mixture when first poured. */
    public static AlchemyMixtureState legacyDrinkState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return AlchemyMixtureState.empty();
        }
        for (AlchemyCauldronRecipe recipe : AlchemyCauldronRecipes.all()) {
            if (!recipe.usesMixtureSystem()
                    || recipe.result().type() != AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM
                    || !LegacyItemMigrationRegistry.matches(stack, recipe.result().item())) {
                continue;
            }
            AlchemyMixtureState state = initialState(recipe);
            // A bottled legacy result is one dose even though its original cauldron began with three.
            state = state.extractUnits(1);
            for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
                restoreCompletedInput(state, recipe, ingredient);
            }
            completeIfReady(state);
            state.lockHeatIfFinished();
            return state;
        }
        return AlchemyMixtureState.empty();
    }

    private static boolean hasInput(
            AlchemyMixtureState state,
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
        return state.hasProvenance(inputMarker(recipe, ingredient));
    }

    private static boolean hasAllInputs(AlchemyMixtureState state, AlchemyCauldronRecipe recipe) {
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            if (!hasInput(state, recipe, ingredient)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasRecipeReaction(AlchemyMixtureState state, AlchemyCauldronRecipe recipe) {
        String prefix = REACTION_PREFIX + recipe.id() + ":";
        return state.reactions().stream().anyMatch(reaction -> reaction.id().startsWith(prefix));
    }

    private static Set<String> inputMarkers(AlchemyMixtureState state, Identifier recipeId) {
        String prefix = INPUT_MARKER + recipeId + ":";
        Set<String> result = new LinkedHashSet<>();
        if (state != null) {
            for (String marker : state.provenance()) {
                if (marker.startsWith(prefix)) {
                    result.add(marker);
                }
            }
        }
        return result;
    }

    private static String recipeMarker(AlchemyCauldronRecipe recipe) {
        return RECIPE_MARKER + recipe.id();
    }

    private static String inputMarker(
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
        return INPUT_MARKER + recipe.id() + ":" + ingredient.id();
    }

    private static String readyMarker(AlchemyCauldronRecipe recipe) {
        return READY_MARKER + recipe.id();
    }

    private static String reactionId(
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
        return REACTION_PREFIX + recipe.id() + ":" + ingredient.id();
    }

    public record Match(
            AlchemyCauldronRecipe recipe,
            AlchemyCauldronRecipe.IngredientStep ingredient
    ) {
    }
}
