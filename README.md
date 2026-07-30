# TotemAlchemy

TotemAlchemy 是 Totem 系列的生存煉金模組，包含豬糞、缽、木灰、硝石、
火藥、熱可可、櫻花釀與資料驅動煉藥鍋。

目前版本為 **0.1.5**，精確搭配 TotemCore **0.2.0**。

## 安裝

Client 與 Server 都放入：

1. Fabric API `0.154.2+26.2`
2. TotemCore `0.2.0`
3. TotemAlchemy `0.1.5`

| 項目 | 需求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Java | 25+ |
| 必要 Totem 模組 | `totem-core =0.2.0` |

Alchemy 不依賴其他功能模組。使用 DeadRecall 2.4.5 整合 JAR 時不要再
安裝獨立 TotemAlchemy。

## 入門材料

### 缽

工作台：

```text
S F S
_ S _
```

`S` 可使用石頭、鵝卵石、安山岩、閃長岩、花崗岩、黑石或深板岩碎石；
`F` 是燧石。

缽的常用配方：

- 缽 + 可可豆 + 糖 → 裝可可粉的缽。
- 缽 + 礫石 → 燧石，缽會返還。
- 缽右鍵硫磺方塊 → 帶硫磺的缽；方塊會被消耗。

### 豬糞

對可正常餵食的豬餵食成功後，牠會在約兩秒後於腳下留下豬糞。同一格
可像雪一樣堆到八層；使用鏟子收集，每層得到一份。

豬糞可以投擲，被命中的生物會得到「臭味」效果並使附近友善生物遠離。

### 木灰與火藥

- 熔煉乾草捆可取得木灰。
- 帶硫磺的缽 + 硝石 + 煤炭或木炭，可無序合成 4 個火藥。

## 煉藥鍋教學

煉金流程使用原版煉藥鍋，並在下方放置點燃的營火。依配方右鍵投入
材料；標記為 dropped-only 的材料要丟進鍋內。烹煮完成會播放提示音。

### 硝石

1. 將煉藥鍋裝滿三層水，下方放點燃營火。
2. 投入木灰、紅色或棕色蘑菇、豬糞。
3. 每份材料各烹煮 200 ticks，並消耗一層水。
4. 完成後鍋旁會產生一份硝石。

### 熱可可

1. 在空煉藥鍋下放點燃營火。
2. 用牛奶桶右鍵開始，空桶會返還。
3. 加入裝可可粉的缽，取得空缽。
4. 將糖丟入鍋內。
5. 等待 200 ticks，再用玻璃瓶裝取熱可可。

### 櫻花釀

1. 使用三層滿水煉藥鍋與點燃營火。
2. 加入糖、櫻花樹葉、發光莓與甜莓。
3. 等待 240 ticks。
4. 用玻璃瓶裝取櫻花釀。

## Data Pack 擴充

煉藥鍋配方位於：

```text
data/<namespace>/deadrecall/cauldron_recipes/*.json
```

內建範例在
[`src/main/resources/data/deadrecall/deadrecall/cauldron_recipes/`](src/main/resources/data/deadrecall/deadrecall/cauldron_recipes/)。
格式可定義起始鍋狀態、初始水位、營火需求、逐材料／全投入後烹煮、
ticks、容器返還、完成音效與掉落／裝瓶結果。

## 舊世界相容

新取得的八個煉金物品使用 `totem:alchemy/<物品名稱>` canonical ID，例如
`totem:alchemy/stone_bowl` 與 `totem:alchemy/cherry_brew`。模組仍註冊全部
既有 `deadrecall:*` IDs，並能讀取舊版煉藥鍋 NBT，包括舊 `HOT_COCOA`
狀態。

合成、硫磺缽 remainder 與煉藥鍋材料入口同時接受新舊物品，結果只產生
canonical ID；未參與轉換流程的舊飲品、豬糞與材料仍保持原功能，不會被
啟動掃描改寫。升級前請先備份世界；不要同時安裝會註冊相同 mod ID 的
DeadRecall 整合 JAR。

## 開發與驗證

```bash
./gradlew build
```

0.1.5 已通過 11/11 required Dedicated Server GameTests，包含 cauldron NBT
round-trip、legacy migration、豬糞堆疊與 Flint-from-Bowl。所有權與
驗證契約見 [EXTRACTION.md](EXTRACTION.md)。
