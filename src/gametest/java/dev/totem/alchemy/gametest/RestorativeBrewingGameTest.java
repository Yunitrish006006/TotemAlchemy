package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

public final class RestorativeBrewingGameTest {
    private static final double EPSILON = 0.000_001D;

    @GameTest(maxTicks = 20)
    public void gameplayIndependentRollsMayProduceNoEffect(GameTestHelper helper) {
        ItemStack sugar = new ItemStack(Items.SUGAR);
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        List<MultiOutcomeBrewing.Outcome> outcomes = MultiOutcomeBrewing.chooseOutcomes(
                sugar, awkward, 0.999F, 0.999F, 0.999F);
        require(helper, outcomes.isEmpty(), "All-miss sugar rolls unexpectedly forced an effect");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:sugar", "minecraft:swiftness") - 0.50D) < EPSILON,
                "Swiftness truth was not the configured independent 50% chance");
        require(helper, Math.abs(MultiOutcomeBrewing.noEffectProbability("minecraft:sugar") - 0.28D) < EPSILON,
                "Sugar no-effect truth was not 28% from independent 50/30/20 rolls");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void restorativeChancesScaleWithAcquisitionCost(GameTestHelper helper) {
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:apple", "minecraft:healing") - 0.03D) < EPSILON,
                "Apple healing chance was not 3%");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:honey_bottle", "minecraft:healing") - 0.08D) < EPSILON,
                "Honey healing chance was not 8%");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:golden_apple", "minecraft:healing") - 0.40D) < EPSILON,
                "Golden apple healing chance was not 40%");
        require(helper, Math.abs(MultiOutcomeBrewing.outcomeProbability(
                        "minecraft:enchanted_golden_apple", "minecraft:healing") - 0.65D) < EPSILON,
                "Enchanted golden apple healing chance was not 65%");
        require(helper, VanillaBrewingChance.chanceFor(new ItemStack(Items.GOLDEN_APPLE)) == 0.94D,
                "Golden apple processing success was not 94%");
        require(helper, VanillaBrewingChance.chanceFor(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)) == 0.99D,
                "Enchanted golden apple processing success was not 99%");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void noEffectObservationIsStoredWithoutUnlockingPotion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack sugar = new ItemStack(Items.SUGAR);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                    player.level(), player.blockPosition(), sugar,
                    List.of(awkward), List.of(awkward), 300, List.of(), player.getUUID());
            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            require(helper, data.research(player.getUUID()).getOrDefault("minecraft:sugar>totem:none", 0) == 1,
                    "No-effect result was not persisted as research");
            require(helper, data.materialSampleCount(player.getUUID(), "minecraft:sugar") == 1,
                    "No-effect result did not count as a material sample");
            require(helper, data.discoveries(player.getUUID()).isEmpty(),
                    "No-effect result incorrectly unlocked a potion discovery");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void cauldronAcceptsAValidNoEffectReaction(GameTestHelper helper) {
        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(awkward);
        require(helper, AlchemyMixtureBrewing.scheduleOutcomeSet(
                        helper.getLevel(), state, new ItemStack(Items.SUGAR), List.of()),
                "Cauldron rejected an all-miss material reaction");
        state.tickReactions(Integer.MAX_VALUE);
        require(helper, state.effects().isEmpty(),
                "No-effect cauldron reaction unexpectedly created an effect");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
