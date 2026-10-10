# M11 Signature Brew Datapack Contract (schema_version 1)

Status: **passive recipe definition loading with a guarded manual committed-process extraction path**. A ready committed process can issue a registered drink through the server cauldron bottle interaction, but ordinary gameplay never auto-creates signature groups yet. Legacy Hot Cocoa/Cherry Brew scheduling remains unchanged. Crash-atomic inventory/cauldron persistence is a separate outstanding gate.

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

The committed process becomes ready only after **all** members finish. `claimSignatureResult(id)` is reserved for a whole-batch solid result, whereas `claimSignatureBottle(id)` consumes exactly one liquid unit for a bottled result. The guarded cauldron interaction now creates a real drink item from a ready bottled claim; durable persistence of the player reward and cauldron mutation remains **unimplemented**. Runtime signature-group scheduling and solid-result issuance are also not activated.

Partial extraction and mixing of mixtures containing committed processes are refused until safe allocation/reconciliation rules are defined. Full extraction moves the committed process intact.

## Three-unit signature bottling quota (M11-T02a, state-only boundary)

For a completed **bottled** signature with 3 liquid units:

1. `claimSignatureBottle(signatureId)` returns one immutable item result descriptor plus a detached, one-volume-unit mixture snapshot. The snapshot contains chemistry scaled to that single unit, has no outstanding reaction/group claims, and is sealed against further cooking.
2. The source loses exactly one liquid unit and a proportional share of conserved chemistry. It retains its ready signature process while units remain.
3. Repeated claims produce **3 bottles total**, then empty the source. A fourth claim fails. A saved-and-reloaded source preserves its remaining volume/quota. `claimSignatureResult` does not redeem bottled signatures.
4. The internal claim now feeds the **server-side cauldron right-click bottle path**. The cauldron first validates the requested container, resolved drink item and (optional) potion; it builds an item from a copy of the mixture, then commits the one-unit claim, updates the cauldron liquid level, and hands the output to the existing same-tick inventory exchange. A failed output validation consumes nothing. This is **not crash-atomic** across player inventory and chunk storage; a durable hand-off/receipt protocol is still an activation gate.
5. `claimSignatureResult` for `drop_item` consumes the **whole batch** once; it never converts liquid units into multiple solid outputs.
6. Until simultaneous process quotas are defined, claiming requires exactly one committed process and no outstanding uncommitted groups or pending reactions. A ready claim cannot bypass normal item grant using ordinary glass-bottle extraction.

This deliberately differs from the old one-time **whole-batch** claim behavior for bottled items. Old 3-unit mixtures are not migrated/changed; their legacy brewing and bottling still operate normally.

## Guarded server-side drink delivery (M11-T02a-delivery, pending CI)

- Only a **ready, committed** group with exactly one active group, no pending reactions and no other reserved groups may be bottled.
- Client-side preview and server-side interaction both recognize the configured `container_item`; a wrong container, unfinished reaction, unregistered item, or non-drinkable output is rejected **without decrementing liquid volume**.
- The cauldron constructs `SignatureBrewBottleOutput` using a copy of the mixture, preserving one dose of the existing liquid/effect chemistry. If `result.potion` is defined, that potion's configured effects are overlaid once onto the one-dose output without overwriting unrelated effects.
- Once the detached drink is prepared, one source liquid unit is consumed, `setChanged()` is invoked, the block's displayed level is reduced, and `AlchemyHandler` swaps the clicked container for the drink through its existing server-side handoff.
- **Durability limit:** this is a single synchronous server callback, not a distributed transaction spanning cauldron chunk data and player inventory. A crash between their independent disk writes can still duplicate or lose a reward. Final activation requires a persisted delivery receipt/escrow design and restart fault-injection tests; do not claim crash-safe exactly-once delivery yet.
- This guarded path handles already-committed states only. No built-in Hot Cocoa or Cherry Brew recipe is yet migrated to signature scheduling.

## Prepared delivery escrow (M11-T02a-durability-01; experimental, NOT live)

`SignatureBrewDeliveryTicket` is a new immutable, versioned `S1` ticket, carrying a stable UUID, signature recipe ID, result descriptor and a detached one-unit completed mixture. Invalid versions or payloads cannot be parsed as deliverable receipts. A ticket is **PREPARED**, not `DELIVERED`; a matching signature result on a ticket does not itself grant a player any item.

`AlchemyCauldronBlockEntity.prepareSignatureBottleDelivery(container)` is a **separate, not-yet-routed** preparation API. It validates a ready signature result and constructs a usable output from a working copy *before* simultaneously updating the cauldron's remaining liquid and retaining its one-dose pending ticket in the same block entity. Preparing another ticket or extracting ordinary/legacy mixtures while that ticket exists is refused. The receipt is saved under `signature_delivery_ticket` independent of `mixture_state`, so the final liquid unit survives even when the mixture becomes empty. A corrupt/unknown serialized ticket is preserved verbatim and locks the cauldron for explicit recovery; never silently discard a possibly spent output.

**This is not an exactly-once delivery protocol.** No ticket acknowledgment, player inventory ID matching, receipt transfer, payout retry, or escrow clearing method is implemented. In particular, calling the existing live `extractSignatureBottle` path does not yet use escrow. The new staging API is only exercised by isolated server GameTests. The current live hand-off remains synchronous but crash-unsafe; do not enable automatic SignatureBrew or advertise crash consistency until its handoff is replaced and reviewed.

Next durability gate: define an explicit recipient-bound delivery state machine with idempotent acknowledgment and independent chunk/player save recovery. Test crashes *before staging*, *after staging but before item issuance*, *after item issuance but before acknowledgment*, *after acknowledgment*, last-dose block replacement, retries, transfer/disconnect, and chunk unload. A simple ticket alone cannot make two independent save files atomic.

## Follow-up acceptance gates

1. Schedule an active group with committed result metadata; reject clashes before the first member completes.
2. Hold claimed outcomes until **all members** finish; do not pay out ordinary reaction results for claimed members.
3. Persist partially completed groups and their metadata through save/load, bottle/pour and server restart, without duplicate item/effect generation.
4. Verify heat requirements, failed outcomes and cancellation behavior.
5. Only then migrate legacy Hot Cocoa, and separately Cherry Brew, to the registry.
