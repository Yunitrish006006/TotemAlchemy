# JUnit baseline recovery — small-task execution queue

Status: **COMPLETE — JUNIT-00 through JUNIT-09 verified in Build #470**; full JUnit baseline restored. M11 feature work can resume at the next checkpoint; PR #120 remains Draft and unmerged.
Branch: `feature/m11-t01-signature-brew-resolver`; PR #120 remains Draft.
Original failure baseline: [Build #419](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38064177467) — **259 JUnit, 43 failed**; one floating-point normalization failure was already fixed in #418 (44 → 43).

**JUNIT-00 verified:** [Build #443](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38113063682) — both focused bootstrap test classes green; 264 JUnit, 9 failures; 111/111 main GameTests, OFF/OFF and all four optional datapack combinations passed.

**JUNIT-01 verified:** [Build #448](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38123683084) — JUNIT-00 and **JUNIT-01 honey adapter focused Gradle steps both passed**, full JUnit **264 executed / 256 passed / 8 failed**; **111/111 main GameTests**, OFF/OFF and all four datapack combinations passed. Overall workflow is still red due to the other eight JUnit failures, not the honey adapter.

**JUNIT-02 verified:** [Build #450](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38135977520) — dedicated `LargeFlaskContainerAdapterTest` Gradle filter **passed** (5 test methods; zero failures) following JUNIT-00 and JUNIT-01 focused passes. Full suite **264 JUnit / 256 passed / 8 failed**, with failures solely in milk bucket (2), water bottle (5), and water bucket (1). Main GameTests **111/111 passed within `build --continue`**; OFF/OFF and each of OFF_OFF, ON_OFF, OFF_ON, ON_ON pack matrix passed. Overall workflow remains **failed** because the remaining 8 JUnit assertions are still red; the standalone “Run server GameTests” step was skipped after the build failure. JUNIT-02 required no production-code or test-assertion changes; CI now runs the focused filter explicitly.

**JUNIT-03 verified:** [Build #452](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38136581606) — dedicated `LiquidContainerAdaptersTest` Gradle filter **passed** (6 test methods, zero failures). Full suite **264 JUnit / 256 passed / 8 failed**: milk bucket (2), water bottle (5), water bucket (1), with no dispatcher regressions. Main GameTests **111/111 passed within `build --continue`**, OFF/OFF isolation **5/5**, and each OFF_OFF, ON_OFF, OFF_ON, ON_ON pack-matrix **3/3**. Overall workflow remains **failed** on the eight unrelated JUnit tests; the later standalone “Run server GameTests” step was skipped. No production or test-assertion changes were needed; focused CI was added in commit `32b6d16`.

**JUNIT-04 verified:** [Build #457](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38138469182) — dedicated `MilkBucketContainerAdapterTest` Gradle filter **passed** (7 tests, zero failures, including new dispatcher/fallback regression). Full suite **265 JUnit / 259 passed / 6 failed**; both previously failing milk assertions are green. The remaining 6 failures are water bottle (5) and water bucket (1). Main GameTests **111/111 passed inside `build --continue`**, OFF/OFF isolation **5/5**, each OFF_OFF, ON_OFF, OFF_ON, ON_ON matrix **3/3**. The overall workflow remains **failed** due to the six unrelated JUnit failures; standalone later “Run server GameTests” was skipped. Production adapter precedence now prefers plain milk before generic water bucket. Milk-adapter rejection is tested directly, whereas generic dispatcher fallback remains allowed and is guarded by a new sealed-state round-trip regression. No assertions were disabled or gates weakened. Focused pre-fix failure baseline: [Build #454](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38137648259); first repair candidate failed on the new round-trip test because it compared unsealed vs sealed bottle-state metadata [Build #456](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38137942295). Fixed by establishing sealed state in the regression before comparison.

**JUNIT-05 verified:** [Build #459](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38140356154) — focused `PotionContainerAdapterTest` Gradle filter **passed (7 tests / 0 failed)**. All previously verified JUNIT-00–04 focused steps also passed. Full suite **265 JUnit / 259 passed / 6 failed**; the 6 failures remain solely `WaterBottleContainerAdapterTest` (5) and `WaterBucketContainerAdapterTest` (1), with **no potion adapter regressions**. Main GameTests **111/111 passed within `build --continue`**, OFF/OFF isolation **5/5**, and OFF_OFF, ON_OFF, OFF_ON, ON_ON optional pack matrix **3/3 each**. Overall Workflow **failed** because of the existing six unrelated JUnit assertions; the later standalone `Run server GameTests` step was skipped after the build failure. No production Java or test assertions changed; this checkpoint only added the dedicated CI filter (commit `45b682a`).

**JUNIT-06 verified:** [Build #463](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38141852277) — dedicated `WaterBottleContainerAdapterTest` Gradle filter **passed** (7 test methods, 0 failures), following a red dedicated [Build #461](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38141555154) with **7 tests / 5 failures**. Updated `AlchemyMixtureBrewing.waterState()` to initialize explicit `minecraft:water = 1.0` liquid composition for nonempty water, and corrected water-only-adapter rejection assertions to target that adapter directly while guarding generic-potion dispatcher fallback and retained chemistry. **Full Gradle `build` passed** with no JUnit failures (265 tests in the preceding baseline; unchanged test method count). **Main GameTests 111/111** passed both inside `build` and the standalone `runGameTest`, **OFF/OFF isolation 5/5**, each pack mode OFF_OFF, ON_OFF, OFF_ON, ON_ON **3/3**. Entire Build #463 workflow conclusion: **success**. The former water-bucket assertion failure also disappears from the full suite after the shared water-state fix, but **JUNIT-07 remains unchecked until its own focused CI run**. Likewise JUNIT-08 and JUNIT-09 remain pending per execution contract. Production fix commit: `fa49907`; test contract/regression commit: `468020e`; dedicated CI commit: `eba775f`.

**JUNIT-07 verified:** [Build #465](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38142653365) — dedicated `WaterBucketContainerAdapterTest` Gradle filter **passed** (5 test methods / 0 failures), following independently successful JUNIT-00–06 focused tests. Tests cover exact three-unit empty/full bucket conversions, mandatory complete transfer, mixed/stateful water bucket round-trip, and single-stack constraints. The shared explicit water-liquid composition repair in JUNIT-06 already removed the historical water-bucket failure; **no JUNIT-07 production or test assertion changes were necessary**. Full Gradle `build` **passed** with no JUnit failures (same 265 methods as the prior baseline); 111/111 main GameTests passed within `build`, **OFF/OFF 5/5**, all OFF_OFF, ON_OFF, OFF_ON, ON_ON pack configurations **3/3 each**, and standalone main GameTests **111/111**. Entire Build #465 workflow conclusion: **success**. Dedicated filter CI commit: `4e897a1`. **JUNIT-08 timing and JUNIT-09 final regression gate are not yet checked off** and M11 remains paused until the queue is formally complete.

**JUNIT-08 verified:** [Build #467](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38144649647) — dedicated `AlchemyMixtureTimingTest` Gradle filter **passed** (11 test methods / 0 failures), following JUNIT-00–07 focused passes. Verified pending reaction progress boundaries and slowest-stage selection, completed stability/overcook classifications, scaled Perfect Window and legacy migration, reaction dose and completed-stage codec round-trip, and bottling heat-lock/history/HUD state. **Full Gradle `build` passed with zero JUnit failures** (test method count unchanged from the previous 265-method baseline). Main GameTests **111/111** passed inside `build` and standalone, **OFF/OFF isolation 5/5**, each pack state OFF_OFF, ON_OFF, OFF_ON, ON_ON **3/3**. Overall Build #467: **success**. No production Java or test assertion changes; added only the focused CI filter in commit `541bc6c`. **JUNIT-09 remains unchecked** pending its separate final acceptance iteration.

**JUNIT-09 FINAL ACCEPTANCE:** [Build #470](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38154552710) against commit `261096a` — independent unfiltered `./gradlew test --rerun-tasks` **passed 265/265 tests in 50 test classes, 0 failures, 0 errors and 0 skipped**. Results were parsed and gated using Gradle JUnit XML; this was not inferred solely from the overall build conclusion. JUNIT-00–08 dedicated tests all passed on the same run. `./gradlew build --continue` **passed**. Main GameTests **111/111 inside build** and **111/111 standalone**; OFF/OFF isolation **5/5**; each OFF_OFF, ON_OFF, OFF_ON, ON_ON optional datapack matrix **3/3**. Entire Build #470 workflow conclusion **success**. Original [Build #419](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38064177467) failed 43/259 JUnit tests; final failed count **43 → 0** with all 265 tests passing, and no test exclusions, disabled assertions or ignored failures. The last checkpoint added a full-suite explicit CI gate (`b8addd6`, `261096a`); **no additional production code or test assertion changes** were required for JUNIT-09. All ten JUNIT recovery tasks are completed. M11 recipient-owned durable grant work may resume as its own next task, not as part of this JUNIT-09 checkpoint. Keep the stacked PR #120 in Draft until the separate M11 feature, review and CI criteria are complete.

## Execution contract

1. **Only one numbered task per development iteration**. Stop and report the exact changed files, commit, CI link, pass/fail counts and next task; do not silently complete the entire queue.
2. Before a fix, run or inspect a **focused Gradle test filter** for the current class/group. After a fix, run the focused filter first, then all JUnit and the existing GameTest matrix; only check off the task after CI evidence. A CI failure must be diagnosed and corrected within that task, not waived.
3. Preserve the existing test assertions and production behavior. **No `ignoreFailures`, disabled tests, skipped tasks, changed CI success criteria or unreviewed blanket fixture mocks**. If a test itself is incorrect, document the precise contract and add a regression case before changing it.
4. Every task has a reproducible exit criterion; a green result from one test class does not imply other classes pass. The M11 source-generation / payout pause is lifted **after** Build #470 completed JUNIT-09; resume M11 in a separate checkpoint.
5. Each entry's initial count below is the **observed failing test count in Build #419**, not an assertion that the entire class contains only those tests. Recheck totals after each task.

## Root-cause and implementation tasks (sequential)

- [x] **JUNIT-00 — Registry / deferred data component fixture.** Verified in Build #443. Explicit Jupiter `@WithMinecraftItemComponents` applied to the standalone smoke test, test-only bootstrap ordering permits mod item registration before vanilla registry freeze, and deferred vanilla/custom item component prototypes are initialized. Both focused bootstrap test classes passed; full suite: **264 JUnit, 9 failures**, down from 43, with no remaining `Components not bound yet` exceptions. Production registration behavior is unchanged.
- [x] **JUNIT-01 — Honey bottle adapter (5 original, 1 remaining after JUNIT-00).** `HoneyBottleContainerAdapterTest` — all tests green in Build #448 focused run and full suite. Fixed a misplaced assertion: the **honey adapter** must reject water, while the **dispatcher** must accept water via its water-bottle adapter. The test now exercises both branches; production behavior unchanged. One remaining failed test removed from the baseline (9 → 8).
- [x] **JUNIT-02 — Large flask adapter (5 original failing tests).** `LargeFlaskContainerAdapterTest`. Verified in Build #450: **5 focused test methods passed, 0 failed**, covering 3-unit capacity, partial top-up/drain, full pending-mixture state copy, and empty/stacked invalid cases. No product or test assertion changes. Full suite still has 8 unrelated JUnit failures.
- [x] **JUNIT-03 — Adapter dispatcher (6 original failing tests).** `LiquidContainerAdaptersTest`. Verified in Build #452: **6 focused test methods passed, 0 failed**, covering defensive copies, invalid transfers, adapter precedence, and fallback for unsupported containers. Production behavior and assertions unchanged. Full suite still has 8 unrelated JUnit failures.
- [x] **JUNIT-04 — Milk bucket adapter (5 original failing tests, 2 after shared bootstrap).** `MilkBucketContainerAdapterTest`. Verified Build #457: **7 focused tests passed / 0 failed**, fixing vanilla milk-bucket precedence and correcting the adapter-vs-dispatcher negative test scope. Added sealed-state fallback preservation regression; full suite now 265 / 6 failed, with no milk adapter failures. Next JUNIT-05.
- [x] **JUNIT-05 — Potion adapter (7 original failing tests).** `PotionContainerAdapterTest`. Verified in Build #459: **7 focused tests passed / 0 failed**, covering drinkable/splash/lingering forms, canonical potion metadata, effect conservation on single-unit export, pending reaction snapshot, signature drink exclusion, and invalid item/transfer cases. No production or test assertion changes; six water adapter failures remain.
- [x] **JUNIT-06 — Water bottle adapter (7 original failing tests; 5 after bootstrap).** `WaterBottleContainerAdapterTest`. Verified Build #463: **7 focused tests passed / 0 failed**. Fixed explicit pure-water composition, verified one-unit conversion, water-only rejection, generic potion fallback with lossless chemistry and delivery form, and invalid stack/transfer cases. Full `build` and GameTest matrix passed; JUNIT-07 is next for separate focused verification.
- [x] **JUNIT-07 — Water bucket adapter (5 original failing tests; 1 after bootstrap).** `WaterBucketContainerAdapterTest`. Build #465 dedicated CI **5/5 passed**, covering three-unit conservation, pure-water bucket, mixed/stateful round-trip and invalid limits/stacks. No further product or test changes after JUNIT-06 fixed shared water state. Full build and GameTest matrix green; JUNIT-08 next.
- [x] **JUNIT-08 — Mixture reaction timing (3 original failing tests).** `AlchemyMixtureTimingTest`. Verified in Build #467: **11 focused tests passed / 0 failed** for reaction progress, completion and overcook states, scaled Perfect Window, reaction dose and codec round-trips, and bottling heat-lock/history/HUD behavior. No production or test assertion changes; full build/GameTest matrix green. Next: JUNIT-09 final acceptance.
- [x] **JUNIT-09 — Final all-suite regression gate.** Verified Build #470: **265/265 JUnit passed in 50 classes, 0 failed, 0 errors, 0 skipped** with explicit unfiltered `./gradlew test --rerun-tasks` and XML summary. JUNIT-00–08 focused checks, full `build`, main GameTests 111/111 (both inside build and standalone), OFF/OFF 5/5 and four pack states 3/3 all passed. Entire CI success. **Recovery plan 10/10 complete; resume M11 recipient-owned durable grant work only in a subsequent checkpoint.**

### Build #419 failure classification

| Test class | Failures |
| --- | ---: |
| `HoneyBottleContainerAdapterTest` | 5 |
| `LargeFlaskContainerAdapterTest` | 5 |
| `LiquidContainerAdaptersTest` | 6 |
| `MilkBucketContainerAdapterTest` | 5 |
| `PotionContainerAdapterTest` | 7 |
| `WaterBottleContainerAdapterTest` | 7 |
| `WaterBucketContainerAdapterTest` | 5 |
| `AlchemyMixtureTimingTest` | 3 |
| **Total** | **43** |

All 43 failed with `java.lang.NullPointerException: Components not bound yet` from `Holder.Reference.components()` when constructing an `ItemStack`. Therefore **JUNIT-00 is a shared blocker**, not 43 independent product defects. Keep per-class follow-up tasks even if the common fixture repair makes them pass, so that each behavior group is independently verified.

### Build #443 post-bootstrap failure classification (historical baseline)

| Test class | Remaining JUnit failures | Interpretation |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | **1** | Liquid/recipe rejection assertion; **JUNIT-01 next** |
| `LargeFlaskContainerAdapterTest` | 0 | Behavior class now green; perform focused verification during JUNIT-02 |
| `LiquidContainerAdaptersTest` | 0 | Behavior class now green; perform focused verification during JUNIT-03 |
| `MilkBucketContainerAdapterTest` | **2** | Plain milk conversions/restrictions; JUNIT-04 |
| `PotionContainerAdapterTest` | 0 | Behavior class now green; perform focused verification during JUNIT-05 |
| `WaterBottleContainerAdapterTest` | **5** | Water composition and stateful-water handling; JUNIT-06 |
| `WaterBucketContainerAdapterTest` | **1** | Water composition; JUNIT-07 |
| `AlchemyMixtureTimingTest` | 0 | Behavior class now green; perform focused verification during JUNIT-08 |
| **Total** | **9** | All are assertions; no JVM bootstrap errors |

### Build #448 after JUNIT-01 focused acceptance (historical baseline)

| Test class | Failed | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | **0** | JUNIT-01 **done** |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 **next — focused verification needed** |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 focused verification pending |
| `MilkBucketContainerAdapterTest` | 2 | JUNIT-04 repair pending |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 focused verification pending |
| `WaterBottleContainerAdapterTest` | 5 | JUNIT-06 repair pending |
| `WaterBucketContainerAdapterTest` | 1 | JUNIT-07 repair pending |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **8** | Build #448: 264 JUnit / 8 failed |

### Build #450 after JUNIT-02 focused acceptance (historical baseline)

| Test class | Failed | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | **0** | JUNIT-02 **done; focused class passed** |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 **next — focused verification needed** |
| `MilkBucketContainerAdapterTest` | 2 | JUNIT-04 repair pending |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 focused verification pending |
| `WaterBottleContainerAdapterTest` | 5 | JUNIT-06 repair pending |
| `WaterBucketContainerAdapterTest` | 1 | JUNIT-07 repair pending |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **8** | 264 JUnit / 8 failed; JUNIT-02 focused passed |

### Build #452 after JUNIT-03 focused acceptance (historical baseline)

| Test class | Failed | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | **0** | JUNIT-03 **done; focused class passed** |
| `MilkBucketContainerAdapterTest` | 2 | JUNIT-04 **next — repair needed** |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 focused verification pending |
| `WaterBottleContainerAdapterTest` | 5 | JUNIT-06 repair pending |
| `WaterBucketContainerAdapterTest` | 1 | JUNIT-07 repair pending |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **8** | 264 JUnit / 8 failed; JUNIT-03 focused passed |

### Build #457 after JUNIT-04 focused acceptance (historical baseline)

| Test class | Failed | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 done |
| `MilkBucketContainerAdapterTest` | **0** | JUNIT-04 **done; 7 focused tests passed** |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 **next — focused verification needed** |
| `WaterBottleContainerAdapterTest` | 5 | JUNIT-06 repair pending |
| `WaterBucketContainerAdapterTest` | 1 | JUNIT-07 repair pending |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **6** | 265 JUnit / 6 failed; 111/111 main GameTests passed |

### Build #459 after JUNIT-05 focused acceptance (historical baseline)

| Test class | Failed | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 done |
| `MilkBucketContainerAdapterTest` | 0 | JUNIT-04 done |
| `PotionContainerAdapterTest` | **0** | JUNIT-05 **done; 7 focused tests passed** |
| `WaterBottleContainerAdapterTest` | 5 | JUNIT-06 **next — repair needed** |
| `WaterBucketContainerAdapterTest` | 1 | JUNIT-07 repair pending |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **6** | 265 JUnit / 6 failed; main GameTests 111/111 |

### Build #463 after JUNIT-06 focused acceptance (historical baseline)

| Test class | Failed in full suite | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 done |
| `MilkBucketContainerAdapterTest` | 0 | JUNIT-04 done |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 done |
| `WaterBottleContainerAdapterTest` | **0** | JUNIT-06 **done; 7 focused tests passed** |
| `WaterBucketContainerAdapterTest` | 0 | JUNIT-07 **next — focused verification needed** |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 focused verification pending |
| **Total** | **0** | Full `build` green; prior suite size 265 JUnit methods |

### Build #465 after JUNIT-07 focused acceptance (historical baseline)

| Test class | Failed in full suite | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 done |
| `MilkBucketContainerAdapterTest` | 0 | JUNIT-04 done |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 done |
| `WaterBottleContainerAdapterTest` | 0 | JUNIT-06 done |
| `WaterBucketContainerAdapterTest` | **0** | JUNIT-07 **done; 5 focused tests passed** |
| `AlchemyMixtureTimingTest` | 0 | JUNIT-08 **next — focused verification needed** |
| **Total** | **0** | Full `build` green; 265 JUnit methods, 111/111 main GameTests |

### Build #467 after JUNIT-08 focused acceptance (historical baseline)

| Test class | Failed in full suite | Assigned task |
| --- | ---: | --- |
| `HoneyBottleContainerAdapterTest` | 0 | JUNIT-01 done |
| `LargeFlaskContainerAdapterTest` | 0 | JUNIT-02 done |
| `LiquidContainerAdaptersTest` | 0 | JUNIT-03 done |
| `MilkBucketContainerAdapterTest` | 0 | JUNIT-04 done |
| `PotionContainerAdapterTest` | 0 | JUNIT-05 done |
| `WaterBottleContainerAdapterTest` | 0 | JUNIT-06 done |
| `WaterBucketContainerAdapterTest` | 0 | JUNIT-07 done |
| `AlchemyMixtureTimingTest` | **0** | JUNIT-08 **done; 11 focused tests passed** |
| **Total** | **0** | Full `build` green; unchanged 265 JUnit-method baseline; main GameTests 111/111 |

### Build #470 after JUNIT-09 final all-suite acceptance (final baseline)

| Test scope | Result | Evidence |
| --- | --- | --- |
| JUNIT-00–08 independent CI filters | All passed | Dedicated Gradle steps in Build #470 |
| Full unfiltered JUnit suite | **265 passed / 0 failed / 0 errors / 0 skipped** | 50 JUnit XML `TEST-*.xml` suites, count checked in JUNIT-09 CI |
| TotemAlchemy `build --continue` | **Success** | Build #470 |
| Main GameTests inside `build` | **111/111** | Build #470 |
| OFF/OFF isolation GameTests | **5/5** | Build #470 |
| OFF_OFF pack state | **3/3** | Build #470 |
| ON_OFF pack state | **3/3** | Build #470 |
| OFF_ON pack state | **3/3** | Build #470 |
| ON_ON pack state | **3/3** | Build #470 |
| Standalone main GameTests | **111/111** | Build #470 |
| Overall GitHub Actions workflow | **Success** | [Build #470](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38154552710) |

**Recovery result:** 43 failed JUnit tests in Build #419 → 0 failed in Build #470, with full coverage retained and 265 total tests passing. This plan is closed. The original JR-numbered plan is historical; do not start a second recovery queue. Resume M11 in the next separate development iteration; PR #120 remains Draft until M11 itself passes review and its acceptance gates.

**Work discipline:** Each JUNIT-00–08 task was checked off only after its dedicated focused Gradle test filter passed and CI evidence was recorded. JUNIT-09 independently re-ran the full test suite and verified the XML counts, the whole build and all GameTest modes.
