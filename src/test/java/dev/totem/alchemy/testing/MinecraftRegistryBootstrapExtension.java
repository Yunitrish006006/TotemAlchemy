package dev.totem.alchemy.testing;

import dev.totem.alchemy.registry.AlchemyItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.LinkedHashMap;

/**
 * Fabric Loader JUnit initializes the mod class loader but does NOT perform
 * the first server data-pack reload. Since Minecraft 26.x, ItemStack uses
 * Holder.Reference.components(), which remains unbound after Bootstrap alone.
 *
 * <p>Build and apply Minecraft's real deferred DataComponentInitializers
 * against vanilla static, world and reloadable registry lookups once per
 * JUnit worker. This is test-only and must not modify production bootstrap.</p>
 */
public final class MinecraftRegistryBootstrapExtension implements BeforeAllCallback {
    private static boolean bootstrapped;

    /** Diagnostic handshake proving Jupiter discovered this global callback. */
    static boolean hasBootstrappedForTests() {
        return bootstrapped;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        synchronized (MinecraftRegistryBootstrapExtension.class) {
            if (!bootstrapped) {
                SharedConstants.tryDetectVersion();
                Bootstrap.bootStrap();

                // The first annotated test might only use vanilla containers.
                // Force Totem's static item registration *before* building the
                // deferred component prototypes. Otherwise the first test
                // binds vanilla holders, the extension marks itself complete,
                // and a later flask/hot-cocoa test registers unbound holders.
                AlchemyItems.register();

                RegistryAccess.Frozen builtins =
                        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                HolderLookup.Provider world = VanillaRegistries.createWorldLookup();
                HolderLookup.Provider base = combine(builtins, world);
                HolderLookup.Provider reloadable = VanillaRegistries.createReloadableLookup(base);
                HolderLookup.Provider complete = combine(base, reloadable);

                BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(complete).forEach(
                        DataComponentInitializers.PendingComponents::apply);
                bootstrapped = true;
            }
        }
    }

    private static HolderLookup.Provider combine(HolderLookup.Provider... providers) {
        var registries = new LinkedHashMap<Object, HolderLookup.RegistryLookup<?>>();
        for (HolderLookup.Provider provider : providers) {
            provider.listRegistries().forEach(lookup ->
                    registries.putIfAbsent(lookup.key(), lookup));
        }
        return HolderLookup.Provider.create(registries.values().stream());
    }
}
