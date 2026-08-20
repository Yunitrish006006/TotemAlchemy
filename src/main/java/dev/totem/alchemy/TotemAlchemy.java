package dev.totem.alchemy;

import dev.totem.alchemy.alchemy.AlchemyHandler;
import dev.totem.alchemy.alchemy.AlchemyPortableContainerInteractions;
import dev.totem.alchemy.alchemy.CherryBrewInteractions;
import dev.totem.alchemy.alchemy.FireflyStrengthInteractions;
import dev.totem.alchemy.alchemy.PigManureInteractions;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.alchemy.BrewingOutcomeWeights;
import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.block.entity.AlchemyBlockEntities;
import dev.totem.alchemy.effect.AlchemyMobEffects;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.manual.AlchemyManual;
import dev.totem.alchemy.recipe.AlchemyRecipes;
import dev.totem.alchemy.registry.AlchemyCriteria;
import dev.totem.alchemy.registry.AlchemyItemGroups;
import dev.totem.alchemy.registry.AlchemyItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The sole registration owner for Alchemy gameplay outside the compatibility bundle. */
public final class TotemAlchemy implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("TotemAlchemy");

    @Override
    public void onInitialize() {
        AlchemyBlocks.register();
        AlchemyBlockEntities.register();
        AlchemyMobEffects.register();
        AlchemyPotions.register();
        AlchemyCriteria.register();
        AlchemyItems.register();
        AlchemyRecipes.register();
        AlchemyItemGroups.register();
        BrewingMaterialSettings.register();
        BrewingOutcomeWeights.register();
        AlchemyHandler.register();
        AlchemyPortableContainerInteractions.register();
        AlchemyDiscoveryService.register();
        AlchemyManual.register();
        CherryBrewInteractions.register();
        FireflyStrengthInteractions.register();
        PigManureInteractions.register();
        LOGGER.info("TotemAlchemy initialized without DeadRecall implementation dependency");
    }
}
