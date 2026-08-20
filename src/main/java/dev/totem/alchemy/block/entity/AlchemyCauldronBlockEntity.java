package dev.totem.alchemy.block.entity;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.alchemy.AlchemyHandler;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureColor;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.LinkedHashSet;
import java.util.Set;

public class AlchemyCauldronBlockEntity extends BlockEntity {
    private Identifier recipeId;
    private final Set<String> addedIngredients = new LinkedHashSet<>();
    private final Set<String> cookedIngredients = new LinkedHashSet<>();
    private boolean readyForExtraction;
    private int cookTime;
    private AlchemyMixtureState mixture = AlchemyMixtureState.empty();
    private int lastSyncedColor = -1;
    private int lastSyncedVolume = -1;
    private AlchemyMixtureTiming.State lastSyncedTimingState;
    private long lastVisualSyncTick = Long.MIN_VALUE;

    public AlchemyCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemyBlockEntities.ALCHEMY_CAULDRON, pos, state);
    }

    public Identifier getRecipeId() {
        return recipeId;
    }

    public boolean hasMixture() {
        return mixture != null && !mixture.isEmpty();
    }

    public AlchemyMixtureState mixtureSnapshot() {
        return mixture == null ? AlchemyMixtureState.empty() : mixture.copy();
    }

    public int mixtureColorRgb() {
        return AlchemyMixtureColor.rgb(mixture);
    }

    public boolean initializeMixture(AlchemyMixtureState initial) {
        if (initial == null || initial.isEmpty() || recipeId != null || readyForExtraction || hasMixture()) {
            return false;
        }
        mixture = initial.copy();
        setChanged();
        return true;
    }

    public boolean mergeMixture(AlchemyMixtureState incoming) {
        if (incoming == null || incoming.isEmpty() || recipeId != null || readyForExtraction) {
            return false;
        }
        if (!hasMixture()) {
            mixture = incoming.copy();
            setChanged();
            return true;
        }
        boolean merged = mixture.mergeFrom(incoming);
        if (merged) {
            setChanged();
        }
        return merged;
    }

    public boolean scheduleMixtureReaction(Level level, ItemStack ingredient) {
        if (!hasMixture() || recipeId != null || readyForExtraction) {
            return false;
        }
        boolean scheduled = AlchemyMixtureBrewing.schedule(level, mixture, ingredient);
        if (scheduled) {
            setChanged();
        }
        return scheduled;
    }

    public AlchemyMixtureState extractMixtureBottle() {
        return extractMixtureUnits(1);
    }

    public AlchemyMixtureState extractMixtureUnits(int units) {
        if (!hasMixture() || units <= 0) {
            return AlchemyMixtureState.empty();
        }
        AlchemyMixtureState extracted = mixture.extractUnits(units);
        setChanged();
        return extracted;
    }

    public boolean canAddIngredient(AlchemyCauldronRecipe recipe, AlchemyCauldronRecipe.IngredientStep ingredient) {
        if (recipe == null || ingredient == null || readyForExtraction || hasMixture()) {
            return false;
        }
        if (recipeId != null && !recipeId.equals(recipe.id())) {
            return false;
        }
        return !addedIngredients.contains(ingredient.id()) && !cookedIngredients.contains(ingredient.id());
    }

    public boolean addIngredient(AlchemyCauldronRecipe recipe, AlchemyCauldronRecipe.IngredientStep ingredient) {
        if (!canAddIngredient(recipe, ingredient)) {
            return false;
        }
        recipeId = recipe.id();
        addedIngredients.add(ingredient.id());
        setChanged();
        return true;
    }

    public boolean canExtractBottledResult(AlchemyCauldronRecipe recipe, ItemStack stack) {
        return !hasMixture()
                && recipe != null
                && recipeId != null
                && recipeId.equals(recipe.id())
                && readyForExtraction
                && recipe.result().type() == AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM
                && recipe.result().matchesContainer(stack);
    }

    public boolean extractBottledResult(AlchemyCauldronRecipe recipe, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        if (!canExtractBottledResult(recipe, stack)) {
            return false;
        }

        int fillLevel = state.getValue(LayeredCauldronBlock.LEVEL);
        if (fillLevel > LayeredCauldronBlock.MIN_FILL_LEVEL) {
            level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, fillLevel - 1), 3);
            setChanged();
        } else {
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlchemyCauldronBlockEntity cauldron) {
        if (cauldron.hasMixture()) {
            if (AlchemyHandler.hasLitCampfireBelow(level, pos)) {
                boolean changed = cauldron.mixture.hasPendingReactions()
                        ? cauldron.mixture.tickReactions(1)
                        : cauldron.mixture.tickOvercook(level.getRandom(), 1);
                if (changed) {
                    cauldron.setChanged();
                }
            }
            return;
        }

        if (cauldron.recipeId == null) {
            cauldron.cookTime = 0;
            return;
        }

        AlchemyCauldronRecipe recipe = AlchemyCauldronRecipes.get(cauldron.recipeId);
        if (recipe == null) {
            cauldron.cookTime = 0;
            return;
        }

        if (cauldron.readyForExtraction) {
            cauldron.cookTime = 0;
            return;
        }

        if (recipe.requiresLitCampfire() && !AlchemyHandler.hasLitCampfireBelow(level, pos)) {
            cauldron.cookTime = 0;
            cauldron.setChanged();
            return;
        }

        if (recipe.cookMode() == AlchemyCauldronRecipe.CookMode.PER_INGREDIENT) {
            cauldron.tickPerIngredientRecipe(level, pos, state, recipe);
        } else {
            cauldron.tickAfterAllInputsRecipe(level, pos, recipe);
        }
    }

    private void tickPerIngredientRecipe(Level level, BlockPos pos, BlockState state, AlchemyCauldronRecipe recipe) {
        String next = nextCookableIngredient(recipe);
        if (next == null) {
            cookTime = 0;
            if (isComplete(recipe)) {
                completeRecipe(level, pos, recipe);
            }
            return;
        }

        cookTime++;
        if (cookTime < Math.max(1, recipe.cookTicks())) {
            setChanged();
            return;
        }

        cookTime = 0;
        addedIngredients.remove(next);
        cookedIngredients.add(next);

        if (isComplete(recipe)) {
            completeRecipe(level, pos, recipe);
            return;
        }

        if (recipe.consumeLevelPerCook()) {
            int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            if (waterLevel > LayeredCauldronBlock.MIN_FILL_LEVEL) {
                level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
            }
        }
        setChanged();
    }

    private void tickAfterAllInputsRecipe(Level level, BlockPos pos, AlchemyCauldronRecipe recipe) {
        if (!hasAllInputs(recipe)) {
            cookTime = 0;
            return;
        }

        cookTime++;
        if (cookTime < Math.max(1, recipe.cookTicks())) {
            setChanged();
            return;
        }

        cookTime = 0;
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            addedIngredients.remove(ingredient.id());
            cookedIngredients.add(ingredient.id());
        }
        completeRecipe(level, pos, recipe);
    }

    private void completeRecipe(Level level, BlockPos pos, AlchemyCauldronRecipe recipe) {
        playSound(level, pos, recipe.completeSound(), 1.0F, 1.0F);
        notifyNearbyPlayers(level, pos, recipe.successMessageKey());

        if (recipe.result().type() == AlchemyCauldronRecipe.ResultType.BOTTLED_ITEM) {
            readyForExtraction = true;
            setChanged();
            return;
        }

        ItemStack resultStack = recipe.createResultStack();
        if (!resultStack.isEmpty()) {
            ItemEntity result = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                    resultStack);
            result.setDeltaMovement(0.0D, 0.05D, 0.0D);
            level.addFreshEntity(result);
        }
        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
    }

    private static void notifyNearbyPlayers(Level level, BlockPos pos, String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= 64.0D) {
                player.sendOverlayMessage(Component.translatable(messageKey));
            }
        }
    }

    private String nextCookableIngredient(AlchemyCauldronRecipe recipe) {
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            if (addedIngredients.contains(ingredient.id()) && !cookedIngredients.contains(ingredient.id())) {
                return ingredient.id();
            }
        }
        return null;
    }

    private boolean hasAllInputs(AlchemyCauldronRecipe recipe) {
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            if (!addedIngredients.contains(ingredient.id()) && !cookedIngredients.contains(ingredient.id())) {
                return false;
            }
        }
        return true;
    }

    private boolean isComplete(AlchemyCauldronRecipe recipe) {
        for (AlchemyCauldronRecipe.IngredientStep ingredient : recipe.ingredients()) {
            if (!cookedIngredients.contains(ingredient.id())) {
                return false;
            }
        }
        return true;
    }

    private static void playSound(Level level, BlockPos pos, Identifier soundId, float volume, float pitch) {
        SoundEvent sound = AlchemyCauldronRecipes.getSound(soundId);
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide()) {
            return;
        }
        int color = mixtureColorRgb();
        int volume = hasMixture() ? mixture.volumeUnits() : 0;
        AlchemyMixtureTiming.State timingState = AlchemyMixtureTiming.classify(mixture);
        long gameTime = level.getGameTime();
        boolean volumeChanged = volume != lastSyncedVolume;
        boolean colorChanged = color != lastSyncedColor;
        boolean timingChanged = timingState != lastSyncedTimingState;
        boolean intervalElapsed = lastVisualSyncTick == Long.MIN_VALUE || gameTime - lastVisualSyncTick >= 10L;
        if (volumeChanged || timingChanged || (colorChanged && intervalElapsed)) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            lastSyncedColor = color;
            lastSyncedVolume = volume;
            lastSyncedTimingState = timingState;
            lastVisualSyncTick = gameTime;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (recipeId != null) {
            output.putString("recipe_id", recipeId.toString());
        }
        output.putString("added_ingredients", String.join(",", addedIngredients));
        output.putString("cooked_ingredients", String.join(",", cookedIngredients));
        output.putBoolean("ready_for_extraction", readyForExtraction);
        output.putInt("cook_time", cookTime);
        if (hasMixture()) {
            output.putString("mixture_state", mixture.encode());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        recipeId = readIdentifier(input.getStringOr("recipe_id", ""));
        addedIngredients.clear();
        cookedIngredients.clear();
        addedIngredients.addAll(readIngredientSet(input.getStringOr("added_ingredients", "")));
        cookedIngredients.addAll(readIngredientSet(input.getStringOr("cooked_ingredients", "")));
        readyForExtraction = input.getBooleanOr("ready_for_extraction", false);
        cookTime = input.getIntOr("cook_time", 0);
        mixture = AlchemyMixtureState.decode(input.getStringOr("mixture_state", ""));

        if (hasMixture()) {
            recipeId = null;
            addedIngredients.clear();
            cookedIngredients.clear();
            readyForExtraction = false;
            cookTime = 0;
            return;
        }

        if (recipeId == null) {
            loadLegacyState(input);
        }
    }

    private void loadLegacyState(ValueInput input) {
        String legacyMode = input.getStringOr("recipe_mode", "NONE");
        if ("SALTPETER".equals(legacyMode)) {
            recipeId = Identifier.fromNamespaceAndPath("deadrecall", "saltpeter");
            addLegacyIngredient(input, "ash", "wood_ash");
            addLegacyIngredient(input, "mushroom", "mushroom");
            addLegacyIngredient(input, "manure", "pig_manure");
        } else if ("HOT_COCOA".equals(legacyMode)) {
            recipeId = Identifier.fromNamespaceAndPath("deadrecall", "hot_cocoa");
            boolean cocoaAdded = input.getBooleanOr("cocoa_added", false);
            boolean hotCocoaReady = input.getBooleanOr("hot_cocoa_ready", false);
            readyForExtraction = hotCocoaReady;
            if (hotCocoaReady) {
                cookedIngredients.add("milk");
                cookedIngredients.add("cocoa");
            } else {
                addedIngredients.add("milk");
                if (cocoaAdded) {
                    addedIngredients.add("cocoa");
                }
            }
        }
    }

    private void addLegacyIngredient(ValueInput input, String legacyKey, String ingredientId) {
        if (input.getBooleanOr(legacyKey + "_cooked", false)) {
            cookedIngredients.add(ingredientId);
        } else if (input.getBooleanOr(legacyKey + "_added", false)) {
            addedIngredients.add(ingredientId);
        }
    }

    private static Identifier readIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Identifier.tryParse(value);
    }

    private static Set<String> readIngredientSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
