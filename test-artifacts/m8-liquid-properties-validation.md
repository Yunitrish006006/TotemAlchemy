# M8 liquid registry and properties validation

Status: final M8 regression handoff.

## Implemented contract

M8 now defines one explicit liquid-property layer on top of LiquidComposition.

### Built-in liquid identities

- `minecraft:water`
- `minecraft:milk`
- `minecraft:honey`

All three are currently registered with neutral properties because no non-neutral balance values have been explicitly approved yet:

- stability multiplier = 1.0
- reaction-speed multiplier = 1.0
- duration multiplier = 1.0
- potency multiplier = 1.0

This keeps current gameplay behavior unchanged while the runtime property mechanisms are available for later balance tuning.

## Mixed-liquid property resolution

`LiquidPropertyResolver` normalizes `LiquidComposition` and computes deterministic weighted averages for all four multipliers.

Unknown liquid ids participate with neutral properties instead of being dropped. This prevents an unregistered component from shrinking the denominator and unintentionally amplifying a known liquid's modifier.

Null or empty compositions resolve to neutral properties.

## Stability modifier

The stability multiplier scales stability loss only.

- 1.0 preserves existing loss.
- 0.0 prevents new stability damage.
- below 1.0 reduces the loss rate.
- above 1.0 increases it.

Fractional loss is preserved through `stabilityDamageCarry`, which survives copy, split and serialization and is volume-weighted on merge. Explicit `setStability` and empty reset clear the carry. Positive stability recovery and explicit hard stability assignment are not multiplied.

## Reaction-speed modifier

Reaction speed is captured when a cauldron pending reaction is scheduled:

`adjustedRequiredTicks = ceil(baseRequiredTicks / reactionSpeedMultiplier)`

with a minimum of one tick.

The adjusted `requiredTicks` is persisted as part of the pending `Reaction`, so later save/load, extraction, merge or composition changes do not reinterpret existing timing. Compound/resumed reactions preserve their completion fraction when timing is adjusted.

Brewing Stand processing time is intentionally unchanged because Brewing Stand does not use this pending-reaction timer.

## Duration and potency modifiers

Duration and potency affect portable Minecraft effect presentation only.

Canonical `EffectDose.quantity` remains unchanged and therefore retains the M6 conservation contract.

- duration multiplier scales rendered effect duration;
- potency multiplier scales continuous potency level before Minecraft amplifier discretization;
- zero duration or potency suppresses the rendered effect without deleting stored EffectDose;
- neutral 1.0/1.0 preserves the existing portable effect output.

Effectful canonical potions bypass the canonical fast path when presentation is non-neutral so modifiers cannot be skipped. Canonical no-effect potions retain the fast path.

## Regression coverage

The final M8 regression pass verifies:

- Water, Milk and Honey remain registered and neutral at current balance;
- equal and skewed Water/Milk/Honey mixtures resolve neutral;
- stability policy is unchanged under neutral mixed properties;
- reaction timing is unchanged under neutral mixed properties;
- portable duration/amplifier presentation is unchanged under neutral mixed properties;
- canonical EffectDose quantity is unchanged;
- unknown-liquid weight remains neutral.

Earlier focused tests additionally cover synthetic non-neutral weighted properties, fractional stability carry, faster/slower reaction timing, duration-only scaling, potency-only scaling and presentation suppression.

## M8 boundary

M8 intentionally does not:

- choose non-neutral Water/Milk/Honey balance values;
- map Minecraft liquid containers to Alchemy liquids (M9);
- migrate datapack chemistry content (M10);
- rebuild signature brews (M11);
- alter Brewing Stand station success bonus (M7-T03 remains independently blocked).
