# JUnit baseline recovery — small-task execution queue

Status: **active — JUNIT-04 verified, JUNIT-05 next**, supersedes feature work until the baseline test suite is green.
Branch: `feature/m11-t01-signature-brew-resolver`; PR #120 remains Draft.
Original failure baseline: [Build #419](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38064177467) — **259 JUnit, 43 failed**; one floating-point normalization failure was already fixed in #418 (44 → 43).

**JUNIT-00 verified:** [Build #443](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38113063682) — both focused bootstrap test classes green; 264 JUnit, 9 failures; 111/111 main GameTests, OFF/OFF and all four optional datapack combinations passed.

**JUNIT-01 verified:** [Build #448](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38123683084) — JUNIT-00 and **JUNIT-01 honey adapter focused Gradle steps both passed**, full JUnit **264 executed / 256 passed / 8 failed**; **111/111 main GameTests**, OFF/OFF and all four datapack combinations passed. Overall workflow is still red due to the other eight JUnit failures, not the honey adapter.

**JUNIT-02 verified:** [Build #450](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38135977520) — dedicated `LargeFlaskContainerAdapterTest` Gradle filter **passed** (5 test methods; zero failures) following JUNIT-00 and JUNIT-01 focused passes. Full suite **264 JUnit / 256 passed / 8 failed**, with failures solely in milk bucket (2), water bottle (5), and water bucket (1). Main GameTests **111/111 passed within `build --continue`**; OFF/OFF and each of OFF_OFF, ON_OFF, OFF_ON, ON_ON pack matrix passed. Overall workflow remains **failed** because the remaining 8 JUnit assertions are still red; the standalone “Run server GameTests” step was skipped after the build failure. JUNIT-02 required no production-code or test-assertion changes; CI now runs the focused filter explicitly.

**JUNIT-03 verified:** [Build #452](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38136581606) — dedicated `LiquidContainerAdaptersTest` Gradle filter **passed** (6 test methods, zero failures). Full suite **264 JUnit / 256 passed / 8 failed**: milk bucket (2), water bottle (5), water bucket (1), with no dispatcher regressions. Main GameTests **111/111 passed within `build --continue`**, OFF/OFF isolation **5/5**, and each OFF_OFF, ON_OFF, OFF_ON, ON_ON pack-matrix **3/3**. Overall workflow remains **failed** on the eight unrelated JUnit tests; the later standalone “Run server GameTests” step was skipped. No production or test-assertion changes were needed; focused CI was added in commit `32b6d16`.

**JUNIT-04 verified:** [Build #457](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38138469182) — dedicated `MilkBucketContainerAdapterTest` Gradle filter **passed** (7 tests, zero failures, including new dispatcher/fallback regression). Full suite **265 JUnit / 259 passed / 6 failed**; both previously failing milk assertions are green. The remaining 6 failures are water bottle (5) and water bucket (1). Main GameTests **111/111 passed inside `build --continue`**, OFF/OFF isolation **5/5**, each OFF_OFF, ON_OFF, OFF_ON, ON_ON matrix **3/3**. The overall workflow remains **failed** due to the six unrelated JUnit failures; standalone later “Run server GameTests” was skipped. Production adapter precedence now prefers plain milk before generic water bucket. Milk-adapter rejection is tested directly, whereas generic dispatcher fallback remains allowed and is guarded by a new sealed-state round-trip regression. No assertions were disabled or gates weakened. Focused pre-fix failure baseline: [Build #454](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38137648259); first repair candidate failed on the new round-trip test because it compared unsealed vs sealed bottle-state metadata [Build #456](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38137942295). Fixed by establishing sealed state in the regression before comparison.

## Execution contract

1. **Only one numbered task per development iteration**. Stop and report the exact changed files, commit, CI link, pass/fail counts and next task; do not silently complete the entire queue.
2. Before a fix, run or inspect a **focused Gradle test filter** for the current class/group. After a fix, run the focused filter first, then all JUnit and the existing GameTest matrix; only check off the task after CI evidence. A CI failure must be diagnosed and corrected within that task, not waived.
3. Preserve the existing test assertions and production behavior. **No `ignoreFailures`, disabled tests, skipped tasks, changed CI success criteria or unreviewed blanket fixture mocks**. If a test itself is incorrect, document the precise contract and add a regression case before changing it.
4. Every task has a reproducible exit criterion; a green result from one test class does not imply other classes pass. Keep M11 source-generation / payout features paused until JUnit is green.
5. Each entry's initial count below is the **observed failing test count in Build #419**, not an assertion that the entire class contains only those tests. Recheck totals after each task.

## Root-cause and implementation tasks (sequential)

- [x] **JUNIT-00 — Registry / deferred data component fixture.** Verified in Build #443. Explicit Jupiter `@WithMinecraftItemComponents` applied to the standalone smoke test, test-only bootstrap ordering permits mod item registration before vanilla registry freeze, and deferred vanilla/custom item component prototypes are initialized. Both focused bootstrap test classes passed; full suite: **264 JUnit, 9 failures**, down from 43, with no remaining `Components not bound yet` exceptions. Production registration behavior is unchanged.
- [x] **JUNIT-01 — Honey bottle adapter (5 original, 1 remaining after JUNIT-00).** `HoneyBottleContainerAdapterTest` — all tests green in Build #448 focused run and full suite. Fixed a misplaced assertion: the **honey adapter** must reject water, while the **dispatcher** must accept water via its water-bottle adapter. The test now exercises both branches; production behavior unchanged. One remaining failed test removed from the baseline (9 → 8).
- [x] **JUNIT-02 — Large flask adapter (5 original failing tests).** `LargeFlaskContainerAdapterTest`. Verified in Build #450: **5 focused test methods passed, 0 failed**, covering 3-unit capacity, partial top-up/drain, full pending-mixture state copy, and empty/stacked invalid cases. No product or test assertion changes. Full suite still has 8 unrelated JUnit failures.
- [x] **JUNIT-03 — Adapter dispatcher (6 original failing tests).** `LiquidContainerAdaptersTest`. Verified in Build #452: **6 focused test methods passed, 0 failed**, covering defensive copies, invalid transfers, adapter precedence, and fallback for unsupported containers. Production behavior and assertions unchanged. Full suite still has 8 unrelated JUnit failures.
- [x] **JUNIT-04 — Milk bucket adapter (5 original failing tests, 2 after shared bootstrap).** `MilkBucketContainerAdapterTest`. Verified Build #457: **7 focused tests passed / 0 failed**, fixing vanilla milk-bucket precedence and correcting the adapter-vs-dispatcher negative test scope. Added sealed-state fallback preservation regression; full suite now 265 / 6 failed, with no milk adapter failures. Next JUNIT-05.
- [ ] **JUNIT-05 — Potion adapter (7 failing tests).** `PotionContainerAdapterTest`. Verify generic potion forms, effect conservation, pending state and signature drink exclusion. Exit: focused class green.
- [ ] **JUNIT-06 — Water bottle adapter (7 failing tests).** `WaterBottleContainerAdapterTest`. Verify 1-unit conversion and rejection of stateful water that cannot round-trip. Exit: focused class green.
- [ ] **JUNIT-07 — Water bucket adapter (5 failing tests).** `WaterBucketContainerAdapterTest`. Verify exact 3-unit conversion, mixed/stateful contents and bucket constraints. Exit: focused class green.
- [ ] **JUNIT-08 — Mixture reaction timing (3 failing tests).** `AlchemyMixtureTimingTest`. Verify reaction history, codec round-trip and scaled perfect window. Exit: focused class green.
- [ ] **JUNIT-09 — Final all-suite regression gate.** Full `./gradlew test` with **0 failed**, main GameTests **111/111 or more**, OFF/OFF, all four datapack states and build. Update this file with actual run, count and deviations, then resume M11 recipient-owned durable grant work. A failing build is **not** accepted as complete.

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

### Build #457 after JUNIT-04 focused acceptance (current baseline)

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

**Work discipline:** Do not mark JUNIT-02–08 complete merely because some of their tests passed under the global run. Explicitly run their class filter at each task's checkpoint and record that CI result before advancing.
