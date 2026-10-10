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

- [x] M10-T01 Move Minecraft base reactions into `Minecraft Alchemy`.
- [x] M10-T02 Move vanilla ingredient chemistry into `Minecraft Alchemy`.
- [x] M10-T03 Move Totem mushroom starter/base reactions into `Totem Alchemy`.
- [x] M10-T04 Move Totem-only ingredients into `Totem Alchemy`.
- [x] M10-T05 Move Totem reaction extensions into `Totem Alchemy`.
- [x] M10-T06 Move 48 Totem Brewing Stand recipes into `Totem Alchemy`.
- [x] M10-T07 Verify OFF/OFF contains no Totem recipe leakage. (Build #277: isolation GameTests and static content boundary passed; unrelated JUnit failures tracked separately.)
- [x] M10-T08 Verify all four pack combinations. (Build #280: OFF/OFF 0/0/0, ON/OFF 48/0/0, OFF/ON 0/1/50, ON/ON 48/1/50 for Totem brewing/Minecraft base/Minecraft ingredient; all four matrix GameTest launches passed. Pre-existing 44 JUnit failures remain separate.)

## M11 — Signature brews

Design contract (2026-10-10): SignatureBrew is a scheduling-time **reaction group**, not an after-the-fact potion conversion or a second ticking engine. The resolver matches the present liquid composition and **pending** individual material reactions; it returns a deterministic, non-overlapping reservation plan referencing existing reaction IDs. An ingredient reaction can belong to at most one chosen signature group. A completed ordinary reaction cannot be retrospectively stolen. Matching candidates sort by explicit priority, then stable resource ID; unmatched reactions continue normally. Scheduling must commit group membership before any owned member finishes, and group output must be settled **once** after all owned reactions finish, without also applying the owned ordinary outputs. Replanning after adding a material is permitted only for unfinished/uncommitted work and must preserve elapsed processing progress; incompatible or already-committed groups must not be silently displaced. Liquid components act as conditions, not consumed reaction IDs. Full group persistence, atomic settlement, pause/resume, split/merge and datapack schema are separate follow-up tasks and must precede gameplay activation.

- [x] M11-T01 Define `SignatureBrewResolver` as a pure scheduling-time planner with tests for pending-only matching, liquid requirements, deterministic ties, and non-overlap. (Build #282 compiled and executed 7 new unit tests; total JUnit 173 with unchanged 44 pre-existing failures.)
- [x] M11-T01a Persist uncommitted reaction-group reservations through mixture copies, serialization, and extraction; reject overlapping ownership and unsafe merges. (Build #287: 180 JUnit tests, unchanged 44 baseline failures; 7 new persistence tests passed, OFF/OFF and four-pack GameTests passed. Reservation is intentionally inert.)
- [x] M11-T02 Define data schema for signature conditions and group ownership/settlement metadata. (Schema v1 passive loader, `G|` reservations and `Q|` committed progress/result descriptors verified in Build #302. Schema/settlement tests added without increasing the pre-existing 44 JUnit failures.)
- [ ] M11-T02a Implement active-group settlement that suppresses ordinary member results and survives save/load/restart, then wire SignatureBrew to gameplay only after durability gates. (Build #302 verified committed-process `Q|` ledger and ordinary-output suppression. Build #336 verified synchronous server bottle hand-off, 3-unit quota and output effects: 83/83 GameTests, OFF/OFF and all four pack combinations passed; 198 JUnit tests with unchanged 44 baseline failures. **Crash-atomic inventory/chunk delivery and automatic scheduling remain NOT implemented.**)
- [x] M11-T02a-guard Prevent claimed materials from escaping through ordinary cauldron bottling, ingredient re-scheduling, legacy compound finalization, ordinary discovery, or overcook before signature result redemption. (Build #336: 83/83 server GameTests passed, including registry-backed ordinary fallback and discovery attribution; old 44 JUnit failures unchanged.)
- [x] M11-T02a-portions Redeem bottled signature output by 1 liquid unit per bottle; a ready 3-unit batch yields exactly 3 separately claimed bottles. Preserve proportionally scaled chemistry, save/reload remaining volume, disallow double claims and enforce bottled `result.count = 1`. (Build #336 validated three real server-side claims, intermediate save/reload and refusal of a fourth bottle. **Durable cross-object reward settlement remains pending.**)
- [x] M11-T02a-delivery Issue a real one-dose signature drink from a ready committed cauldron group, validating the input container and registered drink item **before** decrementing the source. Route through server right-click, update the liquid level, attach detached one-unit mixture and optional configured potion effects. (Build #336: 83/83 GameTests; synchronous transfer only, **not crash-atomic**. No auto-scheduling or legacy drink migration.)
- [ ] M11-T02a-durability Design and test crash-safe receipt/escrow for the two independently persisted state changes: player's output inventory and cauldron consumed units; audit network retries, disconnects, chunk unload, and server crashes before enabling SignatureBrew recipes in production.
- [x] M11-T02a-durability-01 Implement *prepared-only* receipt/escrow in the cauldron block entity. A versioned UUID ticket stores exactly one detached dose and its output descriptor alongside the debited mixture, including when its last unit is consumed; block concurrent preparation, ordinary extraction, and invalid ticket recovery. (Build #344: 87/87 GameTests, 203 JUnit with unchanged 44 baseline failures; OFF/OFF and four-pack matrix passed. Production handoff remains unconnected.)
- [x] M11-T02a-durability-02a Bind prepared tickets to a specific recipient UUID using versioned S2 serialization, preserve legacy S1 tickets as unbound/unclaimable, reject null or mismatched recipients without taking liquid, and restore ownership exactly through save/load. (Build #351: 89/89 server GameTests, OFF/OFF and all four pack states passed; 205 JUnit tests with unchanged 44 baseline failures. **Identity checks do not constitute payout acknowledgment or exactly-once delivery.**)
- [ ] M11-T02a-durability-02b Implement idempotent payout/acknowledgment tied to persisted player receipts, with explicit server-restart and chunk-destruction recovery. A cauldron-only ticket cannot make inventory/chunk storage atomic; test all crash windows before switching live right-click flow.
- [x] M11-T02a-durability-02b-01 Persist a per-ticket `J1` delivery progress journal alongside the escrowed dose, with `PREPARED` -> `ISSUANCE_UNCERTAIN` one-way attempt marking; quarantine mismatched/corrupted entries and old S2 entries lacking progress, never automatically reissue. (Build #358: 92/92 server GameTests, OFF/OFF and four pack states passed, 211 JUnit with unchanged 44 baseline failures. No runtime item issuance or acknowledgment enabled.)
- [ ] M11-T02a-durability-02b-02 Define a verifiable **player-side receipt** and durable write/reconciliation protocol for PREPARED, ambiguous issuance, confirmed delivery, crash rollback, chunk removal and repeated right-click; then wire the live path only after fault-injection acceptance.
- [x] M11-T02a-durability-02b-02a Attach a transaction-bound `R1` receipt marker to a **detached** prepared one-dose drink ItemStack; validate item identity, recipient, signature dose provenance and transaction ID, reject restamping and legacy S1 tickets. (Build #370: 98/98 main server GameTests, 221 JUnit with unchanged 44 baseline failures, OFF/OFF and four pack states passed. Item metadata remains nonauthoritative; never infer absence allows retry.)
- [ ] M11-T02a-durability-02b-02b Implement a **durably persisted player-side receipt ledger** that survives consumption, transfers and restart, and define an authoritative reconciliation protocol against the cauldron journal; never enable retry without an independently verified and crash-tested delivery outcome.
- [x] M11-T02a-durability-02b-02b-01 Record validated R1 reward observations under a separate **per-player keyed world SavedData**, independent of drink ItemStacks; support idempotent duplicate recognition, preserve malformed/conflicting evidence, reject cross-player attribution, and round-trip the actual SavedData codec. (Build #370: 98/98 main server GameTests and six focused JUnit tests passed without exceeding 44 baseline failures. **Observed item != confirmed payout**; world SavedData is not player inventory persistence, no live issuance connection.)
- [ ] M11-T02a-durability-02b-02b-02 Specify and implement the authoritative recipient-side inventory receipt/acknowledgment and its durable commit/recovery ordering, including crash after every save boundary, chunk deletion and player disconnect. No retries or ACKNOWLEDGED status until independently proven safe.
- [ ] M11-T02a-durability-02b-02b-02a Build a **read-only three-store recovery assessment** comparing S2 cauldron ticket, J1 issuance journal and full R1 saved player observation (not UUID alone). Enumerate four independent save orderings, missing last-dose block, absent journal, wrong recipient, mismatched receipt payload, corrupted/foreign ledger and unverified ACKNOWLEDGED. All findings require independent review; none permit payout, deletion or acknowledgment. (Implementation with 8 JUnit and 3 GameTests submitted; awaiting CI. **Not crash-atomic, not wired to gameplay**.)
- [ ] M11-T02a-durability-02b-02b-02b Implement authoritative recipient-owned receipt storage and crash-safe transaction coordinator/recovery. A matching R1 **observation** is not the inventory's durable receipt; choose the single source of truth and verify write ordering under process-kill/rollback/chunk removal tests before any automatic action.
- [x] M11-T02a-durability-02b-02b-02c Introduce **experimental world-level canonical escrow registration** for the S2 reward snapshot, transaction UUID, recipient, origin dimension and packed BlockPos, isolated from cauldron chunks and player data. Reject repeated transaction IDs and source reuse; retain malformed/colliding records and preserve the complete reward after the last-dose cauldron is removed. (Build #397: 106/106 server GameTests, 244 JUnit with unchanged 44 known baseline failures, OFF/OFF and all four pack matrix states passed. Registration remains independent of live brewing.)
- [ ] M11-T02a-durability-02b-02b-02d Define an authoritative **single-writer transaction coordinator** and persisted tombstone/finalization rules before enabling source reuse; test write ordering for global registry vs cauldron debit vs player receipt, including real server termination and filesystem rollback. Only transition from the experimental registry to production after this gate.
- [x] M11-T02a-durability-02b-02b-02d-01 Persist a **review-only C1 closure intent** keyed to the original A1 transaction, recipient, source dimension/position and SHA-256 fingerprint of the exact S2 escrow. Idempotent requests and save/reload do not release a source; corrupted/mismatching/future review data is preserved and quarantined. (Build #397: 106/106 GameTests, 244 JUnit with unchanged 44 baseline failures. No recipient receipt verification or source-release API exists.)
- [ ] M11-T02a-durability-02b-02b-02d-02 Introduce independently proven **terminal grant/closure evidence**, a durable source lease/generation or tombstone, and restart-tested conditions to release a source only after the authoritative payout coordinator has committed the recipient receipt. A C1 review request alone must never release the source.
- [ ] M11-T02a-durability-02b-02b-02d-02a Add a **read-only closure preflight across A1/C1/J1/R1** and expose it from world SavedData. Require exact origin, full canonical S2 payload, C1 fingerprint, J1 ownership and R1 audit match; classify inconsistent/missing/uncertain states separately. No finding authorizes item payout, ACKNOWLEDGED or source release. (Implemented with 8 JUnit + 2 server GameTests, latest CI pending. This is a fencing gate, **not terminal proof**.)
- [ ] M11-T02a-durability-02b-02b-02d-02b Implement recipient-owned authoritative durable grant receipt, single-writer/fenced generation and terminal tombstone; support subsequent batches at the same origin only after fault-injected save ordering / process kill tests prove it safe.
- [ ] M11-T02b Define safe replan/merge semantics for active groups (including compatibility, component splits, and cancellation).
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