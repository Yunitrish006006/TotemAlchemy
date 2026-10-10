# TotemAlchemy — JUnit failure recovery (one task per checkpoint)

**Branch:** `feature/m11-t01-signature-brew-resolver`  
**PR:** #120 (Draft).  
**Paused feature work:** M11 transaction finalization / mailbox; no further functionality until the JUnit baseline is green.

## Verified starting point

- Build #412: **259 JUnit, 44 failed**, 111/111 main server GameTests passed.
- Build #418: liquid normalization fixed; **259 JUnit, 43 failed**, 111/111 main GameTests passed.
- Build #419: **259 JUnit, 43 failed**. All remaining failures originate in `Holder.Reference.components()` with `NullPointerException: Components not bound yet`; Fabric Loader/Jupiter test runtime is now working but the vanilla 26.3 item components are not bound for tests.
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
| **JR-01** | **IN PROGRESS** | **Minecraft 26.3 JUnit bootstrap:** ensure Fabric Loader and actual vanilla deferred item components are bound in the JUnit test worker | Shared root cause of 43 | Focused canary creates vanilla + Totem `ItemStack` without `Components not bound yet`; no registry fallback hacks in production |
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
- [ ] Add a **focused bootstrap canary** that verifies the component holders are actually bound, so a silent partial initialization fails immediately and clearly.
- [ ] Correct the real init/binding gap using vanilla 26.3 APIs rather than allowing fabricated output items or bypassing component checks.
- [ ] Confirm the canary and dependent adapter tests by CI; mark JR-01 DONE, then assess subsequent test classes.

**Limitation:** even a green isolated canary is not proof of complete CI until JR-10. The broken baseline is in the JUnit JVM, not the server GameTest runtime.
