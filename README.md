# TotemAlchemy

TotemAlchemy owns the Alchemy Cauldron, Pig Manure and Cherry Brew gameplay.
It depends on TotemCore and Fabric API only; it has no DeadRecall implementation
dependency.

`0.1.2` is the current Java 25 cutover-capable artifact. It preserves the legacy
`deadrecall:*` identifiers and is selected by DeadRecall only when installed in
the exact compatibility bundle. The old implementation remains source-visible
as a rollback path during the observation window. Dedicated-server GameTests
cover both current cauldron NBT round-trips and legacy `HOT_COCOA` state migration.

See [EXTRACTION.md](EXTRACTION.md) for the ownership, compatibility and
validation contract.
