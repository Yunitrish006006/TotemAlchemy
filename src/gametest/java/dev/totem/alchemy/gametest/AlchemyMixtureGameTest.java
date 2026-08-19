package dev.totem.alchemy.gametest;

import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class AlchemyMixtureGameTest {
    private static final double EPSILON = 0.0001D;

    @GameTest(maxTicks = 40)
    public void opposingSpeedEffectsNeutralizeByEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.putEffect("minecraft:speed", 2_000.0D, 0);
        state.putEffect("minecraft:slowness", 800.0D, 0);

        require(helper, !state.effects().containsKey("minecraft:slowness"),
                "Slowness remained after a smaller opposing dose was neutralized");
        requireNear(helper, state.effects().get("minecraft:speed").potencyTicks(), 1_200.0D,
                "Speed/slowness neutralization did not conserve the remaining quantity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void strengthAndWeaknessNeutralizeAcrossFireflyFamily(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.putEffect("minecraft:strength", 900.0D, 0);
        state.putEffect("deadrecall:firefly_strength", 600.0D, 0);
        state.putEffect("minecraft:weakness", 750.0D, 0);

        double remaining = state.effects().entrySet().stream()
                .filter(entry -> entry.getKey().equals("minecraft:strength")
                        || entry.getKey().equals("deadrecall:firefly_strength"))
                .mapToDouble(entry -> entry.getValue().potencyTicks())
                .sum();
        requireNear(helper, remaining, 750.0D,
                "Weakness did not neutralize the combined strength-family quantity");
        require(helper, !state.effects().containsKey("minecraft:weakness"),
                "Weakness remained despite a larger positive strength-family dose");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void bottlingAndRecombiningConservesMixedEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(2);
        state.putEffect("minecraft:night_vision", 4_000.0D, 0);
        state.putEffect("minecraft:regeneration", 1_800.0D, 0);
        double before = totalPotency(state);

        AlchemyMixtureState bottle = state.extractBottle();
        double split = totalPotency(state) + totalPotency(bottle);
        requireNear(helper, split, before,
                "Extracting a bottle created or destroyed effect quantity");
        require(helper, state.volumeUnits() == 1 && bottle.volumeUnits() == 1,
                "A two-unit mixture did not split into one-unit bottle states");
        require(helper, state.mergeFrom(bottle), "The extracted bottle could not be recombined");
        require(helper, state.volumeUnits() == 2, "Recombining the bottle did not restore liquid volume");
        requireNear(helper, totalPotency(state), before,
                "Recombining a bottle changed the conserved effect quantity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void unfinishedReactionProgressSurvivesBottleRoundTrip(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(3);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, new ItemStack(Items.NETHER_WART)),
                "Water mixture could not schedule a nether-wart reaction");
        state.tickReactions(123);
        AlchemyMixtureState.Reaction before = state.reactions().iterator().next();

        AlchemyMixtureState bottled = state.extractBottle();
        AlchemyMixtureState.Reaction bottleReaction = bottled.reactions().iterator().next();
        require(helper, bottleReaction.elapsedTicks() == before.elapsedTicks(),
                "Bottling reset unfinished reaction progress");
        require(helper, bottleReaction.remainingTicks() == before.remainingTicks(),
                "Bottling changed unfinished reaction time");

        ItemStack bottle = AlchemyMixtureBottle.toPotion(bottled);
        require(helper, AlchemyMixtureBottle.hasStoredMixture(bottle),
                "Unfinished bottle did not carry mixture metadata");
        AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(bottle);
        AlchemyMixtureState.Reaction restoredReaction = restored.reactions().iterator().next();
        require(helper, restoredReaction.elapsedTicks() == before.elapsedTicks(),
                "ItemStack round-trip reset reaction progress");
        require(helper, restoredReaction.remainingTicks() == before.remainingTicks(),
                "ItemStack round-trip changed remaining reaction time");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void livePotionRegistryCompletesWaterToAwkwardInCauldron(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(3);
        ItemStack netherWart = new ItemStack(Items.NETHER_WART);
        require(helper, AlchemyMixtureBrewing.canReact(helper.getLevel(), state, netherWart),
                "Live PotionBrewing registry did not accept water + nether wart for cauldron chemistry");
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, netherWart),
                "Cauldron chemistry could not schedule water + nether wart");
        state.tickReactions(AlchemyMixtureState.DEFAULT_REACTION_TICKS);
        require(helper, !state.hasPendingReactions(), "Completed cauldron reaction remained pending");
        require(helper, "minecraft:awkward".equals(state.canonicalPotionId()),
                "Completed water + nether wart reaction did not become canonical awkward potion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void twoDifferentPotionBottlesCanMixAndCounteract(GameTestHelper helper) {
        AlchemyMixtureState swiftness = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        AlchemyMixtureState slowness = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SLOWNESS));
        require(helper, swiftness.mergeFrom(slowness),
                "Two different potion bottle states could not share one cauldron mixture");
        require(helper, swiftness.volumeUnits() == 2,
                "Mixing two potion bottles did not preserve two units of liquid");
        require(helper, swiftness.canonicalPotionId() == null,
                "A heterogeneous mixture incorrectly retained one canonical potion identity");
        require(helper, !(swiftness.effects().containsKey("minecraft:speed")
                        && swiftness.effects().containsKey("minecraft:slowness")),
                "Swiftness and slowness remained simultaneously instead of counteracting");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void redstoneAndGlowstoneConserveMixedEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState base = new AlchemyMixtureState(2);
        base.putEffect("minecraft:regeneration", 7_200.0D, 0);
        double original = totalPotency(base);

        AlchemyMixtureState redstone = base.copy();
        redstone.applyRedstoneModifier();
        requireNear(helper, totalPotency(redstone), original,
                "Redstone modifier changed total effect quantity");

        AlchemyMixtureState glowstone = base.copy();
        glowstone.applyGlowstoneModifier();
        requireNear(helper, totalPotency(glowstone), original,
                "Glowstone modifier changed total effect quantity");
        require(helper, glowstone.effects().get("minecraft:regeneration").amplifierCap()
                        > base.effects().get("minecraft:regeneration").amplifierCap(),
                "Glowstone did not favour higher potency");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void encodedMixturePreservesEffectsAndPendingChemistry(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(2);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, new ItemStack(Items.NETHER_WART)),
                "Could not create pending chemistry for persistence test");
        state.tickReactions(77);
        state.setStability(63);

        AlchemyMixtureState restored = AlchemyMixtureState.decode(state.encode());
        require(helper, restored.volumeUnits() == 2, "Mixture codec lost liquid volume");
        require(helper, restored.stability() == 63, "Mixture codec lost stability");
        require(helper, restored.reactions().size() == 1, "Mixture codec lost pending reaction");
        AlchemyMixtureState.Reaction reaction = restored.reactions().iterator().next();
        require(helper, reaction.elapsedTicks() == 77, "Mixture codec lost reaction progress");
        require(helper, reaction.remainingTicks() == AlchemyMixtureState.DEFAULT_REACTION_TICKS - 77,
                "Mixture codec lost remaining reaction time");
        helper.succeed();
    }

    private static double totalPotency(AlchemyMixtureState state) {
        return state.effects().values().stream()
                .mapToDouble(AlchemyMixtureState.EffectDose::potencyTicks)
                .sum();
    }

    private static void requireNear(GameTestHelper helper, double actual, double expected, String message) {
        require(helper, Math.abs(actual - expected) <= EPSILON,
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
