# M2 reaction-registry regression validation

Status: bounded validation handoff for M2-T16.

## Authority contract

M2 establishes reaction data as the single authority for brewing chemistry:

- `BaseReaction`, `IngredientReaction`, and `ReactionOutcome` are data models loaded from server data.
- Stable resource IDs identify reactions.
- Indexed lookup resolves base + exact/tag ingredient deterministically.
- Processing time, reaction success chance, outcome chance, max dose, and Brewing Stand compatibility are reaction data.
- `MultiOutcomeBrewing` consumes registry-backed outcomes rather than Java hard-coded outcome pools.
- Brewing Stand chance policy resolves its base chance through reaction data rather than a hard-coded ingredient chance table.
- Ingredient reaction extensions target an existing reaction by ID and may only append new outcomes.
- Extension application is deterministic by extension resource ID; unknown targets and duplicate potion outcomes are reload errors.
- Manual and Discovery use `AlchemyReactionReader` to consume the merged reaction registry without depending on brewing batch state or legacy probability reads.

## Regression coverage

The M2 authority path is covered by:

- `AlchemyReactionDataLoaderTest`
  - stable reaction IDs
  - base/ingredient schema parsing
  - numeric/schema validation
  - deterministic reload error reporting
  - additive extension parsing and merge rules
  - scalar override, unknown target, and duplicate outcome rejection
- `AlchemyReactionReaderTest`
  - merged extension outcomes are visible to readers
  - configured outcome truth is preserved
  - no-effect truth is derived from the complete merged outcome set
- `ReactionRegistryMigrationGameTest`
  - migrated catalog loads from reaction data
  - processing/success/outcome values reach runtime
  - Brewing Stand policy consumes reaction-backed chance data
  - deterministic and probabilistic outcome paths both consume the registry
  - Manual/Discovery reader truth matches the loaded registry

## Known repository-wide inherited failures

The repository-wide Build currently has four documented regressions that predate the M2-T14..T16 tail:

- Totem Alchemy built-in datapack unexpectedly enabled by default.
- Aggregate OFF/OFF vanilla-safety fixture does not begin in OFF/OFF state.
- Awkward + Sugar vanilla swiftness regression fails under the same pack-state condition.
- Alchemy manual chapter page count expects 64 but currently observes 65.

These are not accepted as M2 behavior. M2-T16 is complete only if this branch adds no distinct compile or test regression beyond that inherited baseline.

## Next dependency

After M2-T16 validation, the next unblocked chemistry milestone is M3-T01: define `LiquidComposition`.

M7-T03 remains independently blocked until an explicit Brewing Stand station success-bonus balance value is chosen.
