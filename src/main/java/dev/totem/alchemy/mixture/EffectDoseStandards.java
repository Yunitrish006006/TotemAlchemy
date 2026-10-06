package dev.totem.alchemy.mixture;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Map;

/**
 * Resolves the canonical EffectDose carried by one standard potion bottle.
 *
 * <p>The potion registry remains the source of truth for native duration/amplifier data. This lookup converts
 * that one-bottle presentation into conserved {@link AlchemyMixtureState.EffectDose} quantities without
 * introducing concentration rules. Variant potions therefore retain their registered amplifier while their
 * quantity is calculated through the canonical EffectDose contract.</p>
 */
public final class EffectDoseStandards {
    private EffectDoseStandards() {
    }

    public static Map<String, AlchemyMixtureState.EffectDose> forPotion(Holder<Potion> potion) {
        if (potion == null) {
            return Map.of();
        }
        return AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, potion)
        ).effects();
    }

    public static AlchemyMixtureState.EffectDose forEffect(Holder<Potion> potion, String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return null;
        }
        return forPotion(potion).get(effectId);
    }
}
