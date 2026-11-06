# ParaPo

ParaPo is an Android prototype for checking whether one verified directional jeepney route can take a commuter directly between two selected locations. The M2 build provides an offline manual planner; it does not include sign recognition.

## Current implementation

The single-module Kotlin app includes Home, Results, Route Details, Explore, and About / Data screens. It resolves curated names and aliases, reports ambiguous or unsupported locations, returns every eligible same-pattern direct journey, and shows a local route diagram with an ordered stop list. A Scan action explains that recognition is unavailable and retains the selected journey.

The bundled release catalog covers only the Jaro Plaza–WVSU corridor on Iloilo City Routes 3 and 10, in the mapped directions. The project lead approved the corridor and confirmed the Jaro Plaza direction on 2026-11-06 using the City Planning route maps and LTFRB MC 2023-036. The supplied maps are preserved in [M2 source maps](docs/evidence/m2/source-maps/README.md); coordinates are attributed to OpenStreetMap contributors. This is limited corridor coverage, not a full Iloilo route catalog. Test-only synthetic routes are under `app/src/test` and `app/src/androidTest`; they are not bundled into the release. The app requests neither camera nor network permission, and no recognition model is included.

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

The planner compares directed `(patternId, sequence)` adjacency-list BFS against an independent ordered-list scan. The implementation notes and host/phone measurements are in [M2 implementation evidence](docs/evidence/m2/implementation-2026-11-06.md). Those synthetic timings do not replace target-phone measurements.

To rebuild the checked-in route bundle after editing the reviewed route source, run the standard-library-only data packager:

```powershell
python tools/build_route_bundle.py
```

## Route data and offline behavior

An authorized route bundle is a ZIP containing `manifest.json` and `routes.sqlite`. The store checks the manifest, SHA-256 checksum, SQLite integrity and foreign keys, schema version, record references, ordered stops, coordinates, geometry anchors, source provenance, and pattern verification before replacing the installed database. A rejected update leaves the last valid database available, including after restart.

The versioned source data is [data/iloilo-jaro-wvsu.json](data/iloilo-jaro-wvsu.json); `tools/build_route_bundle.py` validates and packages it as `app/src/main/assets/routes.bundle`. The app rechecks the manifest, SHA-256 checksum, SQLite integrity and foreign keys, schema version, references, ordered stops, coordinates, geometry anchors, provenance, and verification state before installing a bundle. Rejected updates preserve the last valid catalog, including after restart.

Project route-data policy: the project lead checks the cited Iloilo City / LTFRB sources at least every 90 days, before each data release, and whenever a route change is reported. Mark a conflicting or unconfirmed pattern unavailable until checked again. The local route diagram is schematic; it is not turn-by-turn navigation, a timetable, or a service-frequency guarantee.

## Project documents

- [Product requirements](docs/PRD.md)
- [Research brief](docs/research-brief.md)
- [Original concept](docs/original-concept.md)
- [Phase task tracker](docs/phasetask.md)

## Limitations and next work

This M2 build does not identify vehicles, use a camera, access a network map, infer reverse travel, support transfers, cover routes outside the Jaro Plaza–WVSU corridor, or establish service schedules. It contains two project-approved routes in four explicit directions. The Tecno KJ8 (Spark 20 Pro 5G) Android 14/API 34 was inspected; the route-enabled app was opened on the phone with Wi-Fi and mobile data disabled, and the results, details, and Scan fallback were exercised. The route source lists streets and landmarks; temporary detours or operator-specific boarding changes may not appear in it.
