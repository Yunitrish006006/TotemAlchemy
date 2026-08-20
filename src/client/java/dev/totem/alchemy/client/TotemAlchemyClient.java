package dev.totem.alchemy.client;

import dev.totem.alchemy.client.manual.AlchemyDiscoveryClientCache;
import dev.totem.alchemy.client.manual.AlchemyManualPageOverlay;
import dev.totem.alchemy.client.manual.AlchemyMaterialResearchOverlay;
import dev.totem.alchemy.client.manual.AlchemyMixtureManualOverlay;
import dev.totem.alchemy.client.manual.AlchemyResearchClientCache;
import dev.totem.alchemy.client.mixture.AlchemyCauldronColorProvider;
import dev.totem.alchemy.client.mixture.AlchemyCauldronHud;
import dev.totem.alchemy.client.mixture.AlchemyMixtureTooltip;
import net.fabricmc.api.ClientModInitializer;

public final class TotemAlchemyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AlchemyDiscoveryClientCache.register();
        AlchemyResearchClientCache.register();
        AlchemyManualPageOverlay.register();
        AlchemyMaterialResearchOverlay.register();
        AlchemyMixtureManualOverlay.register();
        AlchemyMixtureTooltip.register();
        AlchemyCauldronHud.register();
        AlchemyCauldronColorProvider.register();
    }
}
