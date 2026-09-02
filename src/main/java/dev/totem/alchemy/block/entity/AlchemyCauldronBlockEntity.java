package dev.totem.alchemy.block.entity;

import dev.totem.alchemy.alchemy.AlchemyCauldronRecipe;
import dev.totem.alchemy.alchemy.AlchemyCauldronRecipes;
import dev.totem.alchemy.alchemy.AlchemyHandler;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureColor;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.AlchemyMixtureTiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AlchemyCauldronBlockEntity extends BlockEntity {
    private Identifier recipeId;
    private final Set<String> addedIngredients = new LinkedHashSet<>();
    private final Set<String> cookedIngredients = new LinkedHashSet<>();
    private final Map<String, PendingDiscovery> pendingDiscoveries = new LinkedHashMap<>();
    private boolean readyForExtraction;
    private int cookTime;
    private AlchemyMixtureState mixture = AlchemyMixtureState.empty();
    private int lastSyncedColor = -1;
    private int lastSyncedVolume = -1;
    private int lastSyncedTimingSignature = Integer.MIN_VALUE;
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
        pendingDiscoveries.clear();
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
        AlchemyMixtureBrewing.ScheduleResult scheduled =
                AlchemyMixtureBrewing.scheduleDetailed(level, mixture, ingredient);
        if (!scheduled.scheduled()) {
            return false;
        }
        if (scheduled.researchable()) {
            pendingDiscoveries.put(
                    scheduled.reactionId(),
                    new PendingDiscovery(nearestResearcherId(level, worldPosition), scheduled.resultPotionIds())
            );
        }
        setChanged();
        return true;
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
                boolean changed = false;
                if (cauldron.mixture.hasCompletedStages()) {
                    changed = cauldron.mixture.tickCompletedStages(level.getRandom(), 1);
                }
                if (cauldron.mixture.hasPendingReactions()) {
                    List<AlchemyMixtureState.Reaction> before = List.copyOf(cauldron.mixture.reactions());
                    boolean reactionsChanged = cauldron.mixture.tickReactions(1);
                    changed |= reactionsChanged;
                    if (reactionsChanged && level instanceof ServerLevel serverLevel) {
                        cauldron.recordCompletedMixtureReactions(serverLevel, pos, before);
                    }
                } else if (!cauldron.mixture.hasCompletedStages()) {
                    changed = cauldron.mixture.tickOvercook(level.getRandom(), 1);
                }
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

    private void recordCompletedMixtureReactions(
            ServerLevel level,
            BlockPos pos,
            List<AlchemyMixtureState.Reaction> before
    ) {
        Set<String> active = new LinkedHashSet<>();
        for (AlchemyMixtureState.Reaction reaction : mixture.reactions()) {
            active.add(reaction.id());
        }
        for (AlchemyMixtureState.Reaction reaction : before) {
            if (active.contains(reaction.id())) {
                continue;
            }
            PendingDiscovery pending = pendingDiscoveries.remove(reaction.id());
            if (pending == null) {
                continue;
            }
            Identifier ingredientId = Identifier.tryParse(reaction.ingredientId());
            if (ingredientId == null) {
                continue;
            }
            Item ingredientItem = BuiltInRegistries.ITEM.getValue(ingredientId);
            if (ingredientItem == null) {
                continue;
            }
            ItemStack ingredient = new ItemStack(ingredientItem);
            List<Holder<Potion>> results = new ArrayList<>();
            for (String potionId : pending.resultPotionIds()) {
                Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(potionId);
                if (potion != null) {
                    results.add(potion);
                }
            }
            AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                    level,
                    pos,
                    ingredient,
                    List.of(),
                    List.of(),
                    reaction.requiredTicks(),
                    results,
                    pending.researcherId()
            );
        }
    }

    private static UUID nearestResearcherId(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        ServerPlayer nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double distance = player.distanceToSqr(x, y, z);
            if (distance <= 64.0D && distance < bestDistance) {
                nearest = player;
                bestDistance = distance;
            }
        }
        return nearest == null ? null : nearest.getUUID();
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
        int timingSignature = AlchemyMixtureTiming.visualSignature(mixture);
        long gameTime = level.getGameTime();
        boolean volumeChanged = volume != lastSyncedVolume;
        boolean colorChanged = color != lastSyncedColor;
        boolean timingChanged = timingSignature != lastSyncedTimingSignature;
        boolean intervalElapsed = lastVisualSyncTick == Long.MIN_VALUE || gameTime - lastVisualSyncTick >= 10L;
        if (volumeChanged || timingChanged || (colorChanged && intervalElapsed)) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            lastSyncedColor = color;
            lastSyncedVolume = volume;
            lastSyncedTimingSignature = timingSignature;
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
        if (!pendingDiscoveries.isEmpty()) {
            output.putString("pending_discoveries", encodePendingDiscoveries());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        recipeId = readIdentifier(input.getStringOr("recipe_id", ""));
        addedIngredients.clear();
        cookedIngredients.clear();
        pendingDiscoveries.clear();
        addedIngredients.addAll(readIngredientSet(input.getStringOr("added_ingredients", "")));
        cookedIngredients.addAll(readIngredientSet(input.getStringOr("cooked_ingredients", "")));
        pendingDiscoveries.putAll(decodePendingDiscoveries(input.getStringOr("pending_discoveries", "")));
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

        pendingDiscoveries.clear();
        if (recipeId == null) {
            loadLegacyState(input);
        }
    }

    private String encodePendingDiscoveries() {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, PendingDiscovery> entry : pendingDiscoveries.entrySet()) {
            PendingDiscovery pending = entry.getValue();
            String researcher = pending.researcherId() == null ? "" : pending.researcherId().toString();
            String results = pending.resultPotionIds().stream()
                    .map(AlchemyCauldronBlockEntity::encodeToken)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            entries.add(encodeToken(entry.getKey()) + "|" + researcher + "|" + results);
        }
        return String.join(";", entries);
    }

    private static Map<String, PendingDiscovery> decodePendingDiscoveries(String value) {
        Map<String, PendingDiscovery> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String encodedEntry : value.split(";")) {
            if (encodedEntry.isBlank()) {
                continue;
            }
            String[] parts = encodedEntry.split("\\|", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                String reactionId = decodeToken(parts[0]);
                UUID researcherId = parts[1].isBlank() ? null : UUID.fromString(parts[1]);
                List<String> potionIds = new ArrayList<>();
                if (!parts[2].isBlank()) {
                    for (String encodedPotion : parts[2].split(",")) {
                        if (!encodedPotion.isBlank()) {
                            potionIds.add(decodeToken(encodedPotion));
                        }
                    }
                }
                if (!reactionId.isBlank()) {
                    result.put(reactionId, new PendingDiscovery(researcherId, potionIds));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy/corrupt metadata without invalidating the mixture itself.
            }
        }
        return result;
    }

    private static String encodeToken(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeToken(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
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

    private record PendingDiscovery(UUID researcherId, List<String> resultPotionIds) {
        private PendingDiscovery {
            resultPotionIds = List.copyOf(resultPotionIds == null ? List.of() : resultPotionIds);
        }
    }
}
