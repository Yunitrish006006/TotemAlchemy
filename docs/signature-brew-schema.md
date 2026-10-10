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

`SignatureBrewDeliveryTicket` is an immutable, versioned ticket containing a stable transaction UUID, signature recipe ID, result descriptor, and detached one-unit completed mixture. **New `S2` tickets also bind an explicit recipient player UUID.** A null recipient cannot create a ticket or debit any liquid. `pendingSignatureDeliveryFor(recipientId)` reveals the prepared record only when the candidate UUID matches. This is only an identity check; it is **not proof of payout**. Existing `S1` tickets can still be decoded and serialized without mutation, but have no recipient and cannot authorize payout. Malformed `S2` owner fields and unknown versions are rejected. A ticket is **PREPARED**, not `DELIVERED`; matching a recipe/UUID does not grant an item.

`AlchemyCauldronBlockEntity.prepareSignatureBottleDelivery(container, recipientId)` is a **separate, not-yet-routed** preparation API. It validates a ready signature result and constructs a usable output from a working copy *before* simultaneously updating the cauldron's remaining liquid and retaining its one-dose pending ticket in the same block entity. Preparing another ticket or extracting ordinary/legacy mixtures while that ticket exists is refused. The receipt is saved under `signature_delivery_ticket` independent of `mixture_state`, so the final liquid unit survives even when the mixture becomes empty. A corrupt/unknown serialized ticket is preserved verbatim and locks the cauldron for explicit recovery; never silently discard a possibly spent output.

**This is not an exactly-once delivery protocol.** No ticket acknowledgment, independently persisted inventory receipt comparison, receipt transfer, payout retry, or escrow clearing method is implemented. In particular, calling the existing live `extractSignatureBottle` path does not yet use escrow. The new staging API is only exercised by isolated server GameTests. The current live hand-off remains synchronous but crash-unsafe; do not enable automatic SignatureBrew or advertise crash consistency until its handoff is replaced and reviewed.

Next durability gate: add idempotent payout acknowledgment and independent chunk/player save recovery to the now recipient-bound ticket. Test crashes *before staging*, *after staging but before item issuance*, *after item issuance but before acknowledgment*, *after acknowledgment*, last-dose block replacement, retries, transfer/disconnect, and chunk unload. A simple ticket alone cannot make two independent save files atomic.

## Delivery attempt journal (M11-T02a-durability-02b-01, isolated checkpoint)

`SignatureBrewDeliveryProgress` is an additional `J1|transaction_uuid|recipient_uuid|phase` record in the **same cauldron block entity** as its prepared `S2` ticket and debited liquid. The journal's IDs must exactly match the prepared ticket.

| State | Meaning | Safe automatic replay? |
| --- | --- | --- |
| `PREPARED` | Reward was escrowed, but item issuance has not been attempted | **No automatic payout enabled** |
| `ISSUANCE_UNCERTAIN` | A delivery attempt may already have transferred the item | **Never**; compare an independently persisted player receipt |
| `ACKNOWLEDGED` | Reserved future terminal state after receipt reconciliation | No implementation yet |

The only new transition method is `markSignatureDeliveryAttempt(transactionId, recipientId)`. It requires a matching ticket and **PREPARED** journal, changes the state to **ISSUANCE_UNCERTAIN** once, and rejects subsequent attempts. It does **not** grant a drink. A missing progress record on an older `S2` prepared ticket is treated as **ISSUANCE_UNCERTAIN**, not as a fresh reward. Malformed or mismatched journals are preserved in the original serialized form and lock the cauldron. Older `S1` ownerless tickets remain quarantined without an active journal.

This is deliberately **not** crash-safe delivery: `setChanged()` is only an in-memory dirty flag, not a guarantee that the journal was durably flushed to disk before a future inventory mutation. In addition to matching the player receipt, production must define an ordering / recovery protocol that handles write reordering, chunk unload/destruction, save rollback, server kill at each transition, and the final-dose block replacement. No runtime right-click route currently invokes this journal; the existing delivery path stays unchanged.

## Prepared reward item receipt (M11-T02a-durability-02b-02a, isolated)

A future payout will need independent evidence of *which* prepared transaction reached the player. `SignatureBrewRewardReceipt` defines `R1|transaction_uuid|recipient_uuid|signature_id|output_item_id`, embedded in the detached output ItemStack's `CUSTOM_DATA.totem_alchemy_signature_receipt`. `SignatureBrewBottleOutput.createWithReceipt(ticket)` builds the prepared drink and stamps this marker without touching the cauldron or a player inventory. The returned item still carries its existing one-dose `totem_alchemy_mixture_state` and potion presentation.

Validation rejects wrong item IDs, missing or mismatched signature provenance, pending or claimed group metadata in the dose, stacked rewards, invalid records, and restamping an item that already has a receipt. An ownerless legacy S1 ticket cannot produce an R1-marked drink. The receipt ID must exactly match the pending S2 ticket, but this comparison is **read-only** and does not grant a second item.

**Security limitation:** an ItemStack tag is *not* a tamper-proof or permanent delivery receipt. Players may consume, move, delete or duplicate item stacks; a missing matching item does not mean no payout happened. The R1 marker is therefore only an audit aid for future player-ledger reconciliation, **not** authorization to release the escrow, retry an uncertain attempt, or declare ACKNOWLEDGED. No current live right-click path uses signed output.

Next checkpoint: design and persist an independent player-side receipt ledger that survives item consumption and restart; reconcile with the cauldron's J1 state while testing all crash/order windows, including when a chunk no longer exists.

## Independent player receipt observations (M11-T02a-durability-02b-02b-01, experimental)

`SignatureBrewPlayerReceiptSavedData` is a **separate world SavedData file** (`totem:alchemy/signature_receipt_observations`) keyed internally by a player's UUID and transaction UUID. It stores `SignatureBrewRewardReceipt` R1 records independently of drink ItemStacks. It is not the player's `player.dat` inventory file and does not prove that player's inventory changes have been flushed to disk.

The server-only `observe(playerUuid, pendingTicket, actualRewardItem)` verifies the physical reward's R1 marker and one-unit signature chemistry against the exact recipient-bound S2 escrow ticket. Identical repeat observations are idempotent. Wrong owners and different tickets are refused. A reused transaction UUID with conflicting owners/payloads is quarantined; corrupted/future serialized records are preserved and the ledger becomes untrusted, rather than silently discarding evidence. The SavedData codec round-trips the observations so they remain accessible even if a specific drink is consumed, deleted, or transferred.

**No payouts, retries, or ACKNOWLEDGED transitions are authorized by this ledger.** An `OBSERVED` entry only means the server examined a marked item at some time. An absent record might mean crash-before-save, item never delivered, or observation never committed. A present record might predate a lost inventory write. Neither state proves the inventory and cauldron have been committed together. The API is currently used by isolated tests only, not by the live right-click handoff.

Next durability gate: introduce a transaction coordinator with a **durable write ordering/recovery contract**, plus a recipient-owned independent receipt acknowledged through the inventory-save lifecycle; test save-file rollback, process kill before/after each write, last-dose block replacement, chunks unloaded or destroyed, player logoff/transfer, and repeated interactions. Until then, fail closed rather than issuing a second drink.

## Three-store crash assessment (M11-T02a-durability-02b-02b-02a)

`SignatureBrewRecoveryAssessment` is a **pure, read-only, non-authoritative** evaluation of three independently serialized objects: (1) an S2 cauldron delivery ticket, (2) its J1 journal, and (3) an R1 player receipt *observation* from `SignatureBrewPlayerReceiptSavedData`. The caller supplies the independently expected transaction UUID and recipient UUID, rather than guessing identity from item contents.

The R1 comparison now checks the **entire** recipe/recipient/output payload, not just matching transaction UUID. Different recipients, contradictory entries, unexpected item or recipe IDs, future/invalid records and missing data yield distinct risk findings.

Independent restart boundaries that must remain blocked include:

| Loaded cauldron/J1 | Loaded player observation | Risk classification |
| --- | --- | --- |
| PREPARED | Absent | Prepared, no evidence of payout |
| PREPARED | Present and matching | Prepared but item previously observed; journal might have rolled back |
| ISSUANCE_UNCERTAIN | Absent | Delivery may already have occurred before ledger save |
| ISSUANCE_UNCERTAIN | Present and matching | Delivery was observed but independently persisted inventory is unproven |
| Missing/removed last-dose cauldron | Either | Escrow missing; no safe reconstruction from R1 |
| Mismatched/absent J1, wrong recipient, conflicting or unknown ledger | Either | Manual investigation required |
| ACKNOWLEDGED label without authoritative inventory proof | Either | Still unverified; never trusted for settlement |

Every decision exposes explicit `allowsAutomaticPayout() = false`, `allowsAutomaticAcknowledgment() = false`, and `allowsAutomaticEscrowDeletion() = false`. This checkpoint classifies snapshots only and **never edits player inventory, chunks, ledgers, or escrow**. Unit and server GameTests simulate different saved snapshots by serializing the cauldron and receipt ledger separately, including a removed last-dose cauldron. This is *not* fault injection into the filesystem and does not establish crash-atomic exactly-once delivery.

**Next gate:** choose an authoritative, durable, recipient-owned receipt/transaction mechanism, implement correct save ordering or a provable replayable single-source-of-truth transaction, and exercise real process kills, incomplete writes, chunk removal, player logout and rollback before changing production right-click behavior.

## Follow-up acceptance gates

1. Schedule an active group with committed result metadata; reject clashes before the first member completes.
2. Hold claimed outcomes until **all members** finish; do not pay out ordinary reaction results for claimed members.
3. Persist partially completed groups and their metadata through save/load, bottle/pour and server restart, without duplicate item/effect generation.
4. Verify heat requirements, failed outcomes and cancellation behavior.
5. Only then migrate legacy Hot Cocoa, and separately Cherry Brew, to the registry.
