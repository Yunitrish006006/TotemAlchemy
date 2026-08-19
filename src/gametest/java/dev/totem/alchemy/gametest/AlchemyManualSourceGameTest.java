package dev.totem.alchemy.gametest;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.manual.AlchemyManual;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class AlchemyManualSourceGameTest {
    @GameTest(maxTicks = 20)
    public void brewingStandAndCauldronsAreManualSources(GameTestHelper helper) {
        require(helper, AlchemyManual.isManualSource(Blocks.BREWING_STAND.defaultBlockState()),
                "Brewing stand stopped granting the Alchemy manual");
        require(helper, AlchemyManual.isManualSource(Blocks.CAULDRON.defaultBlockState()),
                "Empty cauldron does not grant the Alchemy manual");
        require(helper, AlchemyManual.isManualSource(Blocks.WATER_CAULDRON.defaultBlockState()),
                "Water cauldron does not grant the Alchemy manual");
        require(helper, AlchemyManual.isManualSource(AlchemyBlocks.ALCHEMY_CAULDRON.defaultBlockState()),
                "Converted Alchemy cauldron does not grant the Alchemy manual");
        require(helper, !AlchemyManual.isManualSource(Blocks.CHEST.defaultBlockState()),
                "Unrelated blocks incorrectly grant the Alchemy manual");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
