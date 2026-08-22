package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.manual.AlchemyManual;
import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;

public final class AlchemyMaterialCatalogGameTest {
    @GameTest(maxTicks = 20)
    public void manualPageCountTracksMaterialCatalog(GameTestHelper helper) {
        int expected = AlchemyMaterialCatalog.entries().size() + 3;
        if (AlchemyManual.pageKeys().size() != expected) {
            helper.fail("Alchemy manual page count did not follow its material catalog: expected "
                    + expected + ", got " + AlchemyManual.pageKeys().size());
            return;
        }
        if (new HashSet<>(AlchemyMaterialCatalog.pageKeys()).size() != AlchemyMaterialCatalog.entries().size()) {
            helper.fail("Alchemy material catalog contains duplicate page keys");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void organicAndCraftMaterialsJoinAlchemyResearch(GameTestHelper helper) {
        if (!AlchemyMaterialCatalog.contains(Items.RESIN_CLUMP)
                || !AlchemyMaterialCatalog.contains(Items.HONEY_BLOCK)
                || !AlchemyMaterialCatalog.contains(AlchemyItems.PIG_MANURE)
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.RESIN_CLUMP))
                || !MultiOutcomeBrewing.isOutcomeIngredient(new ItemStack(Items.HONEY_BLOCK))
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
}
