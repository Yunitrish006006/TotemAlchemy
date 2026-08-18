package dev.totem.alchemy.manual;

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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/** Alchemy chapter in the shared Totem manual and its brewing-stand acquisition source. */
public final class AlchemyManual {
    /** Reserved first module position in the shared Totem manual. */
    public static final int SECTION_ORDER = 0;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Identifier MANUAL_ADVANCEMENT =
            Identifier.fromNamespaceAndPath("deadrecall", "alchemy_manual");
    private static final List<String> PAGE_KEYS = IntStream.rangeClosed(1, 14)
            .mapToObj(page -> "book.deadrecall.alchemy_manual.page." + page)
            .toList();
    private static final TotemManualSection SECTION = new TotemManualSection(
            Identifier.fromNamespaceAndPath("totem", "alchemy/manual"),
            SECTION_ORDER,
            "book.deadrecall.alchemy_manual.title",
            PAGE_KEYS
    );

    private AlchemyManual() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        TotemManualRegistry.global().register(SECTION);
        TotemManualLifecycle.registerLoginRefresh();

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isSpectator()
                    || !world.getBlockState(hitResult.getBlockPos()).is(Blocks.BREWING_STAND)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!isManualRequest(stack)) {
                return InteractionResult.PASS;
            }
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return grant((ServerPlayer) player, hand)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
    }

    public static boolean isManualRequest(ItemStack stack) {
        return TotemManualPlayerHelper.supportsSourceInteraction(stack, ignored -> false);
    }

    public static boolean grant(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return false;
        }
        boolean handled = TotemManualPlayerHelper.acquireSections(
                player,
                hand,
                List.of(SECTION),
                MANUAL_ADVANCEMENT,
                ignored -> false
        ).handled();
        if (handled) {
            AlchemyDiscoveryService.send(player);
        }
        return handled;
    }

    public static List<String> pageKeys() {
        return PAGE_KEYS;
    }
}
