package dev.totem.alchemy.gametest;

import dev.totem.alchemy.manual.AlchemyManual;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualOnboarding;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

public final class AlchemyManualGameTest {
    @GameTest(maxTicks = 20)
    public void plainBookBecomesAnAlchemyTotemManual(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
            if (!AlchemyManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Alchemy manual source did not handle a plain book");
                return;
            }
            ItemStack manual = player.getMainHandItem();
            WrittenBookContent content = manual.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (!TotemManualAssembler.isCanonical(manual) || content == null) {
                helper.fail("Alchemy manual source did not create a canonical Totem manual");
                return;
            }
            if (AlchemyManual.pageKeys().size() != 14) {
                helper.fail("Alchemy manual did not register all fourteen dynamic icon pages");
                return;
            }
            boolean hasAlchemySection = dev.totem.core.api.v1.manual.TotemManualRegistry.global()
                    .sections().stream()
                    .anyMatch(section -> section.id().toString().equals("totem:alchemy/manual"));
            if (!hasAlchemySection) {
                helper.fail("Canonical manual omitted the Alchemy section");
                return;
            }
            var alchemySection = dev.totem.core.api.v1.manual.TotemManualRegistry.global()
                    .sections().stream()
                    .filter(section -> section.id().toString().equals("totem:alchemy/manual"))
                    .findFirst()
                    .orElseThrow();
            if (alchemySection.order() != AlchemyManual.SECTION_ORDER
                    || content.pages().size() != TotemManualAssembler.validatePageLimit(
                    java.util.List.of(alchemySection))) {
                helper.fail("Alchemy guide did not contain exactly its ordered module section");
                return;
            }
            var advancement = player.level().getServer().getAdvancements().get(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "deadrecall", "alchemy_manual")
            );
            if (advancement == null
                    || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                helper.fail("Obtaining the Alchemy guide did not award its module advancement");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void alchemySourceMergesIntoHeldTotemManual(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            TotemManualOnboarding.register();
            ItemStack manual = TotemManualAssembler.create(List.of(TotemManualOnboarding.SECTION));
            player.setItemInHand(InteractionHand.MAIN_HAND, manual);

            if (!AlchemyManual.grant(player, InteractionHand.MAIN_HAND)) {
                helper.fail("Alchemy manual source did not handle an existing Totem manual");
                return;
            }
            if (player.getMainHandItem() != manual) {
                helper.fail("Alchemy manual source replaced the held manual instead of updating it");
                return;
            }
            List<String> sectionIds = TotemManualAssembler.sections(manual).stream()
                    .map(section -> section.id().toString())
                    .toList();
            if (!sectionIds.contains(TotemManualOnboarding.SECTION_ID.toString())
                    || !sectionIds.contains("totem:alchemy/manual")) {
                helper.fail("Alchemy chapter was not merged into the held Totem manual: " + sectionIds);
                return;
            }
            if (sectionIds.size() != 2) {
                helper.fail("Merging the Alchemy chapter duplicated or dropped manual sections: " + sectionIds);
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void successfulBrewRecordsOnlyItsActualOutcomeForTheNearestPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.SPIDER_EYE);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            MultiOutcomeBrewing.Outcome poison = new MultiOutcomeBrewing.Outcome(
                    Potions.POISON,
                    "message.deadrecall.alchemy.outcome.poison"
            );
            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(),
                    player.blockPosition(),
                    ingredient,
                    java.util.List.of(awkward),
                    poison
            );

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String poisonKey = AlchemyDiscoveryKey.of(Items.SPIDER_EYE, Potions.POISON);
            String weaknessKey = AlchemyDiscoveryKey.of(Items.SPIDER_EYE, Potions.WEAKNESS);
            if (!data.has(player.getUUID(), poisonKey)) {
                helper.fail("Successful brewing outcome was not written to the player's journal");
                return;
            }
            if (data.has(player.getUUID(), weaknessKey)) {
                helper.fail("Unselected brewing outcome was incorrectly unlocked");
                return;
            }
            if (data.discoveries(player.getUUID()).size() != 1) {
                helper.fail("Duplicate or unrelated discoveries were written to the journal");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
