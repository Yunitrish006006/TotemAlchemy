package dev.totem.alchemy.alchemy;

import dev.totem.alchemy.reaction.IngredientReaction;
import dev.totem.alchemy.reaction.ReactionIngredient;
import dev.totem.alchemy.reaction.ReactionOutcome;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class MultiOutcomeRegistryGameTest {
    @GameTest(maxTicks = 20)
    public void registryChanceOverridesLegacyOutcomeWeight(GameTestHelper helper) {
        IngredientReaction reaction = new IngredientReaction(
                id("totem", "reaction/test_sugar"),
                id("totem", "alchemy/awkward"),
                ReactionIngredient.item(id("minecraft", "sugar")),
                1.0D,
                1.0D,
                240,
                3,
                true,
                List.of(
                        new ReactionOutcome(id("minecraft", "swiftness"), 0.10D, 10),
                        new ReactionOutcome(id("minecraft", "slowness"), 0.80D, 0)
                )
        );

        List<MultiOutcomeBrewing.Outcome> selected =
                MultiOutcomeBrewing.chooseRegistryOutcomes(reaction, 0.20F, 0.20F);

        require(helper, selected.size() == 1, "Registry chance did not select exactly one expected outcome");
        require(helper,
                BuiltInRegistries.POTION.getKey(selected.getFirst().potion().value())
                        .equals(id("minecraft", "slowness")),
                "Registry outcome roll used legacy weights instead of reaction chance");
        require(helper,
                Math.abs(MultiOutcomeBrewing.registryOutcomeProbability(
                        reaction, "minecraft:swiftness") - 0.10D) < 0.000_001D,
                "Registry swiftness chance did not come from ReactionOutcome");
        require(helper,
                Math.abs(MultiOutcomeBrewing.registryOutcomeProbability(
                        reaction, "minecraft:slowness") - 0.80D) < 0.000_001D,
                "Registry slowness chance did not come from ReactionOutcome");
        helper.succeed();
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
