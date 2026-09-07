# Canonical ownership contract

## Approved authority

TotemAlchemy owns the Alchemy Cauldron, Pig Manure and Cherry Brew gameplay.
All new registrations and data-pack resources use `totem:alchemy/*`:

- `alchemy/**`, the Alchemy Cauldron block/entity and its reloadable recipes;
- the `saltpeter`, `pig_manure`, `wood_ash`, `cocoa_powder`, `hot_cocoa`,
  `cherry_brew`, `stone_bowl` and `sulfur_bowl` item identifiers;
- the `totem:alchemy/flint_from_bowl` recipe and serializer, which preserve the
  Alchemy-owned Stone Bowl as a crafting remainder;
- the `pig_manure_*` blocks, `stinky` and `cherry_bloom` effects, and the two
  Pig Manure advancement criteria;
- the Pig/Snowball Mixins, Pig Manure goal and recipe/data/texture assets
  required by that gameplay.

`deadrecall:*` is not a live Alchemy registration or resource namespace. The
only retained handling is the explicit migration boundary: TotemCore invokes
Alchemy's allow-listed raw-NBT rewrite before chunk, block-entity, player,
entity, potion and effect registry codecs run. Alchemy also rewrites old IDs in
its cauldron, mixture and discovery persistence readers. The rewrite is
one-way and the next save uses canonical IDs.

## Verified seams and exclusions

`CocoaPowderRecipe` and Flint-from-Bowl move with Alchemy because they use
Alchemy-owned ingredients. Portable container policy belongs to TotemRemnant
and must not be copied here. Client/server payloads, criteria, game rules,
creative tab, advancement data and visual assets use the same canonical owner
path.

## Dependency and validation rules

The module may depend on TotemCore and Fabric API only. It must not import
DeadRecall or another feature implementation. Required validation includes
Java 25 unit tests, standalone Dedicated Server startup, cauldron
persistence/restart coverage, canonical recipe/data-pack loading, and an
explicit legacy decode-to-canonical rewrite test.
