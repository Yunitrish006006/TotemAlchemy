package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.discovery.AlchemyConflictCatalog;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
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

public final class AlchemyConflictDiscoveryGameTest {
    @GameTest(maxTicks = 20)
    public void conflictCatalogExposesFourCanonicalReactionPairs(GameTestHelper helper) {
        if (AlchemyConflictCatalog.entries().size() != 4) {
            helper.fail("Expected four canonical opposing-effect research pairs, got "
                    + AlchemyConflictCatalog.entries().size());
            return;
        }

        AlchemyMixtureState speed = new AlchemyMixtureState(1);
        speed.putEffect("minecraft:speed", 1200.0D, 0);
        AlchemyMixtureState slowness = new AlchemyMixtureState(1);
        slowness.putEffect("minecraft:slowness", 400.0D, 0);
        AlchemyMixtureState resolved = speed.copy();
        if (!resolved.mergeFrom(slowness)) {
            helper.fail("Test mixtures could not be merged");
            return;
        }

        List<AlchemyConflictCatalog.Observation> observations =
                AlchemyConflictCatalog.observe(speed, slowness, resolved);
        if (observations.size() != 1
                || !"speed_slowness".equals(observations.getFirst().entry().id())
                || observations.getFirst().resolution() != AlchemyConflictCatalog.Resolution.POSITIVE_REMAINS) {
            helper.fail("Speed/slowness conflict was not classified as positive residual: " + observations);
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void simultaneousSugarOutcomesUnlockReactionResearch(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.SUGAR);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            List<MultiOutcomeBrewing.Outcome> outcomes = List.of(
                    new MultiOutcomeBrewing.Outcome(
                            Potions.SWIFTNESS, "message.deadrecall.alchemy.outcome.swiftness"),
                    new MultiOutcomeBrewing.Outcome(
                            Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness")
            );
            ItemStack output = AlchemyMixtureBrewing.applyBrewingStandOutcomes(
                    ingredient,
                    awkward,
                    PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS),
                    outcomes
            );

            AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                    player.level(),
                    player.blockPosition(),
                    ingredient,
                    List.of(awkward),
                    List.of(output),
                    400,
                    outcomes.stream().map(MultiOutcomeBrewing.Outcome::potion).toList(),
                    player.getUUID()
            );

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            AlchemyConflictCatalog.Entry speedSlowness = AlchemyConflictCatalog.entries().stream()
                    .filter(entry -> "speed_slowness".equals(entry.id()))
                    .findFirst()
                    .orElseThrow();
            if (!data.has(player.getUUID(), AlchemyConflictCatalog.relationKey(speedSlowness))) {
                helper.fail("Observed sugar conflict did not unlock its reaction research relation");
                return;
            }
            boolean hasResolution = java.util.Arrays.stream(AlchemyConflictCatalog.Resolution.values())
                    .anyMatch(resolution -> data.has(
                            player.getUUID(), AlchemyConflictCatalog.resolutionKey(speedSlowness, resolution)));
            if (!hasResolution) {
                helper.fail("Observed sugar conflict did not record how the reaction resolved");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
