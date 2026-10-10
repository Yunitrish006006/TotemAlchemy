package dev.totem.alchemy.testing;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Initializes vanilla registries before any JUnit class accesses ItemStack,
 * Items, Potions, RandomSource or registry-dependent mod items.
 *
 * <p>Fabric Loader JUnit supplies the mod class-loading environment; this
 * callback supplies the separate vanilla Bootstrap that Fabric's official
 * unit-testing documentation also requires. It is automatically discovered
 * via META-INF/services for all test classes, eliminating ordering-dependent
 * failures when the container adapters run before a bootstrapped test.</p>
 */
public final class MinecraftRegistryBootstrapExtension implements BeforeAllCallback {
    private static boolean bootstrapped;

    @Override
    public void beforeAll(ExtensionContext context) {
        synchronized (MinecraftRegistryBootstrapExtension.class) {
            if (!bootstrapped) {
                SharedConstants.tryDetectVersion();
                Bootstrap.bootStrap();
                bootstrapped = true;
            }
        }
    }
}
