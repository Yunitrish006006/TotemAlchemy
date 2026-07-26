# Extraction contract

## Approved authority

TotemAlchemy owns the Alchemy Cauldron, Pig Manure and Cherry Brew gameplay:

- `alchemy/**`, the Alchemy Cauldron block/entity and its reloadable recipes;
- the `saltpeter`, `pig_manure`, `wood_ash`, `cocoa_powder`, `hot_cocoa`,
  `cherry_brew`, `stone_bowl` and `sulfur_bowl` item identifiers;
- the `deadrecall:flint_from_bowl` recipe and serializer, which preserve the
  Alchemy-owned Stone Bowl as a crafting remainder;
- the `pig_manure_*` blocks, `stinky` and `cherry_bloom` effects, and the two
  Pig Manure advancement criteria;
- the Pig/Snowball Mixins, Pig Manure goal and recipe/data/texture assets
  required by that gameplay.

Existing `deadrecall:*` registry, recipe, effect, block-entity and persistent
cauldron recipe IDs remain readable throughout the compatibility window. The
cauldron block entity already contains a legacy-state reader and this behavior
must move unchanged. Version `0.1.1` adds the module-owned
`deadrecall:alchemy_root` advancement parent so the preserved advancement IDs
also load when DeadRecall is intentionally absent.

## Verified seams and exclusions

The legacy owner is currently split across `ModBlocks`, `ModBlockEntities`,
`ModMobEffects`, `LegacyGameplayItemRegistration`,
`LegacyGameplayCriteriaRegistration`, `LegacyGameplayItemGroupRegistration`,
`LegacyGameplayBootstrap` and `deadrecall.legacy.mixins.json`. The first
external module replaces these as one registration path; DeadRecall must gate
the matching legacy path when `totem-alchemy` is present.

`CocoaPowderRecipe` and Flint-from-Bowl move with Alchemy because they use
Alchemy-owned ingredients. Portable container policy belongs to TotemRemnant
and must not be copied here. The
shared `deadrecall` locale files remain in the compatibility bundle until they
are separated without duplicate resource paths.

## Dependency and validation rules

The module may depend on TotemCore and Fabric API only. It must not import
DeadRecall or another feature implementation. Before cutover it needs Java 25
unit tests, standalone Dedicated Server startup, cauldron persistence/restart
coverage, and an assembled compatibility-bundle proof with exactly one owner
for every preserved registration, resource and Mixin.
