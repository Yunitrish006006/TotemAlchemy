package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.manual.AlchemyManual;
import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;

public final class AlchemyMaterialCatalogGameTest {
    @GameTest(maxTicks = 20)
    public void manualPageCountTracksMaterialCatalog(GameTestHelper helper) {
        int expected = AlchemyMaterialCatalog.entries().size() + 4;
        if (AlchemyManual.pageKeys().size() != expected) {
            helper.fail("Alchemy manual page count did not follow its material catalog plus reaction research: expected "
                    + expected + ", got " + AlchemyManual.pageKeys().size());
            return;
        }
        if (new HashSet<>(AlchemyMaterialCatalog.pageKeys()).size() != AlchemyMaterialCatalog.entries().size()) {
            helper.fail("Alchemy material catalog contains duplicate page keys");
            return;
        }
        if (AlchemyMaterialCatalog.entries().size() < 50) {
            helper.fail("Expanded Alchemy material catalog unexpectedly shrank below the broad organic/crafting set");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void organicAndCraftMaterialsJoinAlchemyResearch(GameTestHelper helper) {
        if (!AlchemyMaterialCatalog.contains(Items.RESIN_CLUMP)
                || !AlchemyMaterialCatalog.contains(Items.HONEY_BLOCK)
                || !AlchemyMaterialCatalog.contains(Items.FEATHER)
                || !AlchemyMaterialCatalog.contains(Items.STRING)
                || !AlchemyMaterialCatalog.contains(AlchemyItems.PIG_MANURE)
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.RESIN_CLUMP))
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.HONEY_BLOCK))
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.FEATHER))
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.STRING))
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(AlchemyItems.PIG_MANURE))) {
            helper.fail("Expanded organic/material ingredients were not wired into both research and brewing");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void vanillaPotionIdentityDominatesRareSideEffects(GameTestHelper helper) {
        double swiftness = MultiOutcomeBrewing.outcomeProbability("minecraft:sugar", "minecraft:swiftness");
        double slowness = MultiOutcomeBrewing.outcomeProbability("minecraft:sugar", "minecraft:slowness");
        double healing = MultiOutcomeBrewing.outcomeProbability("minecraft:glistering_melon_slice", "minecraft:healing");
        double fireResistance = MultiOutcomeBrewing.outcomeProbability("minecraft:magma_cream", "minecraft:fire_resistance");
        if (swiftness < 0.90D || slowness > 0.05D || healing < 0.90D || fireResistance < 0.90D) {
            helper.fail("Vanilla-like primary effects are not sufficiently dominant: sugar="
                    + swiftness + "/" + slowness + ", melon=" + healing + ", magma=" + fireResistance);
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void pufferfishKeepsStrongPoisonSideEffect(GameTestHelper helper) {
        double waterBreathing = MultiOutcomeBrewing.outcomeProbability(
                "minecraft:pufferfish", "minecraft:water_breathing");
        double poison = MultiOutcomeBrewing.outcomeProbability(
                "minecraft:pufferfish", "minecraft:poison");
        double weakness = MultiOutcomeBrewing.outcomeProbability(
                "minecraft:pufferfish", "minecraft:weakness");
        if (waterBreathing < 0.90D || poison < 0.35D || poison > 0.60D || weakness < 0.05D) {
            helper.fail("Pufferfish no longer has a strong poisonous side-effect profile: water="
                    + waterBreathing + ", poison=" + poison + ", weakness=" + weakness);
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void regenerationAndPoisonNeutralizeByPotency(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.putEffect("minecraft:regeneration", 1200.0D, 0);
        state.putEffect("minecraft:poison", 400.0D, 0);

        AlchemyMixtureState.EffectDose regeneration = state.effects().get("minecraft:regeneration");
        if (regeneration == null
                || state.effects().containsKey("minecraft:poison")
                || Math.abs(regeneration.potencyTicks() - 800.0D) > 0.0001D) {
            helper.fail("Regeneration and poison did not neutralize by potency: " + state.effects());
            return;
        }
        helper.succeed();
    }
}
