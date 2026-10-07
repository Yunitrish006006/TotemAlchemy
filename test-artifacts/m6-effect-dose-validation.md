# M6 EffectDose regression validation

Status: bounded validation handoff for M6-T10.

## Canonical M6 contracts

- EffectDose quantity is conserved chemistry measured in level-I-equivalent ticks:
  `quantity = durationTicks * (amplifier + 1)`.
- Standard dose lookup is sourced from the registered one-bottle potion contents.
- Effect concentration is `quantity / bottle-equivalent volume`.
- Sustained effects split relative concentration between potency and duration while preserving their product.
- Sustained-effect potency bias is caller-configurable in `[0, 1]`, defaulting to `0.5`.
- Instant effects have no duration axis; relative concentration maps entirely to continuous potency.
- Water dilution changes concentration without changing total EffectDose.
- Extraction splits EffectDose proportionally and preserves concentration; merging restores the conserved total.
- Normal recipe production is capped at the registered standard concentration without deleting pre-existing over-cap EffectDose.

## Focused GameTest coverage

`AlchemyMixtureGameTest` contains one focused regression for each M6 behavior:

1. `effectDoseUsesCanonicalLevelOneEquivalentTickQuantity`
2. `standardDoseLookupUsesOneRegisteredPotionBottle`
3. `effectConcentrationIsQuantityPerBottleVolume`
4. `sustainedEffectSplitConservesConcentrationAtNeutralBias`
5. `sustainedEffectBiasIsConfigurableAndConservative`
6. `instantEffectPotencyTracksConcentrationWithoutDurationAxis`
7. `waterDilutionConservesEffectDoseAndLowersConcentration`
8. `extractingAndMergingPreservesPerEffectDoseAndConcentration`
9. `normalRecipeAdditionsRespectStandardConcentrationCap`

## Known repository-wide inherited failures

The M6 stacked branches compile and their added focused tests do not introduce a distinct GameTest failure. The repository-wide Build remains red because these four failures predate M6 and reproduce on the earlier station-resolver base:

- Totem Alchemy built-in datapack unexpectedly enabled by default.
- OFF/OFF aggregate vanilla-safety fixture does not begin in OFF/OFF state.
- Awkward + Sugar vanilla swiftness regression fails under the same pack-state condition.
- Alchemy manual chapter page count expects 64 but currently observes 65.

These are not accepted as M6 behavior; they remain separate inherited regressions to repair outside this bounded milestone.

## M7-T08 handoff

M7-T08 (Brewing Stand emits standard dose/concentration) is now unblocked by M6 and must use these boundaries:

- Use `EffectDoseStandards` as the source of the registered one-bottle standard dose.
- Treat one Brewing Stand potion bottle as one bottle-equivalent volume for standard concentration.
- Preserve canonical EffectDose quantity; do not derive recipe production quantity from rendered potency/duration presentation.
- Sustained and instant presentation helpers describe how concentration is expressed, not how much chemistry a normal recipe creates.
- Respect the normal-recipe production cap; repeated normal outcomes must not stack the same effect above standard concentration.
- Do not begin M7-T09 parity work or M7-T10 cleanup as part of M7-T08.
