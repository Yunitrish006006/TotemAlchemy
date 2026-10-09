package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import dev.totem.alchemy.reaction.BaseReaction;
import dev.totem.alchemy.resource.AlchemyContentPackState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MinecraftBaseReactionMigrationGameTest {
    private static final Identifier REACTION_ID =
            Identifier.fromNamespaceAndPath("minecraft_alchemy", "vanilla/nether_wart");
    private static final Identifier AWKWARD_BASE =
            Identifier.fromNamespaceAndPath("totem", "alchemy/awkward");

    @GameTest(maxTicks = 40)
    public void minecraftNetherWartBaseReactionFollowsPackOwnership(GameTestHelper helper) {
        BaseReaction reaction = AlchemyReactionDataLoader.baseReactions().stream()
                .filter(candidate -> REACTION_ID.equals(candidate.id()))
                .findFirst()
                .orElse(null);

        require(helper, !BrewingMaterialSettings.isStarter(Items.NETHER_WART),
                "Nether Wart still leaked starter authority from legacy material settings");

        if (!AlchemyContentPackState.minecraftAlchemyEnabled()) {
            require(helper, reaction == null,
                    "Minecraft base reaction loaded while Minecraft Alchemy pack was disabled");
            helper.succeed();
            return;
        }

        require(helper, reaction != null,
                "Minecraft Alchemy pack did not load the Nether Wart base reaction");
        require(helper, reaction.processingTicks() == 400,
                "Nether Wart base reaction lost its 400-tick processing time");
        require(helper, Math.abs(reaction.activationYield() - 3.0D) < 0.000_001D,
                "Nether Wart base reaction did not retain full-cauldron activation yield");

        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(3);
        require(helper, AlchemyMixtureBrewing.schedule(
                        helper.getLevel(),
                        state,
                        new ItemStack(Items.NETHER_WART)
                ),
                "Loaded Minecraft base reaction could not be scheduled in the Alchemy Cauldron");

        AlchemyMixtureState.Reaction pending = state.reactions().stream().findFirst()
                .orElseThrow(() -> helper.assertionException("Nether Wart base reaction did not create pending state"));
        require(helper, pending.requiredTicks() == 400,
                "Pending Nether Wart reaction did not use reaction-data processing time");

        state.tickReactions(400);

        require(helper, state.reactions().isEmpty(),
                "Nether Wart base reaction remained pending after its configured duration");
        require(helper, Math.abs(state.activatedBaseComposition().units(AWKWARD_BASE) - 3.0D) < 0.000_001D,
                "Nether Wart did not transform all three unactivated Water units into Awkward base");
        require(helper, Math.abs(state.unactivatedUnits()) < 0.000_001D,
                "Nether Wart base reaction left phantom unactivated capacity");
        require(helper, "minecraft:awkward".equals(state.canonicalPotionId()),
                "Fully activated Nether Wart mixture did not retain canonical Awkward potion identity");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
