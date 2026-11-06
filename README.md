# ParaPo

ParaPo is an Android prototype for checking whether one verified directional jeepney route can take a commuter directly between two selected locations. The M2 build provides an offline manual planner; it does not include sign recognition.

## Current implementation

The single-module Kotlin app includes Home, Results, Route Details, Explore, and About / Data screens. It resolves curated names and aliases, reports ambiguous or unsupported locations, returns every eligible same-pattern direct journey, and shows a local route diagram with an ordered stop list. A Scan action explains that recognition is unavailable and retains the selected journey.

The release catalog is intentionally empty. No authorized operational route records or approved reverification policy were available for this implementation, so the app cannot provide commuter route recommendations. Test-only synthetic routes are under `app/src/test` and `app/src/androidTest`; they are not bundled into the release. The app requests neither camera nor network permission, and no recognition model is included.

## Toolchain

- JDK 17
- Gradle 8.11.1 through the checked-in Windows wrapper
- Android Gradle Plugin 8.10.0
- Kotlin Android plugin 2.2.10
- Android SDK platform 36; minimum Android version API 34

Set `ANDROID_HOME` to the Android SDK directory and `ANDROID_SDK_ROOT` to the same value. For a standard PowerShell SDK installation:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

The first Gradle run may download the pinned plugins and test libraries.

## Build and test

From the repository root, build the debug app, run JVM tests, and run instrumentation tests on a connected Android API 34+ emulator or phone:

```powershell
.\gradlew.bat clean assembleDebug testDebugUnitTest connectedDebugAndroidTest
```

To build the release variant:

```powershell
.\gradlew.bat assembleRelease
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; the release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`. Instrumentation tests use synthetic catalogs injected by the test application and cover manual navigation, selected-journey retention, permissions, and route-bundle validation/recovery.

The planner compares directed `(patternId, sequence)` adjacency-list BFS against an independent ordered-list scan. The implementation notes and host-JVM measurements are in [M2 implementation evidence](docs/evidence/m2/implementation-2026-11-06.md). Those synthetic timings do not replace target-phone measurements.

## Route data and offline behavior

An authorized route bundle is a ZIP containing `manifest.json` and `routes.sqlite`. The store checks the manifest, SHA-256 checksum, SQLite integrity and foreign keys, schema version, record references, ordered stops, coordinates, geometry anchors, source provenance, and pattern verification before replacing the installed database. A rejected update leaves the last valid database available, including after restart.

No operational bundle is present in this repository. Route-data owners must supply authorized records and approve the reverification/invalidation policy before operational coverage can be added. Until then, the app clearly reports unavailable verified coverage and returns no route recommendations.

## Project documents

- [Product requirements](docs/PRD.md)
- [Research brief](docs/research-brief.md)
- [Original concept](docs/original-concept.md)
- [Phase task tracker](docs/phasetask.md)

## Limitations and next work

This M2 build does not identify vehicles, use a camera, access a network map, infer reverse travel, support transfers, or establish live route accuracy. The Tecno KJ8 (Spark 20 Pro 5G) Android 14/API 34 build has been inspected, and synthetic UI/recovery checks passed on the phone while offline. A known operational journey and offline use with an authorized route bundle remain untested because no verified route data is available. Operational planning requires authorized directional route records, independent verification, and owner approval.
