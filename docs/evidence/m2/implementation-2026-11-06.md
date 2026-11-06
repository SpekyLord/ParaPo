# ParaPo M2 implementation evidence

- **Evidence date:** 2026-11-06
- **Source baseline:** `main` at `082a9e283d4848567db5d43c5555183323f14936`; implementation files are uncommitted.
- **Application:** `com.parapo.prototype`, version `0.1.0` (version code 1).
- **Route data:** no operational dataset or bundle. Dataset version shown by the app is `none`.

## Implemented

- A single-module Kotlin Android app with native Android views and an XML screen shell. Home, Results, Route Details, Explore, and About / Data are present. The route detail diagram uses local geometry and the ordered stop list remains available as text.
- Manual location resolution has missing, unsupported, unique, ambiguous, and identical-endpoint handling. Direct results keep their route pattern and boarding/alighting occurrences; Scan opens an unavailable-recognition explanation while retaining that journey.
- The planner uses directed `(patternId, sequence)` adjacency and BFS. `OrderedScanBaseline` independently scans preordered stop lists. Inactive and unverified patterns are excluded.
- A versioned ZIP bundle contract with `manifest.json` and `routes.sqlite`, SHA-256 verification, SQLite schema/integrity/foreign-key checks, strict numeric/boolean decoding, catalog validation, staged installation, atomic replacement, and backup recovery.
- A deliberately empty release catalog. Synthetic route data exists only in test source sets. The app does not request camera or network permission, and no recognition model is included.

## Toolchain and run environment

| Component | Recorded value |
| --- | --- |
| Host | Windows 11 Home Single Language 25H2, build 26200 |
| CPU / memory | Intel Core i5-13420H, 8 cores / 12 logical processors, 32 GiB RAM |
| Java | Eclipse Temurin JDK 17.0.18+8 |
| Gradle | Wrapper 8.11.1 |
| Android Gradle Plugin / Kotlin plugin | 8.10.0 / 2.2.10 |
| Android SDK | Compile/target 36, minimum 34; installed build tools 35.0.0 |
| Instrumentation target | `Pixel_7` AVD, Android 17 / API 37, emulator 36.4.10.0, launched with 4096 MiB RAM |
| Designated phone | Tecno Spark 20 Pro 5G per prior device record; ADB reports `TECNO KJ8` / `KJ8-OP`, Android 14 / API 34, build `UP1A.231005.007`, incremental `250108V1200`, security patch `2024-12-05`; fingerprint `TECNO/KJ8-OP/TECNO-KJ8:14/UP1A.231005.007/250108V1200:user/release-keys` |
| Phone constraints | MediaTek MT6833; arm64-v8a; 7,802,768 KiB RAM and 2,600,308 KiB available at inspection; Dalvik heap growth limit 256 MiB / max heap 512 MiB; `/data` 227 GiB total / 52 GiB free. Camera features include front/rear, autofocus, flash, and manual sensor/post-processing. |
| Phone test condition | USB debugging connected. Instrumentation ran with Wi-Fi and cellular data disabled and Android reporting no active default network; previous Wi-Fi/mobile-data settings were restored after the run. Battery was charging at 42%, 39°C during the check. |

`ANDROID_HOME` and `ANDROID_SDK_ROOT` were set to the installed Android SDK directory for the build. Commands run from the repository root:

