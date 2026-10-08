# M4 activated-base composition regression validation

Status: bounded validation handoff for M4-T10.

## Activated-base composition contract

M4 replaces the coarse activation flag as chemistry authority with explicit conserved activated-base units while preserving compatibility behavior:

- `ActivatedBaseComposition` is an immutable value object keyed by base identifier.
- Entries store absolute activated-base units; total activated units are the sum of components.
- Base concentration is derived as activated units divided by mixture volume.
- Component ordering is deterministic by base identifier.
- Copy preserves composition; resetting an empty state clears it.
- `mergeFrom` adds absolute activated-base units without creating or destroying them.
- `extractUnits` splits activated-base units proportionally with volume.
- Persistence uses deterministic `A|` records.
- Modern explicit empty composition uses the empty `A|` sentinel.
- Legacy activated states with no `A|` marker migrate to `totem:alchemy/legacy_activated_base` at one activated unit per liquid unit.
- `baseActivated()` is derived from explicit composition.
- The compatibility setter can create or clear the anonymous legacy fallback but does not overwrite an existing explicit base identity.

## Regression coverage

M4 is covered by:

- `ActivatedBaseCompositionTest`
  - immutable value semantics
  - deterministic identifier ordering
  - invalid numeric rejection
  - aggregate unit accounting
- `AlchemyMixtureGameTest`
  - state storage
  - total-unit and concentration derivation
  - copy/reset lifecycle
  - merge conservation
  - proportional extraction conservation
  - deterministic `A|` serialization
  - explicit empty `A|` sentinel
  - modern `A|` decode
  - legacy `baseActivated` migration
  - compatibility-derived activation behavior
  - end-to-end ingredient outcome -> merge -> extract -> encode/decode -> recombine regression

## Ingredient-yield unchanged invariant

M4 does not apply activated-base concentration to ingredient yield. That behavior is intentionally deferred to M5.

The M4 regression pass requires:

- a compatibility-activated state and an equivalent explicitly identified activated-base state produce the same selected ingredient effect set;
- the same selected ingredient outcome produces identical EffectDose quantity and amplifier cap regardless of whether activation came from the legacy compatibility facade or explicit base composition;
- adding effectless activated-base liquid changes base composition and concentration but does not create or destroy existing ingredient EffectDose quantity;
- splitting, persisting, restoring, and recombining an activated-base mixture restores the original per-base units and ingredient EffectDose;
- M4 does not introduce dose scaling, starter-capacity consumption, or base-concentration yield scaling. Those remain M5 work.

## Persistence and migration boundary

The `A|` marker is also the format-version boundary for activated-base state:

- explicit `A|<id>|<units>` records are modern composition data;
- an empty `A|` record is a modern explicit-empty sentinel;
- only persisted states with no `A|` marker are eligible for legacy `B|` migration;
- corrupt explicit `A|` records are tolerated under the existing codec policy but are not misclassified as legacy absence;
- legacy `B|1` cannot restore historical base identity because old saves never stored one, so migration uses the dedicated anonymous fallback rather than inventing a specific base.

## Known repository-wide inherited failures

The repository-wide Build currently has four documented regressions outside M4:

- Totem Alchemy built-in datapack unexpectedly enabled by default.
- Aggregate OFF/OFF vanilla-safety fixture does not begin in OFF/OFF state.
- Awkward + Sugar vanilla swiftness regression fails under the same pack-state condition.
- Alchemy manual chapter page count expects 64 but currently observes 65.

These are not accepted as M4 behavior. M4-T10 is complete only if this branch adds no distinct compile or test regression beyond that inherited baseline.

## Next dependency

After M4-T10 validation, the next chemistry milestone is M5: base activation and ingredient dose.

M5 is where activated-base concentration begins participating in reaction capacity and produced EffectDose. M4 intentionally stops before that gameplay change.

M7-T03 remains independently blocked until an explicit Brewing Stand station success-bonus balance value is chosen.
