# ParaPo M2 implementation evidence

- **Evidence date:** 2026-11-06
- **Source baseline:** M2 implementation branch `codex/implement-parapo-m2`, based on `f5cedcd`; route data and evidence changes are included in the current delivery.
- **Application:** `com.parapo.prototype`, version `0.1.0` (version code 1).
- **Route data:** `iloilo-jaro-wvsu-2026-11-06`, two approved corridors represented as four active directional patterns; SQLite v1 bundled in the release APK.

## Implemented

- A single-module Kotlin Android app with native Android views and an XML screen shell. Home, Results, Route Details, Explore, and About / Data are present. The route detail diagram uses local geometry and the ordered stop list remains available as text.
- Manual location resolution has missing, unsupported, unique, ambiguous, and identical-endpoint handling. Direct results keep their route pattern and boarding/alighting occurrences; Scan opens an unavailable-recognition explanation while retaining that journey.
- The planner uses directed `(patternId, sequence)` adjacency and BFS. `OrderedScanBaseline` independently scans preordered stop lists. Inactive and unverified patterns are excluded.
- A versioned ZIP bundle contract with `manifest.json` and `routes.sqlite`, SHA-256 verification, SQLite schema/integrity/foreign-key checks, strict numeric/boolean decoding, catalog validation, staged installation, atomic replacement, and backup recovery.
- A limited Jaro Plaza–WVSU route catalog for Iloilo Routes 3 and 10, in both mapped directions. Source maps and hashes are preserved in `docs/evidence/m2/source-maps/`. Synthetic route data remains test-only. The app requests neither camera nor internet permission, and no recognition model is included.

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

**Final results:** `clean assembleDebug testDebugUnitTest connectedDebugAndroidTest` succeeded, including 11 JVM tests and 10 connected instrumentation tests on the TECNO KJ8. Release assembly and lint-vital succeeded. The unsigned release APK is not a store-ready delivery. The earlier Pixel AVD run completed 8 instrumentation tests before the real-route UI assertions were added.

The final connected-phone run used the TECNO KJ8 on Android 14/API 34 with no active default network. All 10 instrumentation tests passed, including real-bundle searches in both directions, Explore, About/Data, bundle validation/recovery, permission checks, synthetic planner cases, and on-device benchmarks. A fresh debug install then loaded the actual bundled route data. An ADB-driven on-device walkthrough with Wi-Fi and mobile data service disabled searched Jaro Cathedral → WVSU, opened route results and Route 10 details, and opened the Scan fallback with the selected journey retained. This is phone UI evidence, not independent field observation.

| Artifact | Local output | SHA-256 |
| --- | --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `8FDF99596715F83530FE5E8302F1C86E2299E8CA0DA2BADA676BC341F241BBB9` |
| Unsigned release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | `CF818AA306E68F1FE0C77A217132B4F908BA5B7C2021F9302844CFF696D299B0` |
| Route bundle | `app/src/main/assets/routes.bundle` | `D55CA576D90F95DE941E6E13A4D9A1758CA010811AE188D600F3698BDF07DBF9` |
| SQLite payload checksum | `manifest.json` → `sqliteSha256` | `61035F47264379D600068D6BF9E3A12EC3FBF6298A741A37BDF64C1E2E58B4B8` |

The release APK contains the exact checked-in `assets/routes.bundle`. Release DEX was scanned for `SYN-UI`, `SYN-FWD`, `SYN-ROUTE`, `synthetic-test-only`, and `Synthetic test fixture`; none were present. `aapt dump permissions` listed only the package name, with no requested permissions. Instrumentation also verified that camera and internet are absent from the app's requested-permission list.

## Automated evidence

JVM reports are under `app/build/test-results/testDebugUnitTest/`. The current physical-device instrumentation report is `app/build/outputs/androidTest-results/connected/debug/TEST-TECNO KJ8 - 14-_app-.xml`; the previous Pixel AVD run completed 8 tests before the phone benchmark test was added.

- Planner and independent-baseline comparisons pass for `SYN-FWD`, `SYN-REVERSE`, `SYN-REPEAT-LATE`, `SYN-NO-BOARD`, `SYN-NO-ALIGHT`, `SYN-SHARED`, `SYN-BRANCH`, `SYN-LOOP-TERMINAL`, and `SYN-MULTI-PATTERN`. Tests also cover stable multi-pattern ordering, prohibited boarding/alighting, inactive/unverified patterns, and identical endpoints.
- Location tests cover missing, unsupported, unique, and ambiguous names, plus a repeated alias on one location.
- Catalog validation tests cover duplicate IDs, missing references, invalid coordinates and ordering, incomplete stop/permission coverage, geometry anchors, and missing provenance.
- Android bundle tests install a valid synthetic database, then reject checksum mismatch, malformed SQLite, broken references, invalid and nonnumeric coordinates/sequences, incomplete patterns, invalid geometry anchors, duplicate/incomplete provenance, malformed/interrupted input, and damaged updates. They verify the prior valid data remains readable after rejection/restart and backup recovery.
- UI instrumentation covers empty-catalog messaging, unknown locations, Explore/About, permission absence, and the actual bundled patterns in both directions through Results, Details, and the real-data Explore screen.
- The final Tecno walkthrough used the installed release-scope route catalog while Android reported no active default network; the route diagram, ordered stop text, provenance, and Scan fallback all appeared. The interaction was driven over ADB rather than by the user's physical touch, and it does not verify live service or curbside stop operations.

### Test attempt history

The first API 37 UI instrumentation attempt used Espresso and failed because its input-method hook called the unavailable `android.hardware.input.InputManager.getInstance` method. A first run also lost the app process under the AVD's default 2 GiB memory. The UI test was changed to use `ActivityScenario` with native view actions, Espresso was removed, and the AVD was restarted with 4 GiB. A later route-source wording change left one stale test assertion; the next clean run passed all 10 Tecno instrumentation tests. An OEM lock-screen overlay prompt appeared during an earlier first-launch attempt and was not granted; it did not appear during the final fresh install. ParaPo requests no overlay or camera permission.

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

## Acceptance scope

The tracker records M2-01–M2-17 as complete for the approved Jaro Plaza–WVSU corridor and the DSA prototype. The project lead is the route-data steward; the documented policy rechecks sources at least every 90 days, before each release, and on a route-change report. Conflicting or unconfirmed patterns are disabled pending review.

This does not establish complete service coverage for Routes 3 or 10, exact curbside boarding locations, service schedules/frequency, temporary detours, or independent agency endorsement beyond the supplied City Planning maps and LTFRB route source. The route drawing is schematic. A Tecno OEM overlay prompt was not granted; no such permission is requested by the app. The release APK is unsigned.
