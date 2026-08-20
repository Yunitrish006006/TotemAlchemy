package dev.totem.alchemy.mixture;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Computes the visible liquid color from the actual effect doses in a mixture. */
public final class AlchemyMixtureColor {
    public static final int WATER_RGB = 0x3F76E4;

    private AlchemyMixtureColor() {
    }

    public static int rgb(AlchemyMixtureState state) {
        if (state == null || state.isEmpty()) {
            return WATER_RGB;
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        state.effects().forEach((id, dose) -> weights.merge(id, dose.potencyTicks(), Double::sum));

        for (AlchemyMixtureState.Reaction reaction : state.reactions()) {
            double progress = reaction.progress();
            reaction.sourceEffects().forEach((id, dose) -> weights.compute(id, (key, old) ->
                    Math.max(0.0D, (old == null ? 0.0D : old) - dose.potencyTicks() * progress)));
            reaction.targetEffects().forEach((id, dose) -> weights.merge(
                    id,
                    dose.potencyTicks() * progress,
                    Double::sum
            ));
        }

        double total = 0.0D;
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double weight = Math.max(0.0D, entry.getValue());
            if (weight <= 0.0001D) {
                continue;
            }
            Holder<MobEffect> effect = effectHolder(entry.getKey());
            if (effect == null) {
                continue;
            }
            int color = effect.value().getColor();
            red += ((color >> 16) & 0xFF) * weight;
            green += ((color >> 8) & 0xFF) * weight;
            blue += (color & 0xFF) * weight;
            total += weight;
        }

        if (total <= 0.0001D) {
            return WATER_RGB;
        }
        int r = clampChannel((int) Math.round(red / total));
        int g = clampChannel((int) Math.round(green / total));
        int b = clampChannel((int) Math.round(blue / total));
        return (r << 16) | (g << 8) | b;
    }

    private static Holder<MobEffect> effectHolder(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(identifier);
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
