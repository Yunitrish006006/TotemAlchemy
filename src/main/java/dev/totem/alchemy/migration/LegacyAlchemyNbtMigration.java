package dev.totem.alchemy.migration;

import dev.totem.core.api.v1.migration.LegacyNbtMigrationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Raw-NBT decode migration for Alchemy's pre-split registry identifiers.
 *
 * <p>Core invokes this before registry codecs decode chunk palettes,
 * block-entity IDs, player inventories, potion contents, and detached entity
 * effects. It has no aliases and no runtime registrations under
 * {@code deadrecall}; after the containing object is next saved only the
 * canonical {@code totem:alchemy/*} identifiers remain.</p>
 */
public final class LegacyAlchemyNbtMigration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private LegacyAlchemyNbtMigration() {
    }

    /** Registers the single, idempotent Alchemy migration with TotemCore. */
    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            LegacyNbtMigrationRegistry.register(LegacyAlchemyNbtMigration::migrate);
        }
    }

    /**
     * Rewrites only allow-listed Alchemy IDs in an arbitrary persisted NBT
     * tree. This covers registry fields represented as a direct string and
     * Alchemy's older compound provenance strings without touching unrelated
     * {@code deadrecall:*} values.
     */
    public static boolean migrate(CompoundTag root) {
        return root != null && migrateTag(root);
    }

    private static boolean migrateTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            boolean changed = false;
            for (String key : List.copyOf(compound.keySet())) {
                Tag child = compound.get(key);
                if (child instanceof StringTag stringTag) {
                    String original = stringTag.value();
                    String canonical = LegacyAlchemyIds.canonicalizeEmbedded(original);
                    if (!original.equals(canonical)) {
                        compound.putString(key, canonical);
                        changed = true;
                    }
                } else if (child != null) {
                    changed |= migrateTag(child);
                }
            }
            return changed;
        }
        if (tag instanceof ListTag list) {
            boolean changed = false;
            for (int index = 0; index < list.size(); index++) {
                Tag child = list.get(index);
                if (child instanceof StringTag stringTag) {
                    String original = stringTag.value();
                    String canonical = LegacyAlchemyIds.canonicalizeEmbedded(original);
                    if (!original.equals(canonical)) {
                        list.set(index, StringTag.valueOf(canonical));
                        changed = true;
                    }
                } else {
                    changed |= migrateTag(child);
                }
            }
            return changed;
        }
        return false;
    }
}
