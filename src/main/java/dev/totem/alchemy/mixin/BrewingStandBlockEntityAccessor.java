package dev.totem.alchemy.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses vanilla brew progress so research can record actual active processing time. */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
    @Accessor("items")
    net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> totemAlchemy$getItems();

    @Accessor("brewTime")
    int totemAlchemy$getBrewTime();
}
