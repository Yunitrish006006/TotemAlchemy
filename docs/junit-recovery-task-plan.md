# JUnit baseline recovery — small-task execution queue

Status: **active**, supersedes feature work until the baseline test suite is green.
Branch: `feature/m11-t01-signature-brew-resolver`; PR #120 remains Draft.
Last verified baseline: [Build #419](https://github.com/Yunitrish006006/TotemAlchemy/actions/runs/38064177467) — **259 JUnit, 43 failed**, **111/111 main GameTests**, OFF/OFF and four optional datapack combinations passed. Build workflow red because of JUnit. One `LiquidCompositionTest` normalization regression was fixed in #418 (44 → 43 failures).

## Execution contract

1. **Only one numbered task per development iteration**. Stop and report the exact changed files, commit, CI link, pass/fail counts and next task; do not silently complete the entire queue.
2. Before a fix, run or inspect a **focused Gradle test filter** for the current class/group. After a fix, run the focused filter first, then all JUnit and the existing GameTest matrix; only check off the task after CI evidence. A CI failure must be diagnosed and corrected within that task, not waived.
3. Preserve the existing test assertions and production behavior. **No `ignoreFailures`, disabled tests, skipped tasks, changed CI success criteria or unreviewed blanket fixture mocks**. If a test itself is incorrect, document the precise contract and add a regression case before changing it.
4. Every task has a reproducible exit criterion; a green result from one test class does not imply other classes pass. Keep M11 source-generation / payout features paused until JUnit is green.
5. Each entry's initial count below is the **observed failing test count in Build #419**, not an assertion that the entire class contains only those tests. Recheck totals after each task.

## Root-cause and implementation tasks (sequential)

- [ ] **JUNIT-00 — Registry / deferred data component fixture (43 failures share this dependency).** Repair the JVM-only Fabric Loader JUnit bootstrap so `new ItemStack(Items.GLASS_BOTTLE)` and `new ItemStack(Items.POTION)` work after real vanilla deferred component binding. Diagnose why the current `MinecraftRegistryBootstrapExtension` and `DataComponentInitializers` invocation still leave `Holder.Reference.components()` unbound. Add one minimal fixture smoke test (vanilla item and custom item) and run only that test first. Ensure registry lookups, reloadable providers and the actual `PendingComponents.apply()` target match Minecraft 26.3. Exit: fixture smoke test green, no `Components not bound yet`; full JUnit baseline recorded. Do **not** claim all 43 failures are fixed without re-running them.
- [ ] **JUNIT-01 — Honey bottle adapter (5 failing tests).** `HoneyBottleContainerAdapterTest`. Verify glass-bottle fill, drain, pure honey restrictions, source remainder and stack-size constraints. Exit: focused class green.
- [ ] **JUNIT-02 — Large flask adapter (5 failing tests).** `LargeFlaskContainerAdapterTest`. Verify 3-unit capacity, partial top-up/drain, state copy and empty/stacked invalid cases. Exit: focused class green.
- [ ] **JUNIT-03 — Adapter dispatcher (6 failing tests).** `LiquidContainerAdaptersTest`. Verify defensive copies, invalid transfers, adapter precedence and fallback. Exit: focused class green.
- [ ] **JUNIT-04 — Milk bucket adapter (5 failing tests).** `MilkBucketContainerAdapterTest`. Verify exact 3-unit conversion and pure-milk constraints. Exit: focused class green.
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
