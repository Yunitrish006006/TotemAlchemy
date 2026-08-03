package dev.totem.alchemy.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Captures the standalone Alchemy Creative tab used by the Modrinth gallery. */
@SuppressWarnings("UnstableApiUsage")
public final class AlchemyCreativeTabVisualGameTest implements FabricClientGameTest {
    private static Object creativeScreen;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode creative @a");
            context.waitFor(AlchemyCreativeTabVisualGameTest::hasCreativeAbilities);
            context.runOnClient(AlchemyCreativeTabVisualGameTest::openCreativeScreen);
            context.waitTicks(20);
            context.runOnClient(AlchemyCreativeTabVisualGameTest::selectAlchemyCreativeTab);
            context.waitTicks(2);
            context.takeScreenshot("totem-alchemy-creative-showcase");
            context.runOnClient(AlchemyCreativeTabVisualGameTest::closeScreen);
        }
    }

    private static void openCreativeScreen(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            Object level = client.getClass().getField("level").get(client);
            Object enabledFeatures = invoke(level, "enabledFeatures");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
            Class<?> playerClass = Class.forName("net.minecraft.client.player.LocalPlayer");
            Class<?> featureFlagsClass = Class.forName("net.minecraft.world.flag.FeatureFlagSet");
            creativeScreen = screenClass
                    .getConstructor(playerClass, featureFlagsClass, boolean.class)
                    .newInstance(player, enabledFeatures, true);
            invoke(client, "setScreenAndShow", creativeScreen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open the Creative inventory", exception);
        }
    }

    private static void selectAlchemyCreativeTab(Object client) {
        try {
            if (creativeScreen == null) {
                throw new IllegalStateException("Creative inventory was not opened");
            }
            Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
            Object id = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, "deadrecall", "main");
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
                    .getField("CREATIVE_MODE_TAB")
                    .get(null);
            Object result = registry.getClass().getMethod("get", identifierClass).invoke(registry, id);
            Object tabHolder = ((java.util.Optional<?>) result)
                    .orElseThrow(() -> new IllegalStateException("Missing standalone Alchemy Creative tab"));
            Object tab = invoke(tabHolder, "value");
            Class<?> tabClass = Class.forName("net.minecraft.world.item.CreativeModeTab");
            Class<?> screenExtension = Class.forName(
                    "net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen");
            boolean selected = (Boolean) screenExtension
                    .getMethod("setSelectedTab", tabClass)
                    .invoke(creativeScreen, tab);
            if (!selected) {
                throw new IllegalStateException("Could not switch to the Alchemy Creative tab");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not select the Alchemy Creative tab", exception);
        }
    }

    private static boolean hasCreativeAbilities(Object client) {
        try {
            Object player = client.getClass().getField("player").get(client);
            if (player == null) {
                return false;
            }
            Object abilities = invoke(player, "getAbilities");
            return abilities.getClass().getField("instabuild").getBoolean(abilities);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not verify the Creative-mode transition", exception);
        }
    }

    private static void closeScreen(Object client) {
        try {
            invoke(client, "setScreenAndShow", new Object[]{null});
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not close the Creative inventory", exception);
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (var method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }
}
