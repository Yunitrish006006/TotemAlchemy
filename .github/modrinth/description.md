# TotemAlchemy

**Experiment with uncertain brewing, discover your own potion effects, and learn to judge a cauldron's heat.**

TotemAlchemy expands Minecraft survival through familiar Brewing Stands, cauldrons, ingredients, and books. This guide describes **0.1.49**: an alchemy system where materials, research, and timing matter.

## Brewing and research

- Brewing ingredients have different success rates. Successful effect ingredients roll their possible effects independently, so one batch can produce several effects together. Compatible bottles in the same Brewing Stand batch share that result; if every effect roll misses, a weighted fallback guarantees one effect.
- Redstone, glowstone dust, gunpowder, and dragon's breath retain their roles in changing duration, potency, and potion delivery.
- Red mushrooms can replace Nether Wart to start brewing, but create an unstable base that reduces later ingredient success rates. Experimentation has a cost, and familiar materials can yield unfamiliar results.
- Your research records effects you have actually discovered. Repeated experiments improve the processing-time estimates shown in the manual.
- Research is saved per player in the world and remains available when you replace your book or reconnect. Recipe unlocks help you find new crafting recipes as you obtain materials. Compatible Totem modules can contribute chapters to the same manual.

## Cauldron mixtures

- A lit Campfire beneath a cauldron powers reactions. Ingredients react on their own schedules. A built-in crosshair display shows capacity and approximate stages such as “Almost there” and “Perfect”, without requiring another HUD mod.
- Bottle an unfinished mixture and return it to the cauldron later to continue cooking.
- Each completed reaction has a short extraction window before continued heating can reduce stability. Finished potions are stabilized when bottled; pouring them back does not restart overcooking unless you begin another reaction.
- Large potion flasks hold up to three portions and show their remaining amount.

## From everyday materials to new drinks

- **Stone bowls:** combine a bowl with gravel to obtain flint and recover the bowl; use a bowl on sulfur to collect it for further crafting.
- **Pig manure and wood ash:** fed pigs leave manure that you can collect with a shovel. Smelt hay bales for wood ash, then combine ash, mushrooms, and manure in a heated cauldron to make saltpeter.
- **Craftable gunpowder:** combine sulfur, saltpeter, and coal or charcoal through the alchemy recipe.
- **New drinks and effects:** prepare hot cocoa from milk, cocoa powder, and sugar, or cherry brew from cherry leaves, sugar, and berries. Cherry Swiftness and Firefly Strength provide themed variants of familiar potion effects.

Server data packs can define cauldron recipes, ingredient processing times, starter materials, and effect weights. See the repository README for formats and examples.

## Getting started

1. Install Fabric API, TotemCore, and TotemAlchemy on both the client and server.
2. Use a Book on a Brewing Stand to obtain or refresh the Alchemy chapter of the Totem Manual.
3. Experiment with brewing ingredients to discover effects and improve your research.
4. Place a lit Campfire beneath a cauldron and follow the recipes in the manual or project README.

## Requirements and documentation

**Requirements for 0.1.49:** Minecraft 26.2, Fabric Loader 0.19.3+, Java 25+, Fabric API, and TotemCore `>=0.7.18 <0.8.0`, published as **TotemLibrary**. Other Totem gameplay modules are optional. Older releases may require different dependency versions.

## 繁體中文

**用實驗發現藥水效果，在煉藥鍋前掌握火候，讓日常材料成為生存煉金的起點。**

TotemAlchemy 使用熟悉的釀造台、煉藥鍋、材料與書本，擴充 Minecraft 的生存釀造。本介紹對應 **0.1.49**。

### 機率釀造與個人研究

不同材料有不同成功率。效果材料融合成功後，每個候選效果分別判定，因此同批藥水可能同時具有多種效果；同批相容瓶子共用結果，全部落空時仍會保底選出一種效果。

紅石、螢石粉、火藥與龍之吐息仍負責延長、強化、飛濺與滯留。紅色蘑菇可以代替地獄疙瘩啟動釀造，但會留下不穩定基底，降低後續材料的成功率。

手持普通書對釀造台使用，即可取得共用 Totem 手冊的煉金章節。研究只揭露你實際發現的效果；重複成功實驗會改善處理時間的估計。紀錄依玩家儲存在世界中，換書或重新登入也會保留。取得材料時還能解鎖相關合成配方；相容的 Totem 模組可把章節整合到同一本手冊。

### 各自計時的混合液

在煉藥鍋下方放置點燃的營火，各份材料會依自己的進度反應。準星提示直接顯示容量，以及「差不多了」「完美」等模糊階段，不必另外安裝 HUD 模組。

未完成混合液可以先裝瓶，之後倒回鍋內接續烹煮。每份材料完成後都有短暫的取出時間窗；持續加熱過久會降低穩定度，甚至改變混合液。完成品裝瓶後會定型，倒回鍋中不會自行再次過火，除非加入材料開始新反應。大型藥水瓶最多保存三份，並顯示剩餘份量。

### 從材料循環到新飲品

- **石缽：**搭配礫石取得燧石，缽會返還；也能對硫磺方塊使用，取得後續製作所需的硫磺。
- **豬糞、木灰與硝石：**餵食豬後收集牠留下的豬糞；熔煉乾草捆取得木灰，再與蘑菇、豬糞放入加熱的煉藥鍋製作硝石。
- **火藥：**硫磺、硝石與煤炭或木炭可透過煉金配方合成火藥。
- **飲品與效果：**牛奶、可可粉與糖可製作熱可可；櫻花樹葉、糖與莓果可製作櫻花釀。櫻花迅捷與螢草力量則提供熟悉藥水效果的特色變體。

### 開始遊玩與安裝需求

1. Client 與 Server 都安裝 Fabric API、TotemLibrary 與 TotemAlchemy。
2. 用普通書對釀造台取得手冊，開始釀造與研究。
3. 製作石缽、收集材料，在煉藥鍋下方點燃營火。
4. 依配方投入材料，觀察火候，再裝瓶保存成果。

伺服器資料包可以設定煉藥鍋配方、材料處理時間、啟動基底的材料與效果權重；完整格式及範例請參閱專案 README。

**0.1.49 需求：**Minecraft 26.2、Fabric Loader 0.19.3+、Java 25+、Fabric API，以及 TotemCore `>=0.7.18 <0.8.0`；TotemCore 在 Modrinth 的名稱為 **TotemLibrary**。其他 Totem 玩法模組皆為選配；舊版請依該版本的依賴需求安裝。

[Source code and detailed recipes](https://github.com/Yunitrish006006/TotemAlchemy) · [Report an issue](https://github.com/Yunitrish006006/TotemAlchemy/issues) · [TotemLibrary (TotemCore dependency)](https://modrinth.com/mod/totemlibrary)
