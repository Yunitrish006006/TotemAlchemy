package dev.totem.alchemy.gametest;

import dev.totem.alchemy.resource.AlchemyContentPackState;
import dev.totem.alchemy.reaction.AlchemyReactionDataLoader;
import net.minecraft.world.item.crafting.RecipeType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AlchemyPackMatrixGameTest {
    @GameTest(maxTicks = 20)
    public void selectedPacksMatchExpectedMatrixState(GameTestHelper helper) {
        String fixture = System.getenv("TOTEM_ALCHEMY_GAMETEST_PACKS");
        if (fixture == null || !java.util.Set.of("ON_ON", "ON_OFF", "OFF_ON", "OFF_OFF").contains(fixture)) {
            throw helper.assertionException("M10-T08 requires a valid TOTEM_ALCHEMY_GAMETEST_PACKS fixture");
        }
        boolean expectedTotem = fixture.startsWith("ON_");
        boolean expectedMinecraft = fixture.endsWith("_ON");
        var selected = helper.getLevel().getServer().getPackRepository().getSelectedIds();
        var state = AlchemyContentPackState.snapshot();
        check(helper, selected.contains("totem-alchemy:totem_alchemy") == expectedTotem,
                "Totem selected-pack mismatch: " + selected);
        check(helper, selected.contains("totem-alchemy:minecraft_alchemy") == expectedMinecraft,
                "Minecraft selected-pack mismatch: " + selected);
        check(helper, state.totemAlchemyEnabled() == expectedTotem,
                "Totem pack state marker mismatch in " + fixture);
        check(helper, state.minecraftAlchemyEnabled() == expectedMinecraft,
                "Minecraft pack state marker mismatch in " + fixture);
        System.out.println("[M10-T08] PASS " + fixture + " selected=" + selected);
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void optionalContentFollowsSelectedPacks(GameTestHelper helper) {
        String fixture = System.getenv("TOTEM_ALCHEMY_GAMETEST_PACKS");
        check(helper, fixture != null && java.util.Set.of(
                        "ON_ON", "ON_OFF", "OFF_ON", "OFF_OFF").contains(fixture),
                "Missing M10-T08 matrix fixture");

        boolean expectedTotem = fixture.startsWith("ON_");
        boolean expectedMinecraft = fixture.endsWith("_ON");

        long totemBrewingRecipes = 0;
        for (var holder : helper.getLevel().recipeAccess().getRecipes()) {
            if (holder.value().getType() == RecipeType.BREWING
                    && holder.id().identifier().getNamespace().equals("totem")
                    && holder.id().identifier().getPath().startsWith("brewing/")) {
                totemBrewingRecipes++;
            }
        }
        check(helper, (totemBrewingRecipes > 0) == expectedTotem,
                fixture + " fixed brewing recipe isolation failed; count=" + totemBrewingRecipes);

        long minecraftBases = AlchemyReactionDataLoader.baseReactions().stream()
                .filter(reaction -> reaction.id().getNamespace().equals("minecraft_alchemy"))
                .count();
        long minecraftIngredients = AlchemyReactionDataLoader.ingredientReactions().stream()
                .filter(reaction -> reaction.id().getNamespace().equals("minecraft_alchemy"))
                .count();
        check(helper, (minecraftBases > 0 && minecraftIngredients > 0) == expectedMinecraft,
                fixture + " Minecraft reaction isolation failed; base=" + minecraftBases
                        + " ingredient=" + minecraftIngredients);
        check(helper, !expectedMinecraft || minecraftBases > 0,
                fixture + " expected Minecraft base reactions");
        System.out.println("[M10-T08] content " + fixture + " brewing=" + totemBrewingRecipes
                + " minecraftBases=" + minecraftBases + " minecraftIngredients=" + minecraftIngredients);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean valid, String message) {
        if (!valid) throw helper.assertionException(message);
    }
}
