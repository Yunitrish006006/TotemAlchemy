# Modrinth gallery

The images in this directory are captured by `runClientGameTest` on Minecraft
26.2/Fabric with Java 25. They are source-controlled so the published gallery
has an auditable, reproducible source.

## Totem Alchemy Creative tab

- File: `totem-alchemy-creative-showcase.png`
- Modrinth title: `Totem Alchemy Creative tab`
- Modrinth description: `All nine Totem Alchemy items in the module-owned Creative tab: saltpeter, pig manure, wood ash, cocoa powder, hot cocoa, cherry brew, stone bowl, sulfur bowl, and the large potion flask.`
- 中文說明：Totem Alchemy 自有的創造模式頁籤，完整展示煉金材料、飲品、可重複使用的石缽與大型藥水瓶；實際物品採用 `totem:alchemy/*`。

Regenerate with:

```bash
JAVA_HOME=/path/to/java-25 xvfb-run -a ../TotemCore/gradlew runClientGameTest --no-daemon
```
