package dev.totem.alchemy.mixin;

import dev.totem.alchemy.alchemy.VanillaBrewingChance;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "addVanillaMixes", at = @At("TAIL"))
    private static void totemAlchemy$addNetherWartAlternative(
            PotionBrewing.Builder builder,
            CallbackInfo ci
    ) {
        builder.addMix(Potions.WATER, Items.RED_MUSHROOM, Potions.AWKWARD);
        builder.addMix(Potions.AWKWARD, Items.RED_MUSHROOM, Potions.POISON);
        builder.addMix(Potions.AWKWARD, Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS);
        builder.addMix(AlchemyPotions.SATURATION, Items.GLOWSTONE_DUST, AlchemyPotions.STRONG_SATURATION);
        builder.addMix(AlchemyPotions.RESISTANCE, Items.REDSTONE, AlchemyPotions.LONG_RESISTANCE);
        builder.addMix(AlchemyPotions.RESISTANCE, Items.GLOWSTONE_DUST, AlchemyPotions.STRONG_RESISTANCE);
        builder.addMix(Potions.SWIFTNESS, Items.CHERRY_LEAVES, AlchemyPotions.CHERRY_SWIFTNESS);
        builder.addMix(Potions.LONG_SWIFTNESS, Items.CHERRY_LEAVES, AlchemyPotions.LONG_CHERRY_SWIFTNESS);
        builder.addMix(Potions.STRONG_SWIFTNESS, Items.CHERRY_LEAVES, AlchemyPotions.STRONG_CHERRY_SWIFTNESS);
        builder.addMix(AlchemyPotions.CHERRY_SWIFTNESS, Items.REDSTONE, AlchemyPotions.LONG_CHERRY_SWIFTNESS);
        builder.addMix(AlchemyPotions.CHERRY_SWIFTNESS, Items.GLOWSTONE_DUST, AlchemyPotions.STRONG_CHERRY_SWIFTNESS);
        builder.addMix(Potions.STRENGTH, Items.FIREFLY_BUSH, AlchemyPotions.FIREFLY_STRENGTH);
        builder.addMix(Potions.LONG_STRENGTH, Items.FIREFLY_BUSH, AlchemyPotions.LONG_FIREFLY_STRENGTH);
        builder.addMix(Potions.STRONG_STRENGTH, Items.FIREFLY_BUSH, AlchemyPotions.STRONG_FIREFLY_STRENGTH);
        builder.addMix(AlchemyPotions.FIREFLY_STRENGTH, Items.REDSTONE, AlchemyPotions.LONG_FIREFLY_STRENGTH);
        builder.addMix(AlchemyPotions.FIREFLY_STRENGTH, Items.GLOWSTONE_DUST, AlchemyPotions.STRONG_FIREFLY_STRENGTH);
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void totemAlchemy$allowLayeredMixtureIngredients(
            ItemStack input,
            ItemStack ingredient,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (AlchemyMixtureBrewing.canApplyBrewingStandIngredient(input, ingredient)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mix", at = @At("RETURN"), cancellable = true)
    private void totemAlchemy$preserveLayeredMixture(
            ItemStack ingredient,
            ItemStack input,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack vanillaOutput = cir.getReturnValue();
        if (vanillaOutput.isEmpty()) {
            return;
        }

        ItemStack selectedOutput = MultiOutcomeBrewing.applyBatchOutcome(ingredient, input, vanillaOutput);
        boolean layeredRule = AlchemyMixtureBrewing.canApplyBrewingStandIngredient(input, ingredient);
        boolean vanillaChanged = !ItemStack.matches(input, selectedOutput);
        ItemStack output = selectedOutput;
        if (layeredRule || vanillaChanged) {
            output = AlchemyMixtureBrewing.applyBrewingStandOutcomes(
                    ingredient,
                    input,
                    selectedOutput,
                    MultiOutcomeBrewing.activeOutcomes()
            );
        }
        if (output.isEmpty()) {
            return;
        }

        if (ingredient.is(Items.RED_MUSHROOM)
                && input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)
                && output.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD)) {
            VanillaBrewingChance.markUnstableMushroomBase(output);
        } else {
            VanillaBrewingChance.carryUnstableMushroomBase(input, output);
        }
        cir.setReturnValue(output);
    }
}
