# Modrinth gallery

The images in this directory are captured by `runClientGameTest` on Minecraft
26.2/Fabric with Java 25. They are source-controlled so the published gallery
has an auditable, reproducible source.

## Standalone Alchemy Creative tab

- File: `creative-showcase.png`
- Modrinth title: `Standalone Alchemy Creative tab`
- Modrinth description: `All eight Totem Alchemy items in the standalone Creative tab: saltpeter, pig manure, wood ash, cocoa powder, hot cocoa, cherry brew, stone bowl, and sulfur bowl. The legacy tab key remains for compatible worlds; standalone items use totem:alchemy/*. `
- 中文說明：獨立安裝時的創造模式頁籤，完整展示煉金材料、飲品與可重複使用的石缽。為相容既有世界，頁籤保留舊鍵值；實際物品採用 `totem:alchemy/*`。

Regenerate with:

```bash
JAVA_HOME=/path/to/java-25 xvfb-run -a ../TotemCore/gradlew runClientGameTest --no-daemon
```
