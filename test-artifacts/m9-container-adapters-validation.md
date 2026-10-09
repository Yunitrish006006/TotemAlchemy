# M9 container adapters validation

Status: final M9 regression handoff.

## Shared boundary

Portable liquid/mixture containers now expose one pure conversion boundary:

- `LiquidContainerAdapter.drain(ItemStack, maxUnits)`
- `LiquidContainerAdapter.fill(ItemStack, AlchemyMixtureState, maxUnits)`

Adapters receive defensive copies and return detached stack/mixture results. World mutation, inventory replacement, sounds, and player messages remain outside the adapter layer.

Adapter dispatch is deterministic: the first registered adapter that can perform the requested transfer wins.

## Registered adapters

Current built-in precedence:

1. Water Bottle
2. Water Bucket
3. Milk Bucket
4. Honey Bottle
5. Large Flask
6. vanilla potion compatibility

### Water Bottle

- one Water Bottle = one Water mixture unit;
- only plain Water can export as a vanilla Water Bottle;
- stored/custom potion mixtures remain for generic potion compatibility.

### Water Bucket

- one bucket = three mixture units;
- plain Water remains a normal vanilla Water Bucket;
- non-plain three-unit mixtures use the existing stored-mixture Water Bucket representation;
- stored mixture buckets drain back to their full encoded AlchemyMixtureState instead of degrading to plain Water.

### Milk Bucket

- one Milk Bucket = three `minecraft:milk` units;
- plain Milk exports as a vanilla Milk Bucket;
- chemistry-bearing Milk is rejected rather than silently discarded.

### Honey Bottle

- one Honey Bottle = one `minecraft:honey` unit;
- plain Honey exports as a vanilla Honey Bottle;
- chemistry-bearing Honey is rejected rather than silently discarded.

### Large Flask

- preserves complete AlchemyMixtureState;
- supports partial drain and partial top-up;
- uses current `FlaskEnchantments.capacity(stack)` on fill;
- base capacity is three units and Capacity V supports eight;
- existing over-capacity contents remain drainable after capacity enchantment removal;
- topping up a non-empty flask uses existing compound merge compatibility.

### Vanilla potion containers

- supports Potion, Splash Potion, and Lingering Potion;
- one container = one mixture unit;
- import delegates to `AlchemyMixtureBottle.fromPotion`;
- export delegates to `AlchemyMixtureBottle.toPotion`;
- canonical potion id, DeliveryForm, EffectDose, LiquidComposition, and stored reaction state are preserved;
- Hot Cocoa and Cherry Brew remain outside the generic adapter and are deferred to signature-brew work.

## Mixed-liquid cauldron regression

M9-T08 covers real cauldron interaction paths with a Water/Milk/Honey mixture carrying activated base, EffectDose, stability, provenance, and a pending reaction.

The regression suite verifies:

- full three-unit mixed cauldron → stored Water Bucket → empty cauldron round-trip preserves the complete encoded mixture;
- one-bottle extraction splits three units into one plus two while preserving LiquidComposition;
- bottle extraction conserves EffectDose and activated-base quantities;
- bottle extraction preserves pending reaction elapsed/required timing and reaction dose on both portions;
- full mixed cauldron → Large Flask → empty cauldron round-trip preserves the complete encoded mixture;
- source containers/mixtures are not mutated by adapter preview operations.

## M9 boundary

M9 intentionally does not:

- change ingredient/reaction chemistry or datapack ownership;
- change Honey Bottle ingredient precedence in reaction content;
- migrate Minecraft/Totem chemistry data (M10);
- rebuild Hot Cocoa or Cherry Brew (M11);
- choose the blocked Brewing Stand station success-bonus value (M7-T03).
