package dev.totem.alchemy.client;

import dev.totem.alchemy.client.manual.AlchemyDiscoveryClientCache;
import dev.totem.alchemy.client.manual.AlchemyManualPageOverlay;
import dev.totem.alchemy.client.manual.AlchemyMixtureManualOverlay;
import dev.totem.alchemy.client.mixture.AlchemyMixtureTooltip;
import net.fabricmc.api.ClientModInitializer;

public final class TotemAlchemyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AlchemyDiscoveryClientCache.register();
        AlchemyManualPageOverlay.register();
        AlchemyMixtureManualOverlay.register();
        AlchemyMixtureTooltip.register();
    }
}
