package dev.totem.alchemy.mixin;

import dev.totem.alchemy.alchemy.BrewingMaterialSettings;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class BrewingRecipeManagerMixin {
    @Unique private RecipePropertySet totemAlchemy$source;
    @Unique private RecipePropertySet totemAlchemy$reagents;
    @Unique private long totemAlchemy$revision = -1;

    @Inject(method = "propertySet", at = @At("RETURN"), cancellable = true)
    private void totemAlchemy$acceptCustomReagents(ResourceKey<RecipePropertySet> key,
                                                  CallbackInfoReturnable<RecipePropertySet> cir) {
        if (key.equals(RecipePropertySet.BREWING_REAGENTS)) {
            cir.setReturnValue(totemAlchemy$reagents(cir.getReturnValue()));
        }
    }

    @Inject(method = "getSynchronizedItemProperties", at = @At("RETURN"), cancellable = true)
    private void totemAlchemy$syncCustomReagents(
            CallbackInfoReturnable<Map<ResourceKey<RecipePropertySet>, RecipePropertySet>> cir) {
        Map<ResourceKey<RecipePropertySet>, RecipePropertySet> properties = new HashMap<>(cir.getReturnValue());
        properties.put(RecipePropertySet.BREWING_REAGENTS, totemAlchemy$reagents(
                properties.getOrDefault(RecipePropertySet.BREWING_REAGENTS, RecipePropertySet.EMPTY)));
        cir.setReturnValue(Map.copyOf(properties));
    }

    @Unique
    private RecipePropertySet totemAlchemy$reagents(RecipePropertySet original) {
        long revision = BrewingMaterialSettings.revision();
        if (totemAlchemy$source != original || totemAlchemy$revision != revision) {
            var ingredients = new ArrayList<Ingredient>();
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (original.test(stack) || BrewingMaterialSettings.isStarter(item)
                        || MultiOutcomeBrewing.isOutcomeIngredient(stack)) {
                    ingredients.add(Ingredient.of(item));
                }
            }
            totemAlchemy$reagents = RecipePropertySet.create(ingredients);
            totemAlchemy$source = original;
            totemAlchemy$revision = revision;
        }
        return totemAlchemy$reagents;
    }
}
