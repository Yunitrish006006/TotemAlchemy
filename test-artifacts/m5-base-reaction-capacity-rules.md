# M5 base-reaction capacity rules

Status: bounded rule handoff for M5-T04.

## Capacity source

Base-reaction capacity is the mixture's remaining unactivated liquid amount:

```
baseReactionCapacityUnits = max(0, volumeUnits - activatedBaseUnits)
```

It is not derived from `completedStages`, reaction count, effect count, or ingredient history.

## Transformation rule

A successful base reaction transforms unactivated capacity into explicit named activated-base units.

Given:

- result base id `B`
- requested activation `R`
- remaining base-reaction capacity `C`

the actual transformed amount is:

```
T = min(R, C)
```

For valid finite positive requests:

- add `T` absolute units to `ActivatedBaseComposition[B]`;
- reduce remaining unactivated capacity by `T` through the derived formula;
- keep `volumeUnits` unchanged;
- keep liquid composition unchanged;
- preserve already activated units of every base;
- never transform more units than exist.

If the request is invalid, non-positive, or capacity is exhausted, the transformation is a no-op and returns zero.

## Conservation invariant

For ordinary valid mixture states where activated units do not exceed liquid volume:

```
activatedBaseUnits + baseReactionCapacityUnits = volumeUnits
```

A base reaction moves units from the right-hand capacity term into activated-base composition without changing total liquid volume.

This makes the existing M4 merge/extract/persistence rules automatically apply to transformed capacity because transformed units are represented only as `ActivatedBaseComposition`.

## Relationship to BaseReaction

M5-T01 already resolves:

- the matching `BaseReaction`;
- `resultBaseId`;
- `activationYield`;
- actual activation units capped to unactivated capacity.

M5-T04 adds the state mutation primitive that consumes that resolved capacity:

```
state.activateBaseUnits(
    resolution.reaction().resultBaseId(),
    resolution.activationUnits()
)
```

Station/runtime wiring remains deferred.

## Not ingredient dose capacity

This base-reaction capacity rule must not be confused with ingredient dose limits.

M5-T05 through M5-T07 still own:

- explicit pending reaction `dose`;
- repeated same-ingredient dose accumulation;
- `max_dose` enforcement.

M5-T08 later uses base concentration when converting an accepted ingredient dose into EffectDose quantity.

Therefore M5-T04 does not define `max_dose`, does not consume ingredient dose, and does not change effect yield.

## Regression coverage

`baseReactionCapacityTransformsOnlyUnactivatedUnits` verifies:

- existing activated-base units reduce available capacity;
- requested activation transforms only the available amount;
- transformation preserves existing base identities;
- activation can target another named base;
- liquid volume and liquid composition remain unchanged;
- excess requests are capped;
- exhausted capacity rejects further transformation;
- total activated units reach, but cannot exceed, liquid volume through this API.
