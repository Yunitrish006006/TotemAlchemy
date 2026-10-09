package dev.totem.alchemy.gametest;

import dev.totem.alchemy.resource.AlchemyContentPackState;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeType;

public final class AlchemyContentPackStateGameTest {
    @GameTest(maxTicks = 20)
    public void optionalAlchemyPacksAreDisabledByDefault(GameTestHelper helper) {
        AlchemyContentPackState.Snapshot snapshot = AlchemyContentPackState.snapshot();
        System.out.println("[M10-T07] selected server datapacks: "
                + helper.getLevel().getServer().getPackRepository().getSelectedIds());

        System.out.println("[M10-T07] optionalAlchemyPacksAreDisabledByDefault Totem="
                + snapshot.totemAlchemyEnabled() + " Minecraft=" + snapshot.minecraftAlchemyEnabled());
        require(helper, !snapshot.totemAlchemyEnabled(),
                "Totem Alchemy built-in datapack was unexpectedly enabled by default");
        require(helper, !snapshot.minecraftAlchemyEnabled(),
                "Minecraft Alchemy built-in datapack was unexpectedly enabled by default");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void disabledTotemPackDoesNotLeakFixedBrewingRecipes(GameTestHelper helper) {
        AlchemyContentPackState.Snapshot snapshot = AlchemyContentPackState.snapshot();
        System.out.println("[M10-T07] disabledTotemPackDoesNotLeakFixedBrewingRecipes Totem="
                + snapshot.totemAlchemyEnabled() + " Minecraft=" + snapshot.minecraftAlchemyEnabled());
        require(helper, !snapshot.totemAlchemyEnabled() && !snapshot.minecraftAlchemyEnabled(),
                "OFF/OFF fixed-recipe isolation fixture requires both optional packs disabled"
                        + " (Totem=" + snapshot.totemAlchemyEnabled()
                        + ", Minecraft=" + snapshot.minecraftAlchemyEnabled() + ")");

        int leaked = 0;
        String firstLeakedId = null;
        for (var holder : helper.getLevel().recipeAccess().getRecipes()) {
            if (holder.value().getType() != RecipeType.BREWING
                    || !holder.id().identifier().getNamespace().equals("totem")
                    || !holder.id().identifier().getPath().startsWith("brewing/")) {
                continue;
            }
            leaked++;
            if (firstLeakedId == null) {
                firstLeakedId = holder.id().identifier().toString();
            }
        }
        require(helper, leaked == 0,
                "Disabled Totem Alchemy leaked " + leaked
                        + " fixed Brewing recipes (first: " + firstLeakedId + ")");
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void disabledAlchemyPacksDoNotLeakReactionRegistry(GameTestHelper helper) {
        AlchemyContentPackState.Snapshot snapshot = AlchemyContentPackState.snapshot();
        System.out.println("[M10-T07] disabledAlchemyPacksDoNotLeakReactionRegistry Totem="
                + snapshot.totemAlchemyEnabled() + " Minecraft=" + snapshot.minecraftAlchemyEnabled());
        require(helper, !snapshot.totemAlchemyEnabled() && !snapshot.minecraftAlchemyEnabled(),
                "OFF/OFF reaction isolation fixture requires both optional packs disabled"
                        + " (Totem=" + snapshot.totemAlchemyEnabled()
                        + ", Minecraft=" + snapshot.minecraftAlchemyEnabled() + ")");

        int baseCount = AlchemyReactionDataLoader.baseReactions().size();
        int ingredientCount = AlchemyReactionDataLoader.ingredientReactions().size();
        require(helper, baseCount == 0 && ingredientCount == 0,
                "OFF/OFF leaked reaction definitions: base=" + baseCount
                        + ", ingredient=" + ingredientCount);
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
