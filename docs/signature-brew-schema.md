# M11 Signature Brew Datapack Contract (schema_version 1)

Status: **definition loading only**. This contract does not switch on special outcomes or change legacy Hot Cocoa/Cherry Brew gameplay. Runtime reservation, suppressing ordinary outcomes, atomic settlement, and save/restore across partially settled groups must be proven before signature outputs are activated.

## Resource location

Files live under `data/<namespace>/alchemy/signature_brews/<recipe>.json`.
A file `data/totem/alchemy/signature_brews/hot_cocoa.json` has ID `totem:alchemy/hot_cocoa`.

The parser rejects unknown fields, invalid IDs, duplicate ingredients, fractions outside (0, 1], liquid minimum sums above 1, invalid result settings, and unsupported schema versions. A bad file rejects the entire registry reload; previously loaded definitions remain intact. No registry data is hard-coded in Java.

## Example: Hot Cocoa (illustrative; not shipped as an active datapack)

```json
{
  "schema_version": 1,
  "priority": 20,
  "requires_heat": true,
  "liquids": {
    "minecraft:milk": 1.0
  },
  "ingredients": [
    "minecraft:sugar",
    "totem:alchemy/cocoa_powder"
  ],
  "result": {
    "type": "bottled_item",
    "item": "totem:alchemy/hot_cocoa",
    "count": 1,
    "container_item": "minecraft:glass_bottle",
    "potion": "totem:alchemy/saturation"
  }
}
```

All root fields are required. `result.count` defaults to 1. For `bottled_item`, `result.count` **must be 1**: each bottle consumes exactly one liquid volume unit; the number of bottles is determined by the mixture's volume, not `count`. `result.potion` is optional for bottled items, and `result.container_item` is mandatory for bottled items. For `drop_item`, `count` is the number of items yielded by a single whole-batch result (1–64), and `container_item` or `potion` are invalid.

## Semantics

- `liquids` expresses **minimum normalized liquid fractions**. A liquid is a condition, not a ticking ingredient reaction. Empty liquid requirements are allowed.
- `ingredients` lists distinct exact item IDs whose **pending** `AlchemyMixtureState.Reaction` instances must be available simultaneously; items that are already completed cannot be taken over. Tags, quantities, and ordered steps are deferred to later schema revisions.
- `requires_heat` is a mandatory boolean, to be evaluated by the eventual scheduler against the real cauldron's active heat state. The pure resolver currently only checks liquid and pending reaction conditions.
- `priority` determines competition when signatures share a pending reaction; ties break by signature ID string.
- When groups are actually activated, *each reaction ID belongs to at most one group*, completed group results are emitted once, and claimed ordinary outputs must never be applied in parallel.
- A reaction group carries **references to existing reaction IDs**. It does not introduce a second timer. The current `G|` reservation codec is inactive metadata until the atomic-settlement task is complete.
- On legacy completion, inactive reservations are cleared and the ordinary output remains unchanged. This is intentional: schema loading does not silently alter existing gameplay.
- No new signature definitions are bundled into gameplay data yet, preventing conflict with `alchemy/cauldron_recipes/hot_cocoa.json` and `cherry_brew.json`.

## Committed-group core (M11-T02a, pending CI)

An inactive reservation is saved as `G|` metadata and still allows ordinary brewing to resolve. It has **no settlement authority**.

A signature group may instead be explicitly committed with `AlchemyMixtureState.commitSignatureGroup(signatureId, result)` **before any member finishes**. The committed group uses the `Q|` save marker, storing its immutable result descriptor, member reaction IDs, and completed-member IDs. Existing reaction timers continue unchanged. When a committed member finishes, its ordinary effect/potion output and completed-stage bookkeeping are skipped; other unclaimed reactions retain normal behavior.

The committed process becomes ready only after **all** members finish. `claimSignatureResult(id)` removes a ready claim from the mixture and returns its output descriptor once. This does not itself create an item. The future cauldron/inventory adapter must persist the state change and grant the item in a single server-side transaction, or retry/restart could otherwise duplicate or lose an output. Runtime scheduler integration and atomic external item grant remain **not implemented**.

Partial extraction and mixing of mixtures containing committed processes are refused until safe allocation/reconciliation rules are defined. Full extraction moves the committed process intact.

## Three-unit signature bottling quota (M11-T02a, state-only boundary)

For a completed **bottled** signature with 3 liquid units:

1. `claimSignatureBottle(signatureId)` returns one immutable item result descriptor plus a detached, one-volume-unit mixture snapshot. The snapshot contains chemistry scaled to that single unit, has no outstanding reaction/group claims, and is sealed against further cooking.
2. The source loses exactly one liquid unit and a proportional share of conserved chemistry. It retains its ready signature process while units remain.
3. Repeated claims produce **3 bottles total**, then empty the source. A fourth claim fails. A saved-and-reloaded source preserves its remaining volume/quota. `claimSignatureResult` does not redeem bottled signatures.
4. No new potion/bottle items are physically handed to a player yet. The return value is an **internal claim**, not an inventory delivery. The eventual server interaction must ensure that consumption and item issuance cannot be replayed after a crash.
5. `claimSignatureResult` for `drop_item` consumes the **whole batch** once; it never converts liquid units into multiple solid outputs.
6. Until simultaneous process quotas are defined, claiming requires exactly one committed process and no outstanding uncommitted groups or pending reactions. A ready claim cannot bypass normal item grant using ordinary glass-bottle extraction.

This deliberately differs from the old one-time **whole-batch** claim behavior for bottled items. Old 3-unit mixtures are not migrated/changed; their legacy brewing and bottling still operate normally.

## Follow-up acceptance gates

1. Schedule an active group with committed result metadata; reject clashes before the first member completes.
2. Hold claimed outcomes until **all members** finish; do not pay out ordinary reaction results for claimed members.
3. Persist partially completed groups and their metadata through save/load, bottle/pour and server restart, without duplicate item/effect generation.
4. Verify heat requirements, failed outcomes and cancellation behavior.
5. Only then migrate legacy Hot Cocoa, and separately Cherry Brew, to the registry.
