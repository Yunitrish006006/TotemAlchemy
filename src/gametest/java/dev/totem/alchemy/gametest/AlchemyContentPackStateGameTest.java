package dev.totem.alchemy.gametest;

import dev.totem.alchemy.resource.AlchemyContentPackState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AlchemyContentPackStateGameTest {
    @GameTest(maxTicks = 20)
    public void optionalAlchemyPacksAreDisabledByDefault(GameTestHelper helper) {
        AlchemyContentPackState.Snapshot snapshot = AlchemyContentPackState.snapshot();
        require(helper, !snapshot.totemAlchemyEnabled(),
                "Totem Alchemy built-in datapack was unexpectedly enabled by default");
        require(helper, !snapshot.minecraftAlchemyEnabled(),
                "Minecraft Alchemy built-in datapack was unexpectedly enabled by default");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
