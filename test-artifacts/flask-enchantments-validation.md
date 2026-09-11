# Flask enchantments — local validation, 2026-09-11

Candidate: TotemAlchemy 0.1.50, Minecraft 26.2, Fabric API 0.154.2+26.2, TotemCore 0.7.18, Java 25.

- Unit suite: 25 tests, zero failures/errors.
- Dedicated-server GameTests: all 41 required tests passed, including four new flask tests and the existing portable-container regressions.
- Native enchantment selection finds both new enchantments. Capacity I–V yields 4–8 doses; eight-dose save/copy/drink preserves volume and concentration.
- Repeated Bottomless drinks apply effects and preserve the mixture; an empty bottle stays empty. Pouring still transfers and debits up to the cauldron’s three-dose capacity.
- Partial refill preserves excess source liquid, item name and enchantments. Removing Capacity preserves existing stored liquid. Empty filling preserves normalized source metadata, stability, delivery form and potency.
- The manual test uses the current canonical advancement ID `totem:alchemy/alchemy_manual`.
- Production client probe: `AlchemyLargeFlaskVisualGameTest` passed with the released-format JAR and official Fabric API, exercising registry/resource reload plus real server-to-client item synchronization in English and Traditional Chinese.
- Screenshots: `screenshots/flask-enchantments/flask-enchantments-en_us.png` and `flask-enchantments-zh_tw.png`. Native inventory, font, enchantment lines and amount bar; reviewed for readability and clipping. No new pixel assets.
- Independent read-only review: `/root/observer_publisher_review`, passed after the full-state empty-fill regression was added; no remaining correctness findings.
- TotemWorkspace impact: Alchemy, Enchanting and their existing Core dependency. No shared API or production Screen changes; existing native item/menu Observer representations remain applicable.

Commands used from TotemAlchemy with Java 25:

```sh
../TotemCore/gradlew test build runGameTest --no-daemon
../TotemCore/gradlew -I /tmp/alchemy-flask-production.init.gradle runFlaskProductionClientGameTest --no-daemon
```

Logs: `/tmp/alchemy-flask-enchantments-final.log`, `/tmp/alchemy-flask-production.log`.
JAR SHA-256: `c482c912769079aaa0515c9f3d63707b57d9da19883c79080e4b21f02da22676`.
Validation preceded publication. Verified release details are recorded in `.github/staging/modrinth-published-0.1.50.json`.
