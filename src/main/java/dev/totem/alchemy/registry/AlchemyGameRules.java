package dev.totem.alchemy.registry;

import dev.totem.core.api.v1.gamerule.TotemGameRuleCategories;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;

/** Persistent per-world switches for Alchemy discovery behavior. */
public final class AlchemyGameRules {
    public static final GameRule<Boolean> AUTO_RECORD_BREWING_MATERIALS =
            GameRuleBuilder.forBoolean(true)
                    .category(TotemGameRuleCategories.TOTEM)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(
                            "totem", "alchemy_auto_record_brewing_materials"));

    private AlchemyGameRules() {
    }

    public static void register() {
        // Class initialization registers the rule.
    }

    public static boolean autoRecordBrewingMaterials(ServerLevel level) {
        return level.getGameRules().get(AUTO_RECORD_BREWING_MATERIALS);
    }
}
