package dev.totem.alchemy.mixture;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts a redeemed one-unit signature claim into its registered drink item.
 *
 * <p>This class only builds a detached stack. It does not consume a glass bottle,
 * mutate the source cauldron or grant the stack to a player.</p>
 */
public final class SignatureBrewBottleOutput {
    private SignatureBrewBottleOutput() {
    }

    /**
     * Restrict v1 to registered drinkable containers: placing mixture data on
     * an arbitrary item could create an unusable output or a replayable claim.
     */
    public static boolean supports(ItemStack inputContainer, SignatureBrewDefinition.Result result) {
        if (inputContainer == null || inputContainer.isEmpty() || result == null
                || result.type() != SignatureBrewDefinition.Type.BOTTLED_ITEM
                || !BuiltInRegistries.ITEM.containsKey(result.containerItemId())
                || !BuiltInRegistries.ITEM.containsKey(result.itemId())) {
            return false;
        }
        Item requiredContainer = BuiltInRegistries.ITEM.getValue(result.containerItemId());
        Item resultItem = BuiltInRegistries.ITEM.getValue(result.itemId());
        if (requiredContainer == null || requiredContainer == Items.AIR
                || resultItem == null || resultItem == Items.AIR
                || !inputContainer.is(requiredContainer)
                || !AlchemyMixtureBottle.isDrinkablePotion(new ItemStack(resultItem))) {
            return false;
        }
        return result.potionId() == null
                || AlchemyMixtureBottle.potionHolder(result.potionId().toString()) != null;
    }

    /** Returns an empty stack on unsupported or unresolved recipe outputs. */
    public static ItemStack create(AlchemyMixtureState.SignatureBottleClaim claim) {
        if (claim == null) {
            return ItemStack.EMPTY;
        }
        SignatureBrewDefinition.Result result = claim.result();
        if (result.type() != SignatureBrewDefinition.Type.BOTTLED_ITEM
                || !BuiltInRegistries.ITEM.containsKey(result.itemId())) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(result.itemId());
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack output = new ItemStack(item);
        if (!AlchemyMixtureBottle.isDrinkablePotion(output)) {
            return ItemStack.EMPTY;
        }
        AlchemyMixtureState stored = claim.mixture().copy();
        Identifier potionId = result.potionId();
        if (potionId != null) {
            Holder<Potion> potion = AlchemyMixtureBottle.potionHolder(potionId.toString());
            if (potion == null) {
                return ItemStack.EMPTY;
            }
            // A signature potion is its defined result, not another ordinary
            // material reaction. Overlay its effects once on the one-unit dose,
            // keeping any independent nonconflicting chemistry from the batch.
            AlchemyMixtureState canonical = AlchemyMixtureBottle.fromPotion(
                    PotionContents.createItemStack(Items.POTION, potion));
            Map<String, AlchemyMixtureState.EffectDose> effects = new LinkedHashMap<>(stored.effects());
            effects.putAll(canonical.effects());
            stored.replaceEffects(effects);
        }
        AlchemyMixtureBottle.writeState(output, stored);
        return output;
    }
}