```powershell
.\gradlew.bat clean assembleDebug testDebugUnitTest connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

**Final results:** the clean debug command succeeded, including 11 JVM tests and 8 instrumentation tests on the API 37 AVD. Release assembly and its lint-vital task succeeded. The release APK is unsigned and is not a store-ready delivery.

The connected-phone run used `ANDROID_SERIAL` set to the TECNO KJ8 and `.\gradlew.bat connectedDebugAndroidTest`. All 9 instrumentation tests passed on Android 14/API 34 while the device had no active default network. This included the synthetic manual journey through Results, Details, and Scan fallback; Explore/About screens; bundle rejection/recovery; permission checks; and on-device benchmarks. After the test runner removed its package, the debug APK was installed with fresh app data; its Home screen showed unavailable verified coverage and no synthetic route state.

| Artifact | Local output | SHA-256 |
| --- | --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `2D8F7BAAFED05AA13C7CAC808EE7B96A9D06F266460D9761C9E9E985D9D61C53` |
| Unsigned release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | `0EE5EBB7B045F0E35FFF3C7C06A5C2E889D6ACE6F7DEE5ACE1D35B831F2142F6` |

The release DEX was scanned for `SYN-UI`, `SYN-FWD`, `synthetic-test-only`, and `Synthetic Route`; none were present. The APK has no `assets/routes.bundle`. `aapt dump permissions` listed the package and no requested permissions. Instrumentation also verified that camera and internet are absent from the app's requested-permission list.

## Automated evidence

JVM reports are under `app/build/test-results/testDebugUnitTest/`. The current physical-device instrumentation report is `app/build/outputs/androidTest-results/connected/debug/TEST-TECNO KJ8 - 14-_app-.xml`; the previous Pixel AVD run completed 8 tests before the phone benchmark test was added.

- Planner and independent-baseline comparisons pass for `SYN-FWD`, `SYN-REVERSE`, `SYN-REPEAT-LATE`, `SYN-NO-BOARD`, `SYN-NO-ALIGHT`, `SYN-SHARED`, `SYN-BRANCH`, `SYN-LOOP-TERMINAL`, and `SYN-MULTI-PATTERN`. Tests also cover stable multi-pattern ordering, prohibited boarding/alighting, inactive/unverified patterns, and identical endpoints.
- Location tests cover missing, unsupported, unique, and ambiguous names, plus a repeated alias on one location.
- Catalog validation tests cover duplicate IDs, missing references, invalid coordinates and ordering, incomplete stop/permission coverage, geometry anchors, and missing provenance.
- Android bundle tests install a valid synthetic database, then reject checksum mismatch, malformed SQLite, broken references, invalid and nonnumeric coordinates/sequences, incomplete patterns, invalid geometry anchors, duplicate/incomplete provenance, malformed/interrupted input, and damaged updates. They verify the prior valid data remains readable after rejection/restart and backup recovery.
- UI instrumentation covers empty-catalog messaging, unknown locations, Explore and About / Data, a synthetic direct journey through details and Scan fallback, and permission absence.
- The same manual navigation and bundle recovery tests passed on the physical TECNO KJ8 with wireless networking disabled. These are automated UI/recovery checks using test-only fixtures, not human field observations or operational route validation.

### Test attempt history

The first API 37 UI instrumentation attempt used Espresso and failed because its input-method hook called the unavailable `android.hardware.input.InputManager.getInstance` method. A first run also lost the app process under the AVD's default 2 GiB memory. The UI test was changed to use `ActivityScenario` with native view actions, Espresso was removed, and the AVD was restarted with 4 GiB. The clean Pixel run completed 8 tests; the follow-up physical-phone run completed all 9 after adding the on-device benchmark. The earlier failure output was observed in the implementation session; the current physical-device Gradle report contains the passing rerun.

## Data-structure explanation and measurements

The planner sorts patterns and each pattern's stop occurrences once when it is created, then creates one node per `(patternId, sequence)` and directed edges only between consecutive occurrences. Adjacency storage is **O(V + E)**. A complete planner query scans the patterns and performs BFS only within each eligible pattern, so the traversal is **O(V + E)** across the catalog. Index preparation includes sorting: `O(P log P + Σ nₚ log nₚ)`, where `P` is the pattern count and `nₚ` is the number of occurrences in a pattern. The independent ordered baseline scans the preordered occurrences in **O(N)** per query after its own one-time ordering. Alias resolution uses an average-case **O(1)** hash-map lookup after normalization; returning multiple ambiguous candidates also sorts those candidates.

Measurements use the actual `JourneyPlanner.findDirectJourneys` and `LocationResolver.resolve` methods from the app module's JVM test. Catalog/index creation is outside the timed calls. Each case has five warm-up calls and 200 timed repetitions; the table reports median and 95th-percentile single-call time. Planner fixtures contain exactly the listed number of stop occurrences and one fewer directed edge. Alias fixtures contain exactly the listed number of canonical location-name keys. These are host-JVM timings, not Android emulator or Tecno timings.

| Operation | Synthetic input | Median | p95 |
| --- | ---: | ---: | ---: |
| Planner query | 100 stop occurrences | 0.0388 ms | 0.0920 ms |
| Planner query | 1,000 stop occurrences | 0.0894 ms | 0.1120 ms |
| Planner query | 10,000 stop occurrences | 0.5230 ms | 1.2858 ms |
| Alias lookup | 100 canonical names | 0.0070 ms | 0.0141 ms |
| Alias lookup | 1,000 canonical names | 0.0033 ms | 0.0045 ms |

The 1,000-name median is lower than the 100-name median, reflecting JVM and microbenchmark noise; the 10,000-stop p95 also includes scheduler/GC variation. Raw host measurements are in `app/build/test-results/testDebugUnitTest/TEST-com.parapo.prototype.routing.PlannerTest.xml`.

The physical-phone benchmark uses the same planner/resolver methods in `PlannerDeviceBenchmarkTest`, with five warm-up calls and 200 timed calls per size. Catalog/index construction is outside the timed calls. These results were taken while the phone was charging at 42% battery and reported 39°C battery temperature; there was no controlled thermal-conditioning interval.

| Operation | Synthetic input | TECNO KJ8 median | TECNO KJ8 p95 |
| --- | ---: | ---: | ---: |
| Planner query | 100 stop occurrences | 0.3363 ms | 0.4601 ms |
| Planner query | 1,000 stop occurrences | 0.4649 ms | 2.1028 ms |
| Planner query | 10,000 stop occurrences | 4.6089 ms | 8.2510 ms |
| Alias lookup | 100 canonical names | 0.0328 ms | 0.0495 ms |
| Alias lookup | 1,000 canonical names | 0.0348 ms | 0.0505 ms |

Raw phone measurements are in `app/build/outputs/androidTest-results/connected/debug/TECNO KJ8 - 14/logcat-com.parapo.prototype.routing.PlannerDeviceBenchmarkTest-measuresPlannerAndAliasLookupAtPlannedSizes.txt`. These are synthetic local-operation measurements, not full-dataset lookup performance or a release acceptance result.

## Acceptance still open

M1-02 and M2-03 are closed in the tracker using the phone inventory and build/test evidence above. All other M2 items remain open because operational coverage still requires authorized directional records and independent verification, and the route-data owner must approve reverification and invalidation policy. The physical-phone offline tests used synthetic test-only routes; they do not establish a known operational journey, service coverage, or field accuracy. The release catalog remains empty.
