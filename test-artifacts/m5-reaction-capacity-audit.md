# M5 reaction-capacity audit

Status: bounded audit handoff for M5-T03. This document records current behavior before M5-T04 defines new capacity-consumption rules.

## Current pending-reaction guard

The active cauldron scheduling guard is ingredient-local and pending-only:

- `AlchemyMixtureBrewing.canReact` rejects an ingredient while `AlchemyMixtureState.hasPendingReactionForIngredient` finds the same ingredient id in the current `reactions` map.
- A second starter is also rejected while an unactivated mixture already has a pending starter.
- Different ingredients may remain pending concurrently.
- Once a reaction completes and leaves `reactions`, the same ingredient is no longer blocked by the pending-only guard.

This is a scheduling/de-duplication rule. It is not a conserved reaction-capacity model.

## What completedStages currently represents

`completedStages` is timing/history state:

- a completed reaction creates one `CompletedStage` carrying reaction id, ingredient id, overcook ticks, and perfect-window ticks;
- completed stages drive per-material perfect-window / overcook timing and HUD presentation;
- completed stages serialize as `T|` records and survive encode/decode;
- merge combines stages with the same reaction id using the maximum timing values;
- finished-bottle heat locking clears completed stages because that cooking history should no longer advance.

No current station resolver asks `completedStages` whether an ingredient still has chemical capacity.

## Why completedStages is not usable as capacity today

Several current lifecycle behaviors prove that completed-stage history is not conserved chemistry:

- extraction copies the full completed-stage collection to the extracted state while the source retains the same collection;
- therefore completed-stage entries can exist in both halves after a split;
- merge de-duplicates only by reaction id and keeps timing maxima rather than summing a conserved amount;
- scheduling a later occurrence of an ingredient is not rejected merely because a completed stage exists for that ingredient;
- `addReaction` removes a completed stage only when the new reaction has the exact same reaction id, not because it consumes an ingredient-capacity balance.

Treating completed-stage count as dose or base capacity would therefore make extraction, merge, and repeat scheduling chemically incorrect.

## Focused regression coverage

M5-T03 adds `completedStagesAreTimingHistoryNotReactionCapacity`:

1. schedule Sugar on an activated mixture;
2. verify another Sugar is rejected while the first Sugar reaction is pending;
3. complete the reaction and verify Sugar is no longer pending and a completed timing stage exists;
4. schedule Sugar again and verify completed timing history does not block the new pending reaction;
5. verify scheduling the later reaction does not consume the existing completed-stage history as if it were capacity.

Existing tests already cover:

- independent progress for concurrent materials;
- independent completed-stage perfect windows;
- completed-stage codec persistence;
- finished-bottle cleanup of completed-stage history.

## M5-T04 handoff

M5-T04 must define reaction capacity as explicit chemistry rather than infer it from timing history.

The rule should account for at least:

- which base units provide capacity;
- when capacity is reserved, consumed, or transformed;
- how capacity behaves through merge and extraction;
- how pending and completed reactions reference that capacity;
- how repeated ingredient dose later consumes the same finite capacity.

M5-T03 intentionally makes no runtime capacity-policy change.
