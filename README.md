# ParaPo

ParaPo is a small Android school prototype for checking whether a verified jeepney route can take a commuter directly between two supported locations. The offline manual planner works in this build. A camera-scanner entry point is present, but live scanning is deliberately unavailable until the local detector export passes its PyTorch parity check; manual planning and route details remain available.

## Current scope

- The bundled catalog covers the Jaro Plaza–WVSU corridor on Iloilo City Routes 3 and 10, in four explicitly mapped directions. This is not a full city route catalog or a service schedule. Directions are based on the supplied Iloilo City Planning maps and LTFRB MC 2023-036; stop coordinates are attributed to OpenStreetMap contributors. Source maps are in [M2 evidence](docs/evidence/m2/source-maps/README.md).
- Results retain the selected journey when opening the scanner or route details. Explore opens neutral sign-identification context and has no journey compatibility result.
- The experimental fine-tuned YOLOv8n export is local and ignored by Git. Its recorded test result is 0 correct sign detections out of 3, and ONNX/PyTorch parity failed. It is not selected or phone-ready. The app therefore reports scanning unavailable instead of using it to guide a commuter.
- The M4 implementation contains CameraX frame handling, original-resolution crop/OCR, route and direction decisions, session/freshness checks, and manual fallback. Camera permission is optional at install time and is requested only by the scanner path when live scanning is enabled. The current build's parity gate prevents requesting the camera.
- The app has no network permission. OCR and detector dependencies are bundled; no frame or OCR text is saved or logged. A user can explicitly share a versioned JSON summary containing only versions and aggregate counts/timing measurements.
- The Android Views interface follows the [ParaPo Figma design](https://www.figma.com/design/eTi2eZQ5W8FU6JJOTPUbjR/ParaPo?node-id=6-3), with larger controls, catalog-backed place search, route cards and a text-only stop view. See [design implementation notes](docs/design-implementation.md) for adaptations and checks.

The detector is packaged only when the ignored local file exists. To prepare a local demo APK, copy the reviewed export into the asset path before building:

```powershell
Copy-Item datasets\m3-samples\runs\yolov8n-reviewed-20ep\weights\best.onnx app\src\main\assets\route-sign-yolov8n.onnx
```

Expected local model SHA-256: `183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8`. The app displays this version/hash and the M3 failure in About / Data. Preprocessing is 320×320 RGB letterbox, FP32 input, NMS IoU 0.7, provisional score cutoff 0.25. Do not loosen parity tolerances to make the export pass. See [M3 results](docs/evidence/m3/results-2026-11-07.md) and the [M4 implementation evidence](docs/evidence/m4/implementation-2026-11-07.md).

## Toolchain

- JDK 17
- Gradle 8.11.1 through the checked-in Windows wrapper
- Android Gradle Plugin 8.10.0
- Kotlin Android plugin 2.2.10
- Android SDK platform 36; minimum Android version API 34
- CameraX 1.5.3, AndroidX Activity 1.13.0, ONNX Runtime Android 1.31.1, bundled ML Kit Latin text recognition 16.0.1

Set `ANDROID_HOME` to the Android SDK directory and `ANDROID_SDK_ROOT` to the same value. For a standard PowerShell SDK installation:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

## Build and checks

Run from the repository root:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`; the unsigned release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`. Instrumentation needs a connected Android API 34+ emulator or phone. On 2026-11-07, all 13 connected tests passed on the designated Tecno KJ8 (Android 14/API 34), including scanner fallback/Explore context and a synthetic-image model inference smoke test. This does not verify live sign recognition or the camera path; the model remains gated off. See the M4 evidence note for details.

To rebuild the route bundle after editing its reviewed source, run:

```powershell
python tools/build_route_bundle.py
```

## Project documents

- [Product requirements](docs/PRD.md)
- [Research brief](docs/research-brief.md)
- [Original concept](docs/original-concept.md)
- [Phase task tracker](docs/phasetask.md)

## What remains

Resolve and rerun the ONNX/PyTorch comparison on identical inputs without changing its recorded tolerances. If parity passes, enable live scanning and check it on the designated Tecno KJ8 (Spark 20 Pro 5G, Android 14/API 34): Plan → Results → Scan → Details → Manual; neutral scanning from Explore; permission denial/recovery; target loss; pause/resume; offline first launch; and capture-to-display timing. The Tecno is now ADB-connected, but those live-camera checks remain open because scanning is gated off. Wider route coverage, reverse-direction inference, transfers, schedules, network maps, and public release remain outside this prototype task.
