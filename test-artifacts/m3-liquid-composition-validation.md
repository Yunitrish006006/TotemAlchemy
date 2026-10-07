# M3 liquid-composition regression validation

Status: bounded validation handoff for M3-T10.

## Composition contract

M3 introduces liquid composition as normalized chemistry metadata without changing the existing EffectDose gameplay model:

- `LiquidComposition` is an immutable value object keyed by liquid identifier.
- State storage uses normalized fractions; total liquid amount remains owned by `AlchemyMixtureState.volumeUnits`.
- Component ordering is deterministic by liquid identifier.
- Copy preserves composition; resetting an empty state clears it.
- `mergeFrom` volume-weights composition fractions.
- `extractUnits` preserves homogeneous composition ratios in both remaining and extracted states.
- Persistence uses deterministic `L|` records.
- Modern explicit unknown composition uses the empty `L|` sentinel.
- Legacy non-empty mixture state with no `L|` marker migrates to Water 100%.
- Vanilla potion, splash-potion, and lingering-potion imports initialize as Water 100%.
- Totem-specific potion-like items are not implicitly classified as Water by the vanilla import adapter.

## Regression coverage

M3 is covered by:

- `LiquidCompositionTest`
  - defensive value semantics
  - deterministic identifier ordering
  - explicit normalization
  - epsilon pruning
  - invalid numeric rejection
- `AlchemyMixtureGameTest`
  - normalized state storage
  - copy/reset lifecycle
  - volume-weighted merge
  - extraction ratio preservation
  - deterministic `L|` serialization
  - explicit modern unknown sentinel
  - `L|` decode and legacy Water migration
  - vanilla potion-container Water import
  - end-to-end import → merge → extract → encode/decode → recombine conservation
- Existing mixture regressions remain authoritative for:
  - EffectDose quantity conservation
  - dilution concentration
  - extraction/recombination concentration
  - potion delivery form and portable-container behavior

## Gameplay-unchanged invariant

Liquid composition does not replace or rescale the pre-existing effect model.

The M3 regression pass requires:

- adding effectless liquid changes volume and therefore concentration, but never creates or destroys EffectDose quantity;
- extracting a homogeneous mixture proportionally splits EffectDose quantity while preserving concentration;
- recombining extracted liquid restores total EffectDose quantity;
- composition persistence does not alter delivery form or effect presentation;
- M3 does not add activated-base chemistry, liquid property behavior, or new reaction matching. Those remain later milestones.

## Known repository-wide inherited failures

The repository-wide Build has four documented regressions that predate M3:

- Totem Alchemy built-in datapack unexpectedly enabled by default.
- Aggregate OFF/OFF vanilla-safety fixture does not begin in OFF/OFF state.
- Awkward + Sugar vanilla swiftness regression fails under the same pack-state condition.
- Alchemy manual chapter page count expects 64 but currently observes 65.

These are not accepted as M3 behavior. M3-T10 is complete only if this branch adds no distinct compile or test regression beyond that inherited baseline.

## Next dependency

After M3-T10 validation, the next chemistry milestone is M4: activated-base composition.

M7-T03 remains independently blocked until an explicit Brewing Stand station success-bonus balance value is chosen.
