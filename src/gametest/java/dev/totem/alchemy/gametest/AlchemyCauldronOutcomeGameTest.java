package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

public final class AlchemyCauldronOutcomeGameTest {
    @GameTest(maxTicks = 20)
    public void cauldronPreservesIndependentProbabilisticOutcomeSets(GameTestHelper helper) {
        ItemStack sugar = new ItemStack(Items.SUGAR);
        List<MultiOutcomeBrewing.Outcome> configured =
                MultiOutcomeBrewing.outcomesForIngredient(sugar);
        require(helper, configured.size() > 1,
                "Sugar fixture no longer exposes multiple probabilistic outcomes");

        float[] hitAllRolls = new float[configured.size()];
        Arrays.fill(hitAllRolls, 0.0F);
        List<MultiOutcomeBrewing.Outcome> hitAll =
                MultiOutcomeBrewing.chooseOutcomes(sugar, hitAllRolls);
        require(helper, hitAll.size() == configured.size(),
                "Independent cauldron rolls collapsed to a single canonical outcome");

        AlchemyMixtureState multiOutcomeState =
                new AlchemyMixtureState(AlchemyMixtureState.MAX_VOLUME_UNITS);
        multiOutcomeState.setBaseActivated(true);
        AlchemyMixtureBrewing.ScheduleResult multiOutcome =
                AlchemyMixtureBrewing.scheduleOutcomeSetDetailed(
                        helper.getLevel(), multiOutcomeState, sugar, hitAll);

        List<String> expectedIds = hitAll.stream()
                .map(MultiOutcomeBrewing.Outcome::potion)
                .map(holder -> BuiltInRegistries.POTION.getKey(holder.value()).toString())
                .distinct()
                .toList();
        require(helper, multiOutcome.scheduled(),
                "Cauldron rejected a valid multi-outcome reaction");
        require(helper, multiOutcome.researchable(),
                "Cauldron multi-outcome reaction stopped being researchable");
        require(helper, multiOutcome.resultPotionIds().equals(expectedIds),
                "Cauldron did not preserve the complete independently rolled outcome set");

        float[] missAllRolls = new float[configured.size()];
        Arrays.fill(missAllRolls, 0.999F);
        List<MultiOutcomeBrewing.Outcome> missAll =
                MultiOutcomeBrewing.chooseOutcomes(sugar, missAllRolls);
        require(helper, missAll.isEmpty(),
                "Sugar no-effect fixture unexpectedly selected an outcome");

        AlchemyMixtureState noEffectState =
                new AlchemyMixtureState(AlchemyMixtureState.MAX_VOLUME_UNITS);
        noEffectState.setBaseActivated(true);
        AlchemyMixtureBrewing.ScheduleResult noEffect =
                AlchemyMixtureBrewing.scheduleOutcomeSetDetailed(
                        helper.getLevel(), noEffectState, sugar, missAll);

        require(helper, noEffect.scheduled(),
                "Cauldron rejected a valid no-effect probabilistic result");
        require(helper, noEffect.researchable(),
                "Cauldron no-effect result stopped being researchable");
        require(helper, noEffect.resultPotionIds().isEmpty(),
                "Cauldron replaced a no-effect roll with a deterministic fallback");

        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
