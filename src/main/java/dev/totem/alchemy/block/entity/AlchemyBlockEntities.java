package dev.totem.alchemy.block.entity;

import dev.totem.alchemy.TotemAlchemy;
import dev.totem.alchemy.block.AlchemyBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class AlchemyBlockEntities {
    public static final BlockEntityType<AlchemyCauldronBlockEntity> ALCHEMY_CAULDRON = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("totem", "alchemy_cauldron"),
            new BlockEntityType<>(AlchemyCauldronBlockEntity::new,
                    Set.of(AlchemyBlocks.ALCHEMY_CAULDRON, AlchemyBlocks.LEGACY_ALCHEMY_CAULDRON))
    );

    private AlchemyBlockEntities() {
    }

    public static void register() {
        TotemAlchemy.LOGGER.info("正在註冊模組方塊實體...");
    }
}
