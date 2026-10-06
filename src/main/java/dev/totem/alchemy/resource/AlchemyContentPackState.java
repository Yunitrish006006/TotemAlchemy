package dev.totem.alchemy.resource;

import dev.totem.alchemy.TotemAlchemy;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class AlchemyContentPackState {
    private static final Identifier TOTEM_ALCHEMY_MARKER =
            Identifier.fromNamespaceAndPath("totem", "alchemy_pack_state/totem_alchemy.json");
    private static final Identifier MINECRAFT_ALCHEMY_MARKER =
            Identifier.fromNamespaceAndPath("totem", "alchemy_pack_state/minecraft_alchemy.json");

    private static volatile boolean totemAlchemyEnabled;
    private static volatile boolean minecraftAlchemyEnabled;
    private static volatile long revision;

    private AlchemyContentPackState() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("totem", "alchemy/content_pack_state");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        AlchemyContentPackState.reload(resourceManager);
                    }
                });
    }

    private static void reload(ResourceManager resourceManager) {
        boolean nextTotemAlchemy = resourceManager.getResource(TOTEM_ALCHEMY_MARKER).isPresent();
        boolean nextMinecraftAlchemy = resourceManager.getResource(MINECRAFT_ALCHEMY_MARKER).isPresent();

        boolean changed = nextTotemAlchemy != totemAlchemyEnabled
                || nextMinecraftAlchemy != minecraftAlchemyEnabled;

        totemAlchemyEnabled = nextTotemAlchemy;
        minecraftAlchemyEnabled = nextMinecraftAlchemy;
        revision++;

        if (changed) {
            TotemAlchemy.LOGGER.info(
                    "Alchemy content packs: Totem Alchemy={}, Minecraft Alchemy={}",
                    totemAlchemyEnabled,
                    minecraftAlchemyEnabled
            );
        }
    }

    public static boolean totemAlchemyEnabled() {
        return totemAlchemyEnabled;
    }

    public static boolean minecraftAlchemyEnabled() {
        return minecraftAlchemyEnabled;
    }

    public static long revision() {
        return revision;
    }

    public static Snapshot snapshot() {
        return new Snapshot(totemAlchemyEnabled, minecraftAlchemyEnabled, revision);
    }

    public record Snapshot(boolean totemAlchemyEnabled, boolean minecraftAlchemyEnabled, long revision) {
    }
}
