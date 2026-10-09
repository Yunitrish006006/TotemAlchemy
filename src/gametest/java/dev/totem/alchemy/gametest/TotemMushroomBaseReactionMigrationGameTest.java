package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.AlchemyBrewing;
import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.AlchemyReactionResolver;
import dev.totem.alchemy.reaction.BaseReaction;
import dev.totem.alchemy.resource.AlchemyContentPackState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class TotemMushroomBaseReactionMigrationGameTest {
    private static final Identifier REACTION_ID =
            Identifier.fromNamespaceAndPath("totem_alchemy", "mushroom/red_mushroom");
    private static final Identifier AWKWARD_BASE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/awkward");
    private static final double EPSILON = 0.000_001D;

    @GameTest(maxTicks = 40)
    public void mushroomStarterAndFullWaterBaseFollowTotemPackState(GameTestHelper helper) {
        BaseReaction reaction = AlchemyReactionDataLoader.baseReactions().stream()
                .filter(candidate -> candidate.id().equals(REACTION_ID))
                .findFirst().orElse(null);
        require(helper, !BrewingMaterialSettings.isStarter(Items.RED_MUSHROOM),
                "Always-on settings still expose Red Mushroom as a base starter");

        AlchemyMixtureState water = AlchemyMixtureBrewing.waterState(3);
        ItemStack mushroom = new ItemStack(Items.RED_MUSHROOM);
        if (!AlchemyContentPackState.totemAlchemyEnabled()) {
            require(helper, reaction == null,
                    "Totem mushroom base reaction leaked while Totem Alchemy was disabled");
            require(helper, AlchemyReactionResolver.resolveBaseReaction(water, mushroom).isEmpty(),
                    "Disabled Totem Alchemy still resolves the mushroom as a base starter");
            helper.succeed();
            return;
        }

        require(helper, reaction != null, "Totem mushroom base reaction did not load");
        require(helper, reaction.processingTicks() == 360,
                "Red Mushroom lost its 360-tick processing time");
        require(helper, Math.abs(reaction.successChance() - 0.8D) < EPSILON,
                "Red Mushroom lost its 80% base success chance");
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), water, mushroom),
                "Red Mushroom did not schedule the Totem-owned water base reaction");
        require(helper, water.reactions().size() == 1, "Missing pending mushroom base reaction");
        require(helper, water.reactions().iterator().next().requiredTicks() == 360,
                "Mushroom base reaction did not use its datapack processing duration");

        water.tickReactions(360);
        require(helper, water.reactions().isEmpty(), "Mushroom base reaction did not complete");
        require(helper, Math.abs(water.activatedBaseComposition().units(AWKWARD_BASE) - 3.0D) < EPSILON,
                "Mushroom base did not activate exactly three water units");
        require(helper, Math.abs(water.unactivatedUnits()) < EPSILON,
                "Mushroom base left unactivated full-cauldron capacity");
        require(helper, "minecraft:awkward".equals(water.canonicalPotionId()),
                "Fully activated mushroom base was not canonical Awkward");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void mushroomStarterRetainsFiniteCapacityAndUnstableStandMarker(GameTestHelper helper) {
        if (!AlchemyContentPackState.totemAlchemyEnabled()) {
            helper.succeed();
            return;
        }

        AlchemyMixtureState oneUnit = AlchemyMixtureBrewing.waterState(1);
        ItemStack mushroom = new ItemStack(Items.RED_MUSHROOM);
        var resolution = AlchemyReactionResolver.resolveBaseReaction(oneUnit, mushroom)
                .orElseThrow(() -> helper.assertionException("Missing Red Mushroom base reaction resolution"));
        require(helper, Math.abs(resolution.activationUnits() - 1.0D) < EPSILON,
                "Three-unit mushroom yield exceeded one-unit finite base capacity");

        ItemStack source = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        ItemStack result = AlchemyBrewing.mix(helper.getLevel(), mushroom, source);
        require(helper, result.is(Items.POTION)
                && result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD),
                "Totem mushroom Brewing Stand route did not preserve Awkward output");
        require(helper, VanillaBrewingChance.hasUnstableMushroomBase(result),
                "Mushroom base lost its established Brewing Stand instability marker");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
