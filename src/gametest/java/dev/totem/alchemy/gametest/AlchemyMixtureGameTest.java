package dev.totem.alchemy.gametest;

import dev.totem.alchemy.alchemy.MultiOutcomeBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureBottle;
import dev.totem.alchemy.mixture.AlchemyMixtureBrewing;
import dev.totem.alchemy.mixture.AlchemyMixtureState;
import dev.totem.alchemy.mixture.EffectDoseStandards;
import dev.totem.alchemy.mixture.LiquidComposition;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.util.RandomSource;

import java.util.Map;

public final class AlchemyMixtureGameTest {
    private static final double EPSILON = 0.0001D;

    @GameTest(maxTicks = 40)
    public void mixtureStateStoresNormalizedLiquidComposition(GameTestHelper helper) {
        Identifier water = Identifier.fromNamespaceAndPath("minecraft", "water");
        Identifier milk = Identifier.fromNamespaceAndPath("minecraft", "milk");

        AlchemyMixtureState state = new AlchemyMixtureState(2);
        require(helper, state.liquidComposition().isEmpty(),
                "New mixture state did not begin with empty liquid composition");

        state.setLiquidComposition(LiquidComposition.of(Map.of(
                water, 4.0D,
                milk, 2.0D
        )));

        requireNear(helper, state.liquidComposition().amount(water), 2.0D / 3.0D,
                "Mixture state did not store normalized water fraction");
        requireNear(helper, state.liquidComposition().amount(milk), 1.0D / 3.0D,
                "Mixture state did not store normalized milk fraction");
        requireNear(helper, state.liquidComposition().totalAmount(), 1.0D,
                "Mixture state liquid composition did not remain normalized");

        state.setLiquidComposition(null);
        require(helper, state.liquidComposition().isEmpty(),
                "Null liquid composition did not reset the state field to empty");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void liquidCompositionSurvivesCopyAndClearsWhenStateResets(GameTestHelper helper) {
        Identifier water = Identifier.fromNamespaceAndPath("minecraft", "water");
        Identifier milk = Identifier.fromNamespaceAndPath("minecraft", "milk");

        AlchemyMixtureState original = new AlchemyMixtureState(1);
        original.setLiquidComposition(LiquidComposition.of(Map.of(
                water, 3.0D,
                milk, 1.0D
        )));

        AlchemyMixtureState copy = original.copy();
        require(helper, copy.liquidComposition().equals(original.liquidComposition()),
                "Mixture copy did not preserve liquid composition");
        requireNear(helper, copy.liquidComposition().amount(water), 0.75D,
                "Mixture copy changed water composition");
        requireNear(helper, copy.liquidComposition().amount(milk), 0.25D,
                "Mixture copy changed milk composition");

        original.extractUnits(1);
        require(helper, original.volumeUnits() == 0,
                "Full extraction did not reset source volume");
        require(helper, original.liquidComposition().isEmpty(),
                "Reset-empty mixture retained stale liquid composition");
        require(helper, !copy.liquidComposition().isEmpty(),
                "Resetting the source also cleared the copied composition");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void liquidCompositionMergeUsesVolumeWeightedFractions(GameTestHelper helper) {
        Identifier water = Identifier.fromNamespaceAndPath("minecraft", "water");
        Identifier milk = Identifier.fromNamespaceAndPath("minecraft", "milk");

        AlchemyMixtureState oneWater = new AlchemyMixtureState(1);
        oneWater.setLiquidComposition(LiquidComposition.single(water, 1.0D));
        AlchemyMixtureState twoMilk = new AlchemyMixtureState(2);
        twoMilk.setLiquidComposition(LiquidComposition.single(milk, 1.0D));

        require(helper, oneWater.mergeFrom(twoMilk),
                "One water unit could not merge with two milk units");
        require(helper, oneWater.volumeUnits() == 3,
                "Volume-weighted composition merge changed total volume");
        requireNear(helper, oneWater.liquidComposition().amount(water), 1.0D / 3.0D,
                "One water unit did not contribute one third of merged composition");
        requireNear(helper, oneWater.liquidComposition().amount(milk), 2.0D / 3.0D,
                "Two milk units did not contribute two thirds of merged composition");
        requireNear(helper, oneWater.liquidComposition().totalAmount(), 1.0D,
                "Merged liquid composition did not remain normalized");

        AlchemyMixtureState known = new AlchemyMixtureState(1);
        known.setLiquidComposition(LiquidComposition.single(water, 1.0D));
        AlchemyMixtureState unknown = new AlchemyMixtureState(1);
        require(helper, known.mergeFrom(unknown),
                "Known composition could not merge with an unknown legacy composition");
        require(helper, known.liquidComposition().isEmpty(),
                "Unknown liquid volume was incorrectly inferred as the known composition");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void effectDoseUsesCanonicalLevelOneEquivalentTickQuantity(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose levelOne =
                AlchemyMixtureState.EffectDose.fromDuration(200, 0);
        AlchemyMixtureState.EffectDose levelTwo =
                AlchemyMixtureState.EffectDose.fromDuration(200, 1);

        requireNear(helper, levelOne.quantity(), 200.0D,
                "Level I EffectDose did not use duration ticks as canonical quantity");
        requireNear(helper, levelTwo.quantity(), 400.0D,
                "Level II EffectDose did not scale canonical quantity by amplifier + 1");
        requireNear(helper, levelTwo.potencyTicks(), levelTwo.quantity(),
                "Legacy potencyTicks accessor diverged from canonical EffectDose quantity");
        require(helper, levelTwo.durationForVolume(1) == 200,
                "Canonical quantity did not round-trip to the original one-volume duration");
        requireNear(helper, levelTwo.scale(0.5D).quantity(), 200.0D,
                "Scaling EffectDose did not scale canonical quantity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void standardDoseLookupUsesOneRegisteredPotionBottle(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose swiftness =
                EffectDoseStandards.forEffect(Potions.SWIFTNESS, "minecraft:speed");
        require(helper, swiftness != null,
                "Standard dose lookup did not expose swiftness");
        requireNear(helper, swiftness.quantity(),
                AlchemyMixtureState.EffectDose.quantityForDuration(20 * 180, 0),
                "Standard swiftness dose did not match its registered one-bottle effect");
        require(helper, swiftness.amplifierCap() == 0,
                "Standard swiftness dose changed its registered amplifier");

        AlchemyMixtureState.EffectDose strongSwiftness =
                EffectDoseStandards.forEffect(Potions.STRONG_SWIFTNESS, "minecraft:speed");
        require(helper, strongSwiftness != null,
                "Standard dose lookup did not expose strong swiftness");
        requireNear(helper, strongSwiftness.quantity(),
                AlchemyMixtureState.EffectDose.quantityForDuration(20 * 90, 1),
                "Strong swiftness did not use canonical registered quantity");
        require(helper, strongSwiftness.amplifierCap() == 1,
                "Strong swiftness standard dose lost its registered amplifier");

        require(helper, EffectDoseStandards.forPotion(Potions.AWKWARD).isEmpty(),
                "Effectless awkward potion unexpectedly produced a standard EffectDose");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void effectConcentrationIsQuantityPerBottleVolume(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose standard =
                EffectDoseStandards.forEffect(Potions.SWIFTNESS, "minecraft:speed");
        require(helper, standard != null,
                "Concentration fixture could not resolve standard swiftness dose");

        double baseline = standard.concentrationForVolume(1);
        requireNear(helper, baseline, standard.quantity(),
                "One standard bottle did not retain its standard EffectDose concentration");
        requireNear(helper, standard.concentrationForVolume(2), baseline / 2.0D,
                "Doubling liquid volume without adding EffectDose did not halve concentration");

        AlchemyMixtureState.EffectDose doubled = standard.merge(standard);
        requireNear(helper, doubled.concentrationForVolume(2), baseline,
                "Doubling EffectDose and volume together did not preserve concentration");
        requireNear(helper, standard.concentrationForVolume(0), 0.0D,
                "Zero liquid volume exposed a non-zero effect concentration");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void sustainedEffectSplitConservesConcentrationAtNeutralBias(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose standard =
                EffectDoseStandards.forEffect(Potions.SWIFTNESS, "minecraft:speed");
        require(helper, standard != null,
                "Sustained split fixture could not resolve standard swiftness dose");

        AlchemyMixtureState.SustainedEffectPresentation baseline =
                standard.sustainedPresentation(standard, 1);
        requireNear(helper, baseline.potencyLevel(), 1.0D,
                "Standard level-I potion did not map to potency level 1");
        requireNear(helper, baseline.durationTicks(), 20.0D * 180.0D,
                "Standard level-I potion did not preserve its native duration");
        requireNear(helper, baseline.concentration(), standard.concentrationForVolume(1),
                "Baseline sustained split did not conserve concentration");

        AlchemyMixtureState.EffectDose doubled = standard.merge(standard);
        AlchemyMixtureState.SustainedEffectPresentation concentrated =
                doubled.sustainedPresentation(standard, 1);
        double rootTwo = Math.sqrt(2.0D);
        requireNear(helper, concentrated.potencyLevel(), rootTwo,
                "Neutral split did not assign sqrt(concentration ratio) to potency");
        requireNear(helper, concentrated.durationTicks(), 20.0D * 180.0D * rootTwo,
                "Neutral split did not assign sqrt(concentration ratio) to duration");
        requireNear(helper, concentrated.concentration(), doubled.concentrationForVolume(1),
                "Concentrated sustained split did not conserve effect concentration");

        AlchemyMixtureState.EffectDose strongStandard =
                EffectDoseStandards.forEffect(Potions.STRONG_SWIFTNESS, "minecraft:speed");
        require(helper, strongStandard != null,
                "Sustained split fixture could not resolve strong swiftness dose");
        AlchemyMixtureState.SustainedEffectPresentation strong =
                strongStandard.sustainedPresentation(strongStandard, 1);
        requireNear(helper, strong.potencyLevel(), 2.0D,
                "Strong standard potion did not map amplifier 1 to potency level 2");
        requireNear(helper, strong.durationTicks(), 20.0D * 90.0D,
                "Strong standard potion did not preserve its native duration");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void sustainedEffectBiasIsConfigurableAndConservative(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose standard =
                EffectDoseStandards.forEffect(Potions.SWIFTNESS, "minecraft:speed");
        require(helper, standard != null,
                "Bias fixture could not resolve standard swiftness dose");

        AlchemyMixtureState.EffectDose doubled = standard.merge(standard);
        AlchemyMixtureState.SustainedEffectPresentation defaultSplit =
                doubled.sustainedPresentation(standard, 1);
        AlchemyMixtureState.SustainedEffectPresentation explicitNeutral =
                doubled.sustainedPresentation(
                        standard,
                        1,
                        AlchemyMixtureState.DEFAULT_SUSTAINED_EFFECT_BIAS
                );
        requireNear(helper, AlchemyMixtureState.DEFAULT_SUSTAINED_EFFECT_BIAS, 0.5D,
                "Default sustained-effect bias changed from 0.5");
        requireNear(helper, defaultSplit.potencyLevel(), explicitNeutral.potencyLevel(),
                "Default sustained split diverged from explicit 0.5 bias potency");
        requireNear(helper, defaultSplit.durationTicks(), explicitNeutral.durationTicks(),
                "Default sustained split diverged from explicit 0.5 bias duration");

        AlchemyMixtureState.SustainedEffectPresentation durationOnly =
                doubled.sustainedPresentation(standard, 1, 0.0D);
        requireNear(helper, durationOnly.potencyLevel(), 1.0D,
                "Duration-only bias unexpectedly increased potency");
        requireNear(helper, durationOnly.durationTicks(), 20.0D * 360.0D,
                "Duration-only bias did not allocate the full concentration ratio to duration");

        AlchemyMixtureState.SustainedEffectPresentation potencyOnly =
                doubled.sustainedPresentation(standard, 1, 1.0D);
        requireNear(helper, potencyOnly.potencyLevel(), 2.0D,
                "Potency-only bias did not allocate the full concentration ratio to potency");
        requireNear(helper, potencyOnly.durationTicks(), 20.0D * 180.0D,
                "Potency-only bias unexpectedly changed duration");

        requireNear(helper, durationOnly.concentration(), doubled.concentrationForVolume(1),
                "Duration-biased split did not conserve concentration");
        requireNear(helper, potencyOnly.concentration(), doubled.concentrationForVolume(1),
                "Potency-biased split did not conserve concentration");

        AlchemyMixtureState.SustainedEffectPresentation clampedLow =
                doubled.sustainedPresentation(standard, 1, -10.0D);
        AlchemyMixtureState.SustainedEffectPresentation clampedHigh =
                doubled.sustainedPresentation(standard, 1, 10.0D);
        requireNear(helper, clampedLow.durationTicks(), durationOnly.durationTicks(),
                "Bias below zero was not clamped to duration-only");
        requireNear(helper, clampedHigh.potencyLevel(), potencyOnly.potencyLevel(),
                "Bias above one was not clamped to potency-only");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void instantEffectPotencyTracksConcentrationWithoutDurationAxis(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose standard =
                EffectDoseStandards.forEffect(Potions.HEALING, "minecraft:instant_health");
        require(helper, standard != null,
                "Instant-effect fixture could not resolve standard healing dose");

        double standardPotency = standard.amplifierCap() + 1.0D;
        requireNear(helper, standard.instantPotencyLevel(standard, 1), standardPotency,
                "Standard instant potion did not preserve its registered potency level");

        AlchemyMixtureState.EffectDose doubled = standard.merge(standard);
        requireNear(helper, doubled.instantPotencyLevel(standard, 1), standardPotency * 2.0D,
                "Doubling instant EffectDose at fixed volume did not double potency");
        requireNear(helper, doubled.instantPotencyLevel(standard, 2), standardPotency,
                "Doubling instant EffectDose and volume together changed potency");
        requireNear(helper, standard.instantPotencyLevel(standard, 2), standardPotency * 0.5D,
                "Diluting an instant EffectDose across two volume units did not halve potency");
        requireNear(helper, standard.instantPotencyLevel(standard, 0), 0.0D,
                "Zero liquid volume exposed non-zero instant potency");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void waterDilutionConservesEffectDoseAndLowersConcentration(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        AlchemyMixtureState.EffectDose initial = state.effects().get("minecraft:speed");
        require(helper, initial != null,
                "Dilution fixture could not resolve swiftness EffectDose");

        double quantity = initial.quantity();
        double concentration = initial.concentrationForVolume(state.volumeUnits());

        require(helper, state.mergeFrom(AlchemyMixtureBrewing.waterState(1)),
                "One bottle-equivalent of water could not dilute the potion mixture");
        AlchemyMixtureState.EffectDose onceDiluted = state.effects().get("minecraft:speed");
        require(helper, state.volumeUnits() == 2,
                "First water dilution did not increase mixture volume to two units");
        requireNear(helper, onceDiluted.quantity(), quantity,
                "First water dilution created or destroyed EffectDose quantity");
        requireNear(helper, onceDiluted.concentrationForVolume(state.volumeUnits()), concentration / 2.0D,
                "First water dilution did not halve effect concentration");

        require(helper, state.mergeFrom(AlchemyMixtureBrewing.waterState(1)),
                "Second bottle-equivalent of water could not dilute the potion mixture");
        AlchemyMixtureState.EffectDose twiceDiluted = state.effects().get("minecraft:speed");
        require(helper, state.volumeUnits() == 3,
                "Second water dilution did not increase mixture volume to three units");
        requireNear(helper, twiceDiluted.quantity(), quantity,
                "Second water dilution created or destroyed EffectDose quantity");
        requireNear(helper, twiceDiluted.concentrationForVolume(state.volumeUnits()), concentration / 3.0D,
                "Second water dilution did not reduce concentration to one third");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void normalRecipeAdditionsRespectStandardConcentrationCap(GameTestHelper helper) {
        AlchemyMixtureState.EffectDose standard =
                EffectDoseStandards.forEffect(Potions.STRENGTH, "minecraft:strength");
        require(helper, standard != null,
                "Normal-recipe cap fixture could not resolve standard strength dose");

        ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        MultiOutcomeBrewing.Outcome strength =
                new MultiOutcomeBrewing.Outcome(Potions.STRENGTH, "message.totem.alchemy.outcome.strength");

        ItemStack first = AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.BLAZE_POWDER),
                awkward,
                PotionContents.createItemStack(Items.POTION, Potions.STRENGTH),
                strength
        );
        ItemStack second = AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.BLAZE_POWDER),
                first,
                PotionContents.createItemStack(Items.POTION, Potions.STRENGTH),
                strength
        );
        AlchemyMixtureState capped = AlchemyMixtureBottle.fromPotion(second);
        requireNear(helper, capped.effects().get("minecraft:strength").quantity(), standard.quantity(),
                "Repeated normal recipe additions exceeded standard one-volume concentration");

        AlchemyMixtureState alreadyConcentrated = new AlchemyMixtureState(1);
        alreadyConcentrated.setBaseActivated(true);
        alreadyConcentrated.putEffect("minecraft:strength", standard.quantity() * 2.0D, 0);
        ItemStack concentratedInput = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        AlchemyMixtureBottle.writeState(concentratedInput, alreadyConcentrated);
        ItemStack unchanged = AlchemyMixtureBrewing.applyBrewingStandIngredient(
                new ItemStack(Items.BLAZE_POWDER),
                concentratedInput,
                PotionContents.createItemStack(Items.POTION, Potions.STRENGTH),
                strength
        );
        AlchemyMixtureState preserved = AlchemyMixtureBottle.fromPotion(unchanged);
        requireNear(helper, preserved.effects().get("minecraft:strength").quantity(), standard.quantity() * 2.0D,
                "Normal recipe cap deleted pre-existing EffectDose above the recipe production limit");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void opposingSpeedEffectsNeutralizeByEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.putEffect("minecraft:speed", 2_000.0D, 0);
        state.putEffect("minecraft:slowness", 800.0D, 0);

        require(helper, !state.effects().containsKey("minecraft:slowness"),
                "Slowness remained after a smaller opposing dose was neutralized");
        requireNear(helper, state.effects().get("minecraft:speed").potencyTicks(), 1_200.0D,
                "Speed/slowness neutralization did not conserve the remaining quantity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void strengthAndWeaknessNeutralizeAcrossFireflyFamily(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(1);
        state.putEffect("minecraft:strength", 900.0D, 0);
        state.putEffect("totem:alchemy/firefly_strength", 600.0D, 0);
        state.putEffect("minecraft:weakness", 750.0D, 0);

        double remaining = state.effects().entrySet().stream()
                .filter(entry -> entry.getKey().equals("minecraft:strength")
                        || entry.getKey().equals("totem:alchemy/firefly_strength"))
                .mapToDouble(entry -> entry.getValue().potencyTicks())
                .sum();
        requireNear(helper, remaining, 750.0D,
                "Weakness did not neutralize the combined strength-family quantity");
        require(helper, !state.effects().containsKey("minecraft:weakness"),
                "Weakness remained despite a larger positive strength-family dose");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void legacyMixtureIdentifiersRewriteToCanonicalOnDecode(GameTestHelper helper) {
        AlchemyMixtureState legacy = new AlchemyMixtureState(1);
        legacy.setCanonicalPotionId("deadrecall:cherry_swiftness");
        legacy.putEffect("deadrecall:firefly_strength", 600.0D, 0);
        legacy.addReaction(new AlchemyMixtureState.Reaction(
                "compound:deadrecall:hot_cocoa:milk",
                "deadrecall:cocoa_powder",
                20,
                200,
                1,
                "deadrecall:saturation",
                "deadrecall:cherry_swiftness",
                Map.of("deadrecall:firefly_strength", new AlchemyMixtureState.EffectDose(20.0D, 0)),
                Map.of("deadrecall:cherry_bloom", new AlchemyMixtureState.EffectDose(40.0D, 0))
        ));

        AlchemyMixtureState decoded = AlchemyMixtureState.decode(legacy.encode());
        AlchemyMixtureState.Reaction reaction = decoded.reactions().iterator().next();
        require(helper, "totem:alchemy/cherry_swiftness".equals(decoded.canonicalPotionId()),
                "Legacy potion ID did not rewrite during mixture decode");
        require(helper, decoded.effects().containsKey("totem:alchemy/firefly_strength"),
                "Legacy effect ID did not rewrite during mixture decode");
        require(helper, "compound:totem:alchemy/hot_cocoa:milk".equals(reaction.id())
                        && "totem:alchemy/cocoa_powder".equals(reaction.ingredientId())
                        && "totem:alchemy/saturation".equals(reaction.sourcePotionId())
                        && "totem:alchemy/cherry_swiftness".equals(reaction.targetPotionId())
                        && reaction.targetEffects().containsKey("totem:alchemy/cherry_bloom"),
                "Legacy reaction metadata did not rewrite during mixture decode");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void bottlingAndRecombiningConservesMixedEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(2);
        state.putEffect("minecraft:night_vision", 4_000.0D, 0);
        state.putEffect("minecraft:regeneration", 1_800.0D, 0);
        double before = totalPotency(state);

        AlchemyMixtureState bottle = state.extractBottle();
        double split = totalPotency(state) + totalPotency(bottle);
        requireNear(helper, split, before,
                "Extracting a bottle created or destroyed effect quantity");
        require(helper, state.volumeUnits() == 1 && bottle.volumeUnits() == 1,
                "A two-unit mixture did not split into one-unit bottle states");
        require(helper, state.mergeFrom(bottle), "The extracted bottle could not be recombined");
        require(helper, state.volumeUnits() == 2, "Recombining the bottle did not restore liquid volume");
        requireNear(helper, totalPotency(state), before,
                "Recombining a bottle changed the conserved effect quantity");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void extractingAndMergingPreservesPerEffectDoseAndConcentration(GameTestHelper helper) {
        AlchemyMixtureState state = new AlchemyMixtureState(3);
        state.putEffect("minecraft:night_vision", 9_000.0D, 0);
        state.putEffect("minecraft:regeneration", 3_600.0D, 0);

        Map<String, AlchemyMixtureState.EffectDose> original = state.effects();
        double nightVisionConcentration =
                original.get("minecraft:night_vision").concentrationForVolume(state.volumeUnits());
        double regenerationConcentration =
                original.get("minecraft:regeneration").concentrationForVolume(state.volumeUnits());

        AlchemyMixtureState extracted = state.extractUnits(2);
        require(helper, state.volumeUnits() == 1 && extracted.volumeUnits() == 2,
                "Extracting two units did not leave one unit and return two units");

        for (String effectId : original.keySet()) {
            double originalQuantity = original.get(effectId).quantity();
            double splitQuantity = state.effects().get(effectId).quantity()
                    + extracted.effects().get(effectId).quantity();
            requireNear(helper, splitQuantity, originalQuantity,
                    "Extracting units created or destroyed EffectDose for " + effectId);
        }

        requireNear(helper,
                state.effects().get("minecraft:night_vision").concentrationForVolume(state.volumeUnits()),
                nightVisionConcentration,
                "Remaining night-vision mixture changed concentration during extraction");
        requireNear(helper,
                extracted.effects().get("minecraft:night_vision").concentrationForVolume(extracted.volumeUnits()),
                nightVisionConcentration,
                "Extracted night-vision mixture changed concentration");
        requireNear(helper,
                state.effects().get("minecraft:regeneration").concentrationForVolume(state.volumeUnits()),
                regenerationConcentration,
                "Remaining regeneration mixture changed concentration during extraction");
        requireNear(helper,
                extracted.effects().get("minecraft:regeneration").concentrationForVolume(extracted.volumeUnits()),
                regenerationConcentration,
                "Extracted regeneration mixture changed concentration");

        require(helper, state.mergeFrom(extracted),
                "Extracted two-unit mixture could not be merged back");
        require(helper, state.volumeUnits() == 3,
                "Merging extracted units did not restore original volume");
        for (String effectId : original.keySet()) {
            requireNear(helper, state.effects().get(effectId).quantity(), original.get(effectId).quantity(),
                    "Merging extracted units did not restore EffectDose for " + effectId);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void unfinishedReactionProgressSurvivesBottleRoundTrip(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(3);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, new ItemStack(Items.NETHER_WART)),
                "Water mixture could not schedule a nether-wart reaction");
        state.tickReactions(123);
        AlchemyMixtureState.Reaction before = state.reactions().iterator().next();

        AlchemyMixtureState bottled = state.extractBottle();
        AlchemyMixtureState.Reaction bottleReaction = bottled.reactions().iterator().next();
        require(helper, bottleReaction.elapsedTicks() == before.elapsedTicks(),
                "Bottling reset unfinished reaction progress");
        require(helper, bottleReaction.remainingTicks() == before.remainingTicks(),
                "Bottling changed unfinished reaction time");

        ItemStack bottle = AlchemyMixtureBottle.toPotion(bottled);
        require(helper, AlchemyMixtureBottle.hasStoredMixture(bottle),
                "Unfinished bottle did not carry mixture metadata");
        AlchemyMixtureState restored = AlchemyMixtureBottle.fromPotion(bottle);
        AlchemyMixtureState.Reaction restoredReaction = restored.reactions().iterator().next();
        require(helper, restoredReaction.elapsedTicks() == before.elapsedTicks(),
                "ItemStack round-trip reset reaction progress");
        require(helper, restoredReaction.remainingTicks() == before.remainingTicks(),
                "ItemStack round-trip changed remaining reaction time");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void finishedBottleDoesNotResumeCookingWhenPouredBack(GameTestHelper helper) {
        AlchemyMixtureState finished = new AlchemyMixtureState(1);
        finished.setBaseActivated(true);
        finished.addReaction(new AlchemyMixtureState.Reaction(
                "test:finished", "minecraft:sugar", 0, 20, 1,
                "minecraft:awkward", null, Map.of(), Map.of(
                "minecraft:speed", new AlchemyMixtureState.EffectDose(3_600.0D, 0)
        )));
        finished.tickReactions(20);
        require(helper, finished.hasCompletedStages(), "Fixture did not finish its reaction stage");

        ItemStack bottle = AlchemyMixtureBottle.toPotion(finished);
        AlchemyMixtureState pouredBack = AlchemyMixtureBottle.fromPotion(bottle);
        require(helper, pouredBack.isHeatLockedAfterBottling(),
                "Finished bottled potion did not retain its heat lock");
        require(helper, !pouredBack.hasCompletedStages(),
                "Finished bottled potion retained completed ingredient stages");
        require(helper, pouredBack.provenance().stream().noneMatch(marker ->
                        marker.contains("minecraft:sugar") || marker.contains("test:finished")),
                "Finished bottled potion retained its original ingredient history");
        require(helper, pouredBack.effects().containsKey("minecraft:speed"),
                "Discarding finished ingredient history also discarded the final potion effect");
        int stability = pouredBack.stability();
        require(helper, !pouredBack.tickCompletedStages(RandomSource.create(42L), 20 * 120),
                "Finished bottled potion resumed completed-stage cooking after repour");
        require(helper, !pouredBack.tickOvercook(RandomSource.create(43L), 20 * 120),
                "Finished bottled potion resumed general overcooking after repour");
        require(helper, pouredBack.stability() == stability,
                "Repoured finished potion changed while heated");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void livePotionRegistryCompletesWaterToAwkwardInCauldron(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(3);
        ItemStack netherWart = new ItemStack(Items.NETHER_WART);
        require(helper, AlchemyMixtureBrewing.canReact(helper.getLevel(), state, netherWart),
                "Live PotionBrewing registry did not accept water + nether wart for cauldron chemistry");
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, netherWart),
                "Cauldron chemistry could not schedule water + nether wart");
        state.tickReactions(AlchemyMixtureState.DEFAULT_REACTION_TICKS);
        require(helper, !state.hasPendingReactions(), "Completed cauldron reaction remained pending");
        require(helper, "minecraft:awkward".equals(state.canonicalPotionId()),
                "Completed water + nether wart reaction did not become canonical awkward potion");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void twoDifferentPotionBottlesCanMixAndCounteract(GameTestHelper helper) {
        AlchemyMixtureState swiftness = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        AlchemyMixtureState slowness = AlchemyMixtureBottle.fromPotion(
                PotionContents.createItemStack(Items.POTION, Potions.SLOWNESS));
        require(helper, swiftness.mergeFrom(slowness),
                "Two different potion bottle states could not share one cauldron mixture");
        require(helper, swiftness.volumeUnits() == 2,
                "Mixing two potion bottles did not preserve two units of liquid");
        require(helper, swiftness.canonicalPotionId() == null,
                "A heterogeneous mixture incorrectly retained one canonical potion identity");
        require(helper, !(swiftness.effects().containsKey("minecraft:speed")
                        && swiftness.effects().containsKey("minecraft:slowness")),
                "Swiftness and slowness remained simultaneously instead of counteracting");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void redstoneAndGlowstoneConserveMixedEffectQuantity(GameTestHelper helper) {
        AlchemyMixtureState base = new AlchemyMixtureState(2);
        base.putEffect("minecraft:regeneration", 7_200.0D, 0);
        double original = totalPotency(base);

        AlchemyMixtureState redstone = base.copy();
        redstone.applyRedstoneModifier();
        requireNear(helper, totalPotency(redstone), original,
                "Redstone modifier changed total effect quantity");

        AlchemyMixtureState glowstone = base.copy();
        glowstone.applyGlowstoneModifier();
        requireNear(helper, totalPotency(glowstone), original,
                "Glowstone modifier changed total effect quantity");
        require(helper, glowstone.effects().get("minecraft:regeneration").amplifierCap()
                        > base.effects().get("minecraft:regeneration").amplifierCap(),
                "Glowstone did not favour higher potency");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void encodedMixturePreservesEffectsAndPendingChemistry(GameTestHelper helper) {
        AlchemyMixtureState state = AlchemyMixtureBrewing.waterState(2);
        require(helper, AlchemyMixtureBrewing.schedule(helper.getLevel(), state, new ItemStack(Items.NETHER_WART)),
                "Could not create pending chemistry for persistence test");
        state.tickReactions(77);
        state.setStability(63);

        AlchemyMixtureState restored = AlchemyMixtureState.decode(state.encode());
        require(helper, restored.volumeUnits() == 2, "Mixture codec lost liquid volume");
        require(helper, restored.stability() == 63, "Mixture codec lost stability");
        require(helper, restored.reactions().size() == 1, "Mixture codec lost pending reaction");
        AlchemyMixtureState.Reaction reaction = restored.reactions().iterator().next();
        require(helper, reaction.elapsedTicks() == 77, "Mixture codec lost reaction progress");
        require(helper, reaction.remainingTicks() == AlchemyMixtureState.DEFAULT_REACTION_TICKS - 77,
                "Mixture codec lost remaining reaction time");
        helper.succeed();
    }

    private static double totalPotency(AlchemyMixtureState state) {
        return state.effects().values().stream()
                .mapToDouble(AlchemyMixtureState.EffectDose::potencyTicks)
                .sum();
    }

    private static void requireNear(GameTestHelper helper, double actual, double expected, String message) {
        require(helper, Math.abs(actual - expected) <= EPSILON,
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
