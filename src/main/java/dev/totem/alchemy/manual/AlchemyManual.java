package dev.totem.alchemy.manual;

import dev.totem.alchemy.block.AlchemyBlocks;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.core.api.v1.manual.TotemManualLifecycle;
import dev.totem.core.api.v1.manual.TotemManualPlayerHelper;
import dev.totem.core.api.v1.manual.TotemManualRegistry;
import dev.totem.core.api.v1.manual.TotemManualSection;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Alchemy chapter in the shared Totem manual and its brewing-source acquisition hooks. */
public final class AlchemyManual {
    public static final int SECTION_ORDER = 0;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Identifier MANUAL_ADVANCEMENT = Identifier.fromNamespaceAndPath("deadrecall", "alchemy_manual");
    private static final List<String> PAGE_KEYS = List.of(
            "book.deadrecall.alchemy_manual.page.1",
            "book.deadrecall.alchemy_manual.page.2",
            "book.totem_alchemy.material.nether_wart",
            "book.totem_alchemy.material.red_mushroom",
            "book.totem_alchemy.material.spider_eye",
            "book.totem_alchemy.material.fermented_spider_eye",
            "book.totem_alchemy.material.sugar",
            "book.totem_alchemy.material.rabbit_foot",
            "book.totem_alchemy.material.magma_cream",
            "book.totem_alchemy.material.glistering_melon_slice",
            "book.totem_alchemy.material.golden_carrot",
            "book.totem_alchemy.material.blaze_powder",
            "book.totem_alchemy.material.ghast_tear",
            "book.totem_alchemy.material.pufferfish",
            "book.totem_alchemy.material.turtle_helmet",
            "book.totem_alchemy.material.phantom_membrane",
            "book.totem_alchemy.material.breeze_rod",
            "book.totem_alchemy.material.slime_block",
            "book.totem_alchemy.material.stone",
            "book.totem_alchemy.material.cobweb",
            "book.totem_alchemy.material.cherry_leaves",
            "book.totem_alchemy.material.firefly_bush",
            "book.totem_alchemy.material.redstone",
            "book.totem_alchemy.material.glowstone_dust",
            "book.totem_alchemy.material.gunpowder",
            "book.totem_alchemy.material.dragon_breath",
            "book.deadrecall.alchemy_manual.page.8"
    );
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.fromNamespaceAndPath("totem", "alchemy/manual"), SECTION_ORDER,
            "book.deadrecall.alchemy_manual.title", PAGE_KEYS);

    private AlchemyManual() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        TotemManualRegistry.global().register(SECTION);
        TotemManualLifecycle.registerLoginRefresh();
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator() || !isManualSource(world.getBlockState(hitResult.getBlockPos()))) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!isManualRequest(stack)) {
                return InteractionResult.PASS;
            }
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return grant((ServerPlayer) player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }

    public static boolean isManualSource(BlockState state) {
        return state.is(Blocks.BREWING_STAND) || state.is(Blocks.CAULDRON)
                || state.is(Blocks.WATER_CAULDRON) || state.is(AlchemyBlocks.ALCHEMY_CAULDRON);
    }

    public static boolean isManualRequest(ItemStack stack) {
        return TotemManualPlayerHelper.supportsSourceInteraction(stack, ignored -> false);
    }

    public static boolean grant(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        boolean handled = TotemManualPlayerHelper.acquireSections(
                player, hand, List.of(SECTION), MANUAL_ADVANCEMENT, ignored -> false).handled();
        if (handled) {
            AlchemyDiscoveryService.send(player);
        }
        return handled;
    }

    public static List<String> pageKeys() {
        return PAGE_KEYS;
    }
}
