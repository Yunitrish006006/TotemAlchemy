# TotemAlchemy — JUnit failure recovery (one task per checkpoint)

> **Canonical active plan:** [junit-recovery-task-plan.md](junit-recovery-task-plan.md). This original JR-numbered plan remains as historical context: **JR-01 = JUNIT-00**, JR-02 through JR-09 map to JUNIT-01 through JUNIT-08, and JR-10 = JUNIT-09. Track future progress in the canonical plan to avoid parallel task queues.

**Branch:** `feature/m11-t01-signature-brew-resolver`  
**PR:** #120 (Draft).  
**Paused feature work:** M11 transaction finalization / mailbox; no further functionality until the JUnit baseline is green.

## Verified starting point

- Build #412: **259 JUnit, 44 failed**, 111/111 main server GameTests passed.
- Build #418: liquid normalization fixed; **259 JUnit, 43 failed**, 111/111 main GameTests passed.
- Build #419: **259 JUnit, 43 failed**. All remaining failures originate in `Holder.Reference.components()` with `NullPointerException: Components not bound yet`; Fabric Loader/Jupiter test runtime is now working but the vanilla 26.3 item components are not bound for tests.
- Build #423: isolated diagnostic canary showed **4/4 failed**, including a test proving Jupiter did **not auto-discover** the global bootstrap extension (263 tests, 47 failed including four diagnostics).
- Build #424: explicit `@ExtendWith(MinecraftRegistryBootstrapExtension.class)` produced **4/4 passing canary probes**, including vanilla GLASS_BOTTLE/POTION/HONEY_BOTTLE and Totem HOT_COCOA/LARGE_POTION_FLASK (263 tests, original 43 failures remain because the eight affected classes were not yet explicitly annotated).
- **Build #443:** both explicit JUNIT-00/JR-01 smoke classes green; **264 JUnit, 9 behavior assertion failures**, and **111/111 GameTests** with OFF/OFF and four optional-pack states green. `Components not bound yet` failure class eliminated.
- Keep ALL tests enabled. Never hide failures through `ignoreFailures`, `@Disabled`, excluding test classes, or weakening assertions. The number of failing tests must strictly decrease or a task remains IN PROGRESS.

## Execution rules

1. Work on **exactly one task at a time**, in the order below.
2. For each task, commit only related production/test changes and a focused test; check its Gradle `--tests` filter before the entire build when useful.
3. Record the CI run ID, count of passing/failing JUnit tests, and 111 original main GameTests (plus any additions). Do not mark DONE without evidence.
4. If JR-01 fixes several downstream classes at once, mark their corresponding class tasks DONE only after their tests actually pass; do not make unnecessary class-by-class code changes.
5. If any failure changes shape after the shared bootstrap fix, investigate its new stack trace in its own task. Preserve OFF/OFF and four-pack matrix isolation.
6. Only after JR-10 passes resume the paused M11 implementation.

## Task queue

| ID | Status | Independent scope | Baseline failed tests | Acceptance |
| --- | --- | --- | ---: | --- |
| JR-00 | DONE | Diagnose JUnit failures and fix `LiquidComposition.normalized()` floating-point idempotence | 1 (resolved) | Build #418 and #419: that test passed |
| **JR-01** | **DONE — Build #443** | **Minecraft 26.3 JUnit bootstrap:** ensure Fabric Loader and actual vanilla deferred item components are bound in the JUnit test worker | Shared root cause of 43 | Focused canary creates vanilla + Totem `ItemStack` without `Components not bound yet`; no registry fallback hacks in production |
| JR-02 | TODO | `HoneyBottleContainerAdapterTest` | 5 | 5/5 pass |
| JR-03 | TODO | `LargeFlaskContainerAdapterTest` | 5 | 5/5 pass |
| JR-04 | TODO | `LiquidContainerAdaptersTest` | 6 | 6/6 pass |
| JR-05 | TODO | `MilkBucketContainerAdapterTest` | 5 | 5/5 pass |
| JR-06 | TODO | `PotionContainerAdapterTest` | 7 | 7/7 pass |
| JR-07 | TODO | `WaterBottleContainerAdapterTest` | 7 | 7/7 pass |
| JR-08 | TODO | `WaterBucketContainerAdapterTest` | 5 | 5/5 pass |
| JR-09 | TODO | `AlchemyMixtureTimingTest` | 3 | 3/3 pass |
| JR-10 | TODO | Full test gate and clean CI: all JUnit plus server/OFF-OFF/four-pack matrix; record exact counts | 43 residual | **0 JUnit failures**, main GameTests and all optional-pack modes green |

The eight class-specific tasks account for all **43** currently failing tests (5+5+6+5+7+7+5+3). JR-01 is a shared prerequisite, **not** 43 additional tests. A fix in JR-01 may verify multiple class tasks simultaneously.

## JR-01 implementation checklist

- [x] Fabric Loader JUnit integration added; named classTweaker normalization applied to JUnit runtime (Build #418 verifies the worker can run).
- [x] Global Jupiter `BeforeAllCallback` creates the vanilla registry lookup and requests deferred component initialization.
- [x] Add a **focused bootstrap canary** to verify the global callback ran and that vanilla and Totem `ItemStack` components are bound. Build #423 isolated the skipped callback; Build #424 validated 4/4 when explicitly registered.
- [x] Correct the extension **registration** gap: added the `@WithMinecraftItemComponents` meta-annotation and applied it to the eight affected container/timing test classes and the canary. Fabric's Knot classloader did not pick up the global ServiceLoader-based Jupiter extension.
- [x] Build #443 full suite confirmed no additional bootstrap failures: 264 JUnit, 9 behavioral assertion failures, 111/111 GameTests and datapack variants passed. The remaining class-specific tests are separate follow-ups in the canonical JUNIT plan; JR-01 is DONE.

**Limitation:** JR-01 eliminated the shared bootstrap error but **9 independent assertions still fail** (honey 1, milk 2, water bottle 5, water bucket 1). This is not full CI acceptance until JR-10/JUNIT-09.
