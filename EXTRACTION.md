# Extraction contract

TotemAlchemy will own the Alchemy Cauldron block and block entity, the
`alchemy/**` interactions and recipes, related items, payloads, Mixins and data
resources. Existing `deadrecall:*` registry and recipe identifiers must remain
readable throughout the compatibility window.

Before code moves, inventory every server/client registration, resource,
GameTest, persistent identifier and direct dependency. The initial module must
start with Core alone; optional cross-feature behavior uses a versioned Core
event or consumer-owned adapter.
