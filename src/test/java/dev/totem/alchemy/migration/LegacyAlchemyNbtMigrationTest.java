package dev.totem.alchemy.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyAlchemyNbtMigrationTest {
    @Test
    void rewritesLegacyRegistryValuesBeforeTheyCanBeDecoded() {
        CompoundTag root = new CompoundTag();
        root.putString("Name", "deadrecall:alchemy_cauldron");

        CompoundTag blockEntity = new CompoundTag();
        blockEntity.putString("id", "deadrecall:alchemy_cauldron");
        root.put("block_entity", blockEntity);

        CompoundTag potionContents = new CompoundTag();
        potionContents.putString("potion", "deadrecall:long_firefly_strength");
        ListTag effects = new ListTag();
        effects.add(StringTag.valueOf("deadrecall:firefly_strength"));
        potionContents.put("custom_effects", effects);
        root.put("potion_contents", potionContents);

        root.putString("provenance", "deadrecall:cherry_bloom>deadrecall:firefly_strength");
        root.putString("other_feature", "deadrecall:copper_wrench");

        assertTrue(LegacyAlchemyNbtMigration.migrate(root));
        assertEquals("totem:alchemy/alchemy_cauldron", root.getStringOr("Name", ""));
        assertEquals("totem:alchemy/alchemy_cauldron", blockEntity.getStringOr("id", ""));
        assertEquals("totem:alchemy/long_firefly_strength", potionContents.getStringOr("potion", ""));
        assertEquals("totem:alchemy/firefly_strength", effects.getStringOr(0, ""));
        assertEquals("totem:alchemy/cherry_bloom>totem:alchemy/firefly_strength",
                root.getStringOr("provenance", ""));
        assertEquals("deadrecall:copper_wrench", root.getStringOr("other_feature", ""));
        assertFalse(LegacyAlchemyNbtMigration.migrate(root));
    }
}
