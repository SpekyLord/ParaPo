<p align="center">
  <img src="docs/branding/parapo-logo.svg" width="128" alt="ParaPo route logo">
</p>

<h1 align="center">ParaPo</h1>

<p align="center"><strong>Plan a direct jeepney ride across Jaro Plaza ↔ WVSU.</strong><br>
A small Android school demo for route planning in Iloilo City.</p>

<p align="center">
  <img alt="Android API 34+" src="https://img.shields.io/badge/Android-API%2034%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin 2.2.10" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white">
  <img alt="School prototype" src="https://img.shields.io/badge/Project-school%20demo-FAAD21">
</p>

ParaPo lets someone choose two places and see which of the supported jeepney routes serves the trip directly. It combines a local route catalog, a live camera preview, and an interactive street map. There is no GPS tracking.

## At a glance

| | What works |
| --- | --- |
| **Plan** | Choose a start and destination manually. See eligible directions for Routes 3 and 10. |
| **Scan** | Open a live **back-camera** preview after granting camera permission. Preview works independently of route-sign recognition. |
| **Explore** | Browse the supported route catalog without selecting a journey. |
| **Map** | Pan and zoom street maps in Results and Route Details; select a route to highlight its path and journey stops. |

<p align="center">
  <img src="docs/evidence/m2/screenshots/tecno-results-labeled.png" width="560" alt="ParaPo route results showing Routes 3 and 10 over Iloilo streets with boarding and alighting markers">
</p>

### Demo status

- **Camera preview:** works on the Tecno KJ8 and uses its back-facing camera. Camera permission is requested only when opening Scan. Frames stay on the device.
- **Recognition:** unavailable. The latest experimental ONNX export does not match its PyTorch reference, so ParaPo does not show sign detections or compatibility decisions. Manual planning and route details remain available.
- **Map:** MapLibre Native renders OpenFreeMap Liberty tiles, with streets and place labels. Fresh tiles need internet; a Retry action appears when the map cannot load.
- **Coverage:** only the Jaro Plaza–WVSU corridor on Routes 3 and 10, in four directional patterns. Dashed amber joins are unresolved source-map sections, not verified roads.

## Route coverage

The bundled catalog supports these journeys:

| Route | Jaro Plaza → WVSU | WVSU → Jaro Plaza |
| --- | --- | --- |
| **3** | Supported | Supported |
| **10** | Supported | Supported |

Results display eligible paths together. Choose a route to highlight it; Details shows that direction between the selected boarding and alighting stops. Stop order and route geometry remain in the local catalog.

The route street sequences follow the supplied Iloilo City Planning maps, with road geometry traced against OpenStreetMap data checked on 2026-11-08. Available sources do not establish the exact Route 3 Jaro Plaza boarding/return joins or the Route 10 Plaza approach and short Commission Civil–Del Carmen join. Those sections stay dashed and are described in the [source-map note](docs/evidence/m2/source-maps/README.md). This is corridor planning, not turn-by-turn guidance or a full route catalog.

## Map, network, and privacy

- Map tiles come from the [OpenFreeMap Liberty style](https://tiles.openfreemap.org/styles/liberty), rendered with [MapLibre Native for Android](https://maplibre.org/maplibre-native/android/). No map key or paid account is required for this demo setup.
- Map attribution is shown in the app and names OpenStreetMap contributors, OpenMapTiles, and OpenFreeMap. See [OpenStreetMap copyright and license](https://www.openstreetmap.org/copyright).
- Android `INTERNET` and `ACCESS_NETWORK_STATE` permissions are used for tiles and retry status. Camera permission is requested at Scan. Location permission is not used.
- Camera frames are not uploaded or saved by ordinary app use. If tiles are unavailable, route cards, markers, ordered stops, and the local schematic remain available. Cached tiles may be incomplete; there is no full offline basemap.

## Build and run

### Requirements

- Windows with JDK 17
- Android SDK platform 36 and a connected Android 14 / API 34+ device for phone checks
- The checked-in Gradle wrapper supplies Gradle 8.11.1

Set the Android SDK location in PowerShell, then build:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

.\gradlew.bat assembleDebug
```

Install or update the normal debug build on a connected phone:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The `-r` update keeps existing app data. The APK is a universal debug APK and includes native libraries for several device types.

### Checks

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat -PparapoIsolatedUiTest connectedDebugAndroidTest
.\gradlew.bat assembleDebug assembleRelease
```

The isolated connected-test package uses `com.parapo.prototype.uitest`; it keeps tests that modify route data away from the normal phone installation. Rebuild `assembleDebug` without the isolated-test property before installing the demo APK. On the Tecno KJ8 / Android 14, the 2026-11-08 run passed 22 unit tests and 21 connected tests. See [camera and map test evidence](docs/evidence/m2/map-camera-2026-11-08.md).

### APKs

- Normal installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Route data

Editable catalog source: `data/iloilo-jaro-wvsu.json`. Rebuild the packaged SQLite bundle after reviewing a route-data edit:

```powershell
python tools/build_route_bundle.py
```

Current dataset: `iloilo-jaro-wvsu-2026-11-08` (four directional patterns). On app startup, the validated packaged bundle upgrades an older installed catalog through `RouteBundleStore.replaceBundle`; an older bundle cannot replace newer data, and a failed update retains the last valid catalog.

## Recognition experiment

Recognition is kept off until the current model passes export parity. The latest v10 validation used only three photos; there is no v10 test-set inference, and the ONNX/PyTorch outputs differ. See [M3 results](docs/evidence/m3/results-2026-11-07.md) and [M4 implementation notes](docs/evidence/m4/implementation-2026-11-07.md).

An ignored local model asset is optional for building the manual planner and live camera preview. To include the experimental model in a local build, copy the reviewed export:

```powershell
Copy-Item datasets\m3-samples\runs\yolov8n-reviewed-20ep\weights\best.onnx app\src\main\assets\route-sign-yolov8n.onnx
```

Expected SHA-256: `183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8`. Keep the recorded parity checks; do not loosen thresholds to make the export appear ready.

## Project notes

- [Figma design](https://www.figma.com/design/eTi2eZQ5W8FU6JJOTPUbjR/ParaPo?node-id=6-3) and [design implementation](docs/design-implementation.md)
- [Product requirements](docs/PRD.md) · [Research brief](docs/research-brief.md) · [Phase task tracker](docs/phasetask.md)
- [Route source maps and geometry gaps](docs/evidence/m2/source-maps/README.md) · [Phone screenshots and acceptance results](docs/evidence/m2/map-camera-2026-11-08.md)

ParaPo is a classroom prototype, not a production transit or navigation service. Schedules, transfers, other Iloilo routes, complete route geometry, sign recognition, and complete offline map coverage are outside the current demo.
