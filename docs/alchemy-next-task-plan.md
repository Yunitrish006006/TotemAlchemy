# TotemAlchemy Next — GPT Implementation Task Plan

Status: implementation baseline for the 26.3 chemistry rewrite.

## Task rules

Each task should:
- change one primary concept;
- keep the branch compiling;
- add or update focused tests;
- avoid unrelated cleanup;
- preserve existing gameplay unless the task explicitly changes it.

Statuses: `TODO`, `IN PROGRESS`, `DONE`, `BLOCKED`.

## M0 — Prerequisites and Vanilla safety

- [x] M0-T00 Repair `feature/all-mushrooms-starter` CI without dropping `#c:mushrooms` starter support. (separate PR #29)
- [x] M0-T01 Add explicit vanilla Brewing Stand regression coverage for Water + Nether Wart.
- [x] M0-T02 Add explicit vanilla Brewing Stand regression coverage for Awkward + Sugar.
- [x] M0-T03 Add regression coverage for Redstone/Glowstone vanilla modifiers.
- [x] M0-T04 Add regression coverage for Gunpowder/Dragon Breath delivery conversion.
- [x] M0-T05 Add regression coverage that vanilla recipes do not randomly fail when Totem packs are inactive.
- [x] M0-T06 Add one aggregate OFF/OFF vanilla-safety GameTest.

## M1 — Optional built-in datapack boundary

- [x] M1-T01 Register empty `Totem Alchemy` built-in datapack.
- [x] M1-T02 Register empty `Minecraft Alchemy` built-in datapack.
- [x] M1-T03 Add pack metadata and stable pack identifiers.
- [x] M1-T04 Add enabled-pack detection/state exposed to server-side alchemy code.
- [x] M1-T05 Add reload lifecycle handling for pack-state changes.
- [x] M1-T06 Add OFF/OFF pack-state tests.
- [x] M1-T07 Add ON/OFF, OFF/ON and ON/ON pack-state tests.
- [x] M1-T08 Move no gameplay data yet; verify registration is behavior-neutral.

## M2 — Reaction data as single authority

- [x] M2-T01 Define `BaseReaction` data model.
- [x] M2-T02 Define `IngredientReaction` data model.
- [x] M2-T03 Define `ReactionOutcome` with chance and priority.
- [x] M2-T04 Add JSON loaders and resource IDs.
- [x] M2-T05 Build indexed lookup by base + ingredient.
- [x] M2-T06 Add schema validation and useful reload errors.
- [x] M2-T07 Read processing time from material/reaction data.
- [x] M2-T08 Read reaction success chance from reaction data.
- [x] M2-T09 Read outcome chance from reaction data.
- [x] M2-T10 Convert `MultiOutcomeBrewing` to registry-backed outcomes.
- [x] M2-T11 Remove Java hard-coded outcome pools.
- [x] M2-T12 Convert `VanillaBrewingChance` to resolver-backed chance data.
- [x] M2-T13 Remove Java hard-coded ingredient chance table.
- [x] M2-T14 Add reaction-extension merge semantics for Totem-on-Minecraft additions.
- [x] M2-T15 Adapt Manual/Discovery readers without changing UI.
- [x] M2-T16 Full reaction-registry regression pass.

## M3 — Liquid composition model

- [x] M3-T01 Add `LiquidComposition` value object.
- [x] M3-T02 Add normalize/epsilon/deterministic ordering.
- [x] M3-T03 Store composition in `AlchemyMixtureState`.
- [x] M3-T04 Preserve composition through copy/reset.
- [x] M3-T05 Volume-weight composition in `mergeFrom`.
- [x] M3-T06 Preserve ratios through `extractUnits`.
- [x] M3-T07 Add deterministic `L|` serialization.
- [x] M3-T08 Decode `L|` and migrate legacy non-empty mixtures to Water.
- [x] M3-T09 Import vanilla potion containers as Water 100%.
- [x] M3-T10 Liquid-composition regression pass with gameplay unchanged.

## M4 — Activated base composition

- [x] M4-T01 Add `ActivatedBaseComposition` value object.
- [x] M4-T02 Store base units in `AlchemyMixtureState`.
- [x] M4-T03 Derive total activated units and base concentration.
- [x] M4-T04 Preserve base units through copy/reset.
- [x] M4-T05 Conserve base units through merge.
- [x] M4-T06 Split base units proportionally through extract.
- [x] M4-T07 Add deterministic `A|` serialization.
- [x] M4-T08 Migrate legacy `baseActivated` states.
- [x] M4-T09 Make `baseActivated` a compatibility-derived value.
- [x] M4-T10 Base-composition regression pass without changing ingredient yield.

## M5 — Base activation and ingredient dose

- [x] M5-T01 Resolve starter reactions against unactivated units.
- [x] M5-T02 Prevent dilution from creating activated base.
- [x] M5-T03 Audit `completedStages` and existing reaction-capacity semantics.
- [x] M5-T04 Define reaction-capacity consumption/transformation rules.
- [x] M5-T05 Add `dose` to pending reaction state.
- [x] M5-T06 Repeated same ingredient increments dose instead of being rejected.
- [x] M5-T07 Add `max_dose` and reject excess dose.
- [x] M5-T08 Apply base concentration to produced effect quantity.
- [x] M5-T09 Test 33% base with dose 1/2/3.
- [x] M5-T10 Test that the same base cannot generate unlimited repeated dose.

## M6 — EffectDose and concentration

- [x] M6-T01 Make canonical EffectDose quantity explicit.
- [x] M6-T02 Define standard dose lookup.
- [x] M6-T03 Derive effect concentration from EffectDose + volume.
- [x] M6-T04 Implement sustained-effect potency/duration split.
- [x] M6-T05 Add configurable bias, default 0.5.
- [x] M6-T06 Implement instant-effect concentration rule.
- [x] M6-T07 Verify dilution conserves total EffectDose.
- [x] M6-T08 Verify merge/extract conserve EffectDose.
- [x] M6-T09 Add concentration cap behavior for normal recipes.
- [x] M6-T10 EffectDose regression pass.

## M7 — Station resolvers

- [x] M7-T01 Define station context/API.
- [x] M7-T02 Centralize final success-chance calculation.
- [ ] M7-T03 Add Brewing Stand station success bonus. (Policy hook exists; current bonus remains 0 until balance is chosen.)
- [x] M7-T04 Guarantee native vanilla Brewing Stand recipes reach 100%.
- [x] M7-T05 Brewing Stand selects deterministic highest-chance outcome.
- [x] M7-T06 Implement chance/priority/resource-ID tie break.
- [x] M7-T07 Cauldron keeps full probabilistic outcome resolution.
- [x] M7-T08 Brewing Stand emits standard dose/concentration.
- [x] M7-T09 Cross-station parity test for standard primary outcomes.
- [x] M7-T10 Remove obsolete direct Brewing Stand chance/outcome paths.

## M8 — Liquid registry and properties

- [x] M8-T01 Define `AlchemyLiquid` / `LiquidProperties`.
- [x] M8-T02 Register Water baseline.
- [x] M8-T03 Register Milk.
- [x] M8-T04 Register Honey.
- [x] M8-T05 Weighted property resolution for mixed liquids.
- [x] M8-T06 Apply stability modifier.
- [x] M8-T07 Apply reaction-speed modifier.
- [x] M8-T08 Apply duration/potency modifiers.
- [x] M8-T09 Liquid-property regression pass.

## M9 — Container adapters

- [x] M9-T01 Define ItemStack ↔ liquid adapter boundary.
- [x] M9-T02 Water Bottle adapter.
- [x] M9-T03 Water Bucket adapter.
- [x] M9-T04 Milk Bucket adapter.
- [x] M9-T05 Honey Bottle adapter.
- [x] M9-T06 Large Flask full-mixture adapter.
- [x] M9-T07 Potion bottle import/export compatibility.
- [x] M9-T08 Mixed-liquid cauldron fill/extract tests.

## M10 — Datapack content migration

- [ ] M10-T01 Move Minecraft base reactions into `Minecraft Alchemy`.
- [ ] M10-T02 Move vanilla ingredient chemistry into `Minecraft Alchemy`.
- [ ] M10-T03 Move Totem mushroom starter/base reactions into `Totem Alchemy`.
- [ ] M10-T04 Move Totem-only ingredients into `Totem Alchemy`.
- [ ] M10-T05 Move Totem reaction extensions into `Totem Alchemy`.
- [ ] M10-T06 Move 48 Totem Brewing Stand recipes into `Totem Alchemy`.
- [ ] M10-T07 Verify OFF/OFF contains no Totem recipe leakage.
- [ ] M10-T08 Verify all four pack combinations.

## M11 — Signature brews

- [ ] M11-T01 Define `SignatureBrewResolver`.
- [ ] M11-T02 Define data schema for signature conditions.
- [ ] M11-T03 Rebuild Hot Cocoa using Milk + Cocoa + Sugar + Heat.
- [ ] M11-T04 Preserve full Hot Cocoa mixture state through bottling/pouring.
- [ ] M11-T05 Rebuild Cherry Brew on generic mixture state.
- [ ] M11-T06 Add composition-sensitive Cherry Brew variants only after base version is stable.
- [ ] M11-T07 Signature-brew regression pass.

## M12 — Non-potion and nonlinear liquid reactions

- [ ] M12-T01 Keep Saltpeter functional as a non-potion alchemy process.
- [ ] M12-T02 Define `LiquidReactionResolver` override path.
- [ ] M12-T03 Add first nonlinear Water + Lava reaction.
- [ ] M12-T04 Verify nonlinear reactions can alter volume/stability without breaking conservation elsewhere.

## M13 — Redstone/Glowstone bias

- [ ] M13-T01 Represent effect-distribution bias in reaction/effect state.
- [ ] M13-T02 Redstone shifts bias toward duration.
- [ ] M13-T03 Glowstone shifts bias toward potency.
- [ ] M13-T04 Verify modifiers conserve EffectDose.
- [ ] M13-T05 Cross-check Brewing Stand standard potion parity.

## M14 — HUD, tooltip, Manual and Discovery

- [ ] M14-T01 Cauldron HUD liquid composition.
- [ ] M14-T02 Cauldron HUD base concentration.
- [ ] M14-T03 HUD reaction dose/progress.
- [ ] M14-T04 Bottle/flask liquid/base tooltip.
- [ ] M14-T05 Continuous potency/duration tooltip.
- [ ] M14-T06 Manual reads reaction registry.
- [ ] M14-T07 Discovery records resolved datapack reactions.
- [ ] M14-T08 Multiplayer sync/regression tests.

## M15 — Cleanup and release gate

- [ ] M15-T01 Remove obsolete hard-coded reaction maps.
- [ ] M15-T02 Remove obsolete chance tables.
- [ ] M15-T03 Remove obsolete compound special cases replaced by signature brews.
- [ ] M15-T04 Remove legacy `baseActivated` writes.
- [ ] M15-T05 Validate deterministic save output.
- [ ] M15-T06 Run all server/client GameTests.
- [ ] M15-T07 Validate all four datapack combinations.
- [ ] M15-T08 Update README/manual architecture docs.
- [ ] M15-T09 Final save-migration fixture pass.
- [ ] M15-T10 Release readiness review.

## Current execution order

1. Finish M0-T00 on PR #29.
2. Start M1-T01/T02/T03 on `feature/alchemy-next-foundation`.
3. Add M0 vanilla baseline tests once the optional pack boundary exists.
4. Continue M1 pack-state detection, then M2 reaction authority.