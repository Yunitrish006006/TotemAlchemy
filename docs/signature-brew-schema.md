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

All root fields are required. `result.count` defaults to 1. `result.potion` is optional for bottled items, and `result.container_item` is mandatory for bottled items. `drop_item` results cannot specify `container_item` or `potion`.

## Semantics

- `liquids` expresses **minimum normalized liquid fractions**. A liquid is a condition, not a ticking ingredient reaction. Empty liquid requirements are allowed.
- `ingredients` lists distinct exact item IDs whose **pending** `AlchemyMixtureState.Reaction` instances must be available simultaneously; items that are already completed cannot be taken over. Tags, quantities, and ordered steps are deferred to later schema revisions.
- `requires_heat` is a mandatory boolean, to be evaluated by the eventual scheduler against the real cauldron's active heat state. The pure resolver currently only checks liquid and pending reaction conditions.
- `priority` determines competition when signatures share a pending reaction; ties break by signature ID string.
- When groups are actually activated, *each reaction ID belongs to at most one group*, completed group results are emitted once, and claimed ordinary outputs must never be applied in parallel.
- A reaction group carries **references to existing reaction IDs**. It does not introduce a second timer. The current `G|` reservation codec is inactive metadata until the atomic-settlement task is complete.
- On legacy completion, inactive reservations are cleared and the ordinary output remains unchanged. This is intentional: schema loading does not silently alter existing gameplay.
- No new signature definitions are bundled into gameplay data yet, preventing conflict with `alchemy/cauldron_recipes/hot_cocoa.json` and `cherry_brew.json`.

## Follow-up acceptance gates

1. Schedule an active group with committed result metadata; reject clashes before the first member completes.
2. Hold claimed outcomes until **all members** finish; do not pay out ordinary reaction results for claimed members.
3. Persist partially completed groups and their metadata through save/load, bottle/pour and server restart, without duplicate item/effect generation.
4. Verify heat requirements, failed outcomes and cancellation behavior.
5. Only then migrate legacy Hot Cocoa, and separately Cherry Brew, to the registry.
