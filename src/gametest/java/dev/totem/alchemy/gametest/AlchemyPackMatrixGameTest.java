package dev.totem.alchemy.gametest;

import dev.totem.alchemy.resource.AlchemyContentPackState;
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

    private static void check(GameTestHelper helper, boolean valid, String message) {
        if (!valid) throw helper.assertionException(message);
    }
}
