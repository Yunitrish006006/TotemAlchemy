package dev.totem.alchemy.alchemy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Central success policy for reactions executed by a vanilla Brewing Stand.
 *
 * <p>Native Minecraft brewing recipes are deterministic and therefore always complete successfully.
 * Non-vanilla chemistry uses the reaction-backed base chance plus a station bonus. The current bonus is
 * intentionally zero until balance values are defined; keeping it explicit here prevents future station
 * tuning from leaking back into reaction data.</p>
 */
public final class BrewingStationPolicy {
    private static final double DEFAULT_STATION_BONUS = 0.0D;

    private BrewingStationPolicy() {}

    public static Decision evaluate(
            ServerLevel level,
            ItemStack ingredient,
            Iterable<ItemStack> potionInputs
    ) {
        List<ItemStack> inputs = copyInputs(potionInputs);
        boolean nativeVanillaRecipe =
                AlchemyBrewing.shouldGuaranteeVanillaSuccess(level, ingredient, inputs);
        double baseChance = VanillaBrewingChance.chanceFor(ingredient, inputs);
        double stationBonus = nativeVanillaRecipe ? 0.0D : DEFAULT_STATION_BONUS;
        double effectiveChance = nativeVanillaRecipe
                ? 1.0D
                : clamp(baseChance + stationBonus);
        return new Decision(nativeVanillaRecipe, baseChance, stationBonus, effectiveChance);
    }

    public static boolean succeeds(Decision decision, float randomRoll) {
        if (decision == null) {
            return false;
        }
        if (decision.nativeVanillaRecipe()) {
            return true;
        }
        return randomRoll >= 0.0F && randomRoll < decision.effectiveChance();
    }

    private static List<ItemStack> copyInputs(Iterable<ItemStack> potionInputs) {
        List<ItemStack> inputs = new ArrayList<>();
        if (potionInputs != null) {
            for (ItemStack input : potionInputs) {
                inputs.add(input);
            }
        }
        return inputs;
    }

    private static double clamp(double chance) {
        return Math.max(0.0D, Math.min(1.0D, chance));
    }

    public record Decision(
            boolean nativeVanillaRecipe,
            double baseChance,
            double stationBonus,
            double effectiveChance
    ) {
    }
}
