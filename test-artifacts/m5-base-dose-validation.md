# M5 base activation and ingredient dose validation

Status: final M5 regression handoff.

## Implemented contract

M5 now separates three bounded concepts:

1. **Base-reaction capacity**
   - remaining unactivated liquid units;
   - starter reactions transform those units into named activated-base units;
   - dilution never creates activated base.

2. **Pending ingredient dose**
   - pending reactions carry explicit integer dose;
   - repeated copies of the same pending outcome ingredient increment that dose;
   - configured `IngredientReaction.maxDose` is enforced in both interaction preview and scheduling.

3. **EffectDose production**
   - registry-backed ingredient reactions scale produced quantity by
     `baseConcentration * reaction.dose * effectYield`;
   - normal-recipe production remains capped by the M6 standard-concentration ceiling;
   - existing EffectDose already present in the mixture is not rescaled.

## Finite repeated-dose rule

Completing a reaction and later scheduling the same ingredient again does not reuse `completedStages` as a capacity ledger. Completed stages remain timing/history only.

Instead, repeated completed reactions remain bounded by the combination of:

- unchanged finite activated-base concentration;
- per-pending-reaction `maxDose`;
- the normal-recipe EffectDose concentration ceiling.

The M5-T10 regression repeatedly completes and reschedules Sugar against the same three-unit mixture containing one activated base unit. Across 32 cycles it verifies:

- each new pending reaction begins at dose 1 under the current Sugar `max_dose=1`;
- activated-base units remain exactly one;
- base concentration remains exactly one third;
- produced Speed EffectDose never decreases;
- produced Speed EffectDose never exceeds the three-volume standard normal-recipe ceiling.

This prevents repeated ingredient use from creating unbounded EffectDose from the same finite base while preserving the M5-T03 rule that completed timing history is not itself chemical capacity.

## M5 validation boundary

M5 intentionally does not:

- rebalance datapack `max_dose` values;
- change Brewing Stand station success bonus;
- migrate the remaining datapack content planned for M10;
- start later unrelated chemistry milestones.

M6 EffectDose/concentration behavior remains the canonical quantity and presentation layer used by this M5 production rule.
