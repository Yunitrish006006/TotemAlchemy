package dev.totem.alchemy.gametest;

import dev.totem.alchemy.manual.AlchemyManual;
import dev.totem.alchemy.manual.AlchemyMaterialCatalog;
import dev.totem.alchemy.alchemy.AlchemyPotions;
import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.discovery.AlchemyDiscoveryKey;
import dev.totem.alchemy.discovery.AlchemyDiscoverySavedData;
import dev.totem.alchemy.discovery.AlchemyDiscoveryService;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
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
            int expectedPageCount = AlchemyMaterialCatalog.entries().size() + 3;
            if (AlchemyManual.pageKeys().size() != expectedPageCount) {
                helper.fail("Alchemy manual page count did not follow the material catalog: expected "
                        + expectedPageCount + ", got " + AlchemyManual.pageKeys().size());
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
            List<String> recordedSectionIds = TotemManualAssembler.sections(manual).stream()
                    .map(section -> section.id().toString())
                    .toList();
            List<String> expectedSectionIds = List.of(
                    TotemManualOnboarding.SECTION_ID.toString(),
                    "totem:alchemy/manual"
            );
            if (alchemySection.order() != AlchemyManual.SECTION_ORDER
                    || !recordedSectionIds.equals(expectedSectionIds)
                    || content.pages().size() != 2
                    || TotemManualAssembler.virtualPages(TotemManualAssembler.sections(manual)).size()
                    <= AlchemyManual.pageKeys().size()) {
                helper.fail("Alchemy guide did not seed the shared Core + Alchemy virtual manual: " + recordedSectionIds);
                return;
            }
            var advancement = player.level().getServer().getAdvancements().get(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("deadrecall", "alchemy_manual"));
            if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
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
                    .map(section -> section.id().toString()).toList();
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
            ItemStack brewedPoison = PotionContents.createItemStack(Items.POTION, Potions.POISON);
            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(), player.blockPosition(), ingredient,
                    java.util.List.of(awkward), java.util.List.of(brewedPoison), 400);

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
            if (data.research(player.getUUID()).getOrDefault(poisonKey, 0) != 1) {
                helper.fail("Successful brewing batch did not add exactly one research observation");
                return;
            }
            if (data.materialSampleCount(player.getUUID(), "minecraft:spider_eye") != 1) {
                helper.fail("Successful brewing batch did not add exactly one material sample");
                return;
            }
            AlchemyDiscoverySavedData.ProcessingTimeStats timing =
                    data.processingTime(player.getUUID(), "minecraft:spider_eye");
            if (timing.samples() != 1 || timing.averageTicks() != 400) {
                helper.fail("Successful brewing batch did not record one 400-tick processing-time observation");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void simultaneousEffectsCountAsOneMaterialAndTimingSample(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.SUGAR);
            List<ItemStack> inputs = List.of(
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD),
                    PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)
            );
            List<MultiOutcomeBrewing.Outcome> outcomes = List.of(
                    new MultiOutcomeBrewing.Outcome(
                            Potions.SWIFTNESS, "message.deadrecall.alchemy.outcome.swiftness"),
                    new MultiOutcomeBrewing.Outcome(
                            Potions.SLOWNESS, "message.deadrecall.alchemy.outcome.slowness")
            );
            List<ItemStack> outputs = inputs.stream().map(input -> AlchemyMixtureBrewing.applyBrewingStandOutcomes(
                    ingredient,
                    input,
                    PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS),
                    outcomes
            )).toList();

            AlchemyDiscoveryService.recordSuccessfulBrewOutcomes(
                    player.level(),
                    player.blockPosition(),
                    ingredient,
                    inputs,
                    outputs,
                    400,
                    outcomes.stream().map(MultiOutcomeBrewing.Outcome::potion).toList(),
                    player.getUUID()
            );

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String swiftnessKey = AlchemyDiscoveryKey.of(Items.SUGAR, Potions.SWIFTNESS);
            String slownessKey = AlchemyDiscoveryKey.of(Items.SUGAR, Potions.SLOWNESS);
            if (data.research(player.getUUID()).getOrDefault(swiftnessKey, 0) != 1
                    || data.research(player.getUUID()).getOrDefault(slownessKey, 0) != 1) {
                helper.fail("Simultaneous effects were not each counted as one outcome occurrence");
                return;
            }
            if (data.materialSampleCount(player.getUUID(), "minecraft:sugar") != 1) {
                helper.fail("One multi-effect batch was counted as more than one material sample");
                return;
            }
            AlchemyDiscoverySavedData.ProcessingTimeStats timing =
                    data.processingTime(player.getUUID(), "minecraft:sugar");
            if (timing.samples() != 1 || timing.averageTicks() != 400) {
                helper.fail("One multi-effect batch was counted as more than one timing sample");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void netherWartBaseBrewRecordsDiscoverySampleAndTimingOncePerBatch(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.NETHER_WART);
            List<ItemStack> inputs = List.of(
                    PotionContents.createItemStack(Items.POTION, Potions.WATER),
                    PotionContents.createItemStack(Items.POTION, Potions.WATER),
                    PotionContents.createItemStack(Items.POTION, Potions.WATER)
            );
            List<ItemStack> outputs = inputs.stream()
                    .map(input -> helper.getLevel().potionBrewing().mix(ingredient, input))
                    .toList();
            if (outputs.stream().anyMatch(output -> !output
                    .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD))) {
                helper.fail("Vanilla Nether Wart batch did not genuinely produce Awkward Potion");
                return;
            }

            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(), player.blockPosition(), ingredient, inputs, outputs, 400);

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String awkwardKey = AlchemyDiscoveryKey.of(Items.NETHER_WART, Potions.AWKWARD);
            if (!data.has(player.getUUID(), awkwardKey)) {
                helper.fail("Nether Wart revealed Awkward Potion without recording its discovery");
                return;
            }
            if (data.research(player.getUUID()).getOrDefault(awkwardKey, 0) != 1
                    || data.researchTotal(player.getUUID(), "minecraft:nether_wart") != 1) {
                helper.fail("Three bottles in one Nether Wart batch did not produce exactly one research sample");
                return;
            }
            AlchemyDiscoverySavedData.ProcessingTimeStats timing =
                    data.processingTime(player.getUUID(), "minecraft:nether_wart");
            if (timing.samples() != 1 || timing.averageTicks() != 400) {
                helper.fail("Nether Wart research did not record one 400-tick processing observation");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void redMushroomWaterBatchRecordsOnlyAwkwardBaseOutcome(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack ingredient = new ItemStack(Items.RED_MUSHROOM);
        List<ItemStack> inputs = List.of(
                PotionContents.createItemStack(Items.POTION, Potions.WATER),
                PotionContents.createItemStack(Items.POTION, Potions.WATER),
                PotionContents.createItemStack(Items.POTION, Potions.WATER)
        );
        MultiOutcomeBrewing.beginBatch(ingredient, inputs, 0.0F, 0.0F, 0.0F);
        try {
            if (!MultiOutcomeBrewing.activeOutcomes().isEmpty()) {
                helper.fail("Water-only red-mushroom base batch selected effects before base activation");
                return;
            }
            List<ItemStack> outputs = inputs.stream()
                    .map(input -> helper.getLevel().potionBrewing().mix(ingredient, input))
                    .toList();
            if (outputs.stream().anyMatch(output -> !output
                    .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD))) {
                helper.fail("Water-only red-mushroom batch did not produce only Awkward Potion");
                return;
            }

            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(), player.blockPosition(), ingredient, inputs, outputs, 400);
            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String awkwardKey = AlchemyDiscoveryKey.of(Items.RED_MUSHROOM, Potions.AWKWARD);
            String poisonKey = AlchemyDiscoveryKey.of(Items.RED_MUSHROOM, Potions.POISON);
            String saturationKey = AlchemyDiscoveryKey.of(Items.RED_MUSHROOM, AlchemyPotions.SATURATION);
            if (!data.has(player.getUUID(), awkwardKey)
                    || data.has(player.getUUID(), poisonKey)
                    || data.has(player.getUUID(), saturationKey)) {
                helper.fail("Water-only red-mushroom batch recorded unused poison/saturation effects");
                return;
            }
            if (data.materialSampleCount(player.getUUID(), "minecraft:red_mushroom") != 1) {
                helper.fail("Water-only red-mushroom batch did not count exactly one material sample");
                return;
            }
            helper.succeed();
        } finally {
            MultiOutcomeBrewing.clearBatch();
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void sugarSwiftnessRecordsForCapturedResearcherEvenOutsideCompletionRadius(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack ingredient = new ItemStack(Items.SUGAR);
            ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            var farCompletionPos = player.blockPosition().offset(32, 0, 0);

            AlchemyDiscoveryService.recordSuccessfulBrew(
                    player.level(),
                    farCompletionPos,
                    ingredient,
                    List.of(awkward),
                    List.of(swiftness),
                    400,
                    Potions.SWIFTNESS,
                    player.getUUID()
            );

            AlchemyDiscoverySavedData data = AlchemyDiscoverySavedData.get(player.level().getServer());
            String swiftnessKey = AlchemyDiscoveryKey.of(Items.SUGAR, Potions.SWIFTNESS);
            if (!data.has(player.getUUID(), swiftnessKey)) {
                helper.fail("Captured sugar researcher lost the swiftness discovery after moving away");
                return;
            }
            if (data.research(player.getUUID()).getOrDefault(swiftnessKey, 0) != 1) {
                helper.fail("Sugar -> swiftness did not add one research sample for the captured player");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
