# M4 implementation evidence — 2026-11-07

## Current state

The M4 code is implemented, but **M4-01–M4-17 remain open** where runtime, model, or phone evidence is required. Live detection is deliberately disabled in this build. The scanner entry point explains that the detector is unavailable and preserves the manual journey/details path. It does not request camera permission while the parity gate is closed.

The provisional rules are `scanner-v1`: two agreeing observations within 1,000 ms; a result expires 1,000 ms after the last valid observation. These values appear in About and are recorded for M5-01 review; they are not frozen evaluation criteria.

## Model evidence and gate

The local asset `app/src/main/assets/route-sign-yolov8n.onnx` is ignored by Git and has SHA-256 `183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8` (12,620,173 bytes). The release APK includes this asset. It is versioned in About as **YOLOv8n fine-tuned M3 demo-v3; provisional**. This is not a selected or phone-ready detector.

The existing M3 evaluation found 0 correct detections for 3 labeled test signs. The saved PyTorch and ONNX Runtime CPU outputs cover the same four samples: image 03 has three detections in each output, and the other three images have none. Comparing the saved post-NMS rows shows a maximum score-ordered box coordinate difference of 18.144775 px and score difference of 0.12714114, against 1.5 px / 0.02 tolerances. This confirms the recorded mismatch but does not isolate its cause: raw model tensors and exact preprocessed input tensors were not retained, so the saved rows cannot show where the divergence entered. The local Python environment also lacks ONNX Runtime, so the export comparison was not rerun here. Tolerances were not changed. See [M3 results](../m3/results-2026-11-07.md).

`LIVE_SCANNER_ENABLED` in `MainActivity` remains `false` until the same-input PyTorch/ONNX mismatch is understood and parity passes. The bundled pipeline still uses the recorded provisional settings: 320×320 RGB letterbox, FP32 input, NMS IoU 0.7, and 0.25 score cutoff. The app does not substitute generic COCO weights.

## Implementation coverage

| Scope | Implementation and evidence | Still needed |
| --- | --- | --- |
| M4-01, M4-07 | Scanner copy separates sign-confirmed headsign from user-confirmed direction; destination is not used to infer direction. Conflicting route/headsign evidence clears a prior confirmation. | Review the wording and safe-use procedure on the phone. |
| M4-02, M4-15 | Lazy permission launcher, lifecycle rebinding, fresh scanner sessions, failure fallback, and retained manual journey are implemented. On the Tecno, instrumentation passed the unavailable-path test: journey preserved and camera permission unchanged. | Runtime grant/deny/revoke, camera unavailable, pause/resume, and camera/OCR fault walkthrough. The closed model gate currently bypasses the camera path. |
| M4-03 | CameraX uses `STRATEGY_KEEP_ONLY_LATEST`; analysis is serialized, `ImageProxy` is closed on every branch, and only one OCR crop is in flight. Capture-to-display aggregates are recorded in memory. | Camera load, dropped-frame behavior, and real capture-to-display timing on device. |
| M4-04 | YUV frames are rotated/cropped to upright coordinates; boxes are unletterboxed/clipped and mapped to the fill-center preview. OCR receives a copied crop before the camera frame closes. Geometry and clipping have unit fixtures. | Camera orientation and crop/overlay comparison on device. |
| M4-05 | One target is selected by outline or unambiguous centered candidate; nearest-box association rejects lost/ambiguous matches and starts new evidence for a new target. Association fixtures pass. | Overlapping-sign replay and actual target movement/loss on device. |
| M4-06 | Route lookup covers the whole catalog, exact complete aliases and route codes with token boundaries; no fuzzy match. Unit cases include ambiguity, Route 30, and unrelated number 123. | Real OCR and validation-set threshold evidence; current model is gated off. |
| M4-08–12 | Match, no-match, confirm-direction, unreadable, unsupported, uncertain, and neutral identification outcomes have separate text/icon and color branches plus a manual action. Unit tests cover all seven states and conflicting evidence. | Full result UI and actual OCR decision walkthrough; unavailable while model gate is closed. |
| M4-13 | The neutral Explore scanner entry passed on the Tecno and showed no compatibility context. | Recognized route details and Plan a journey after live OCR remain untested while scanning is disabled. |
| M4-14 | Two-observation window, expiry, target/session/context rejection, and clearing on lost targets are implemented and covered by JVM tracker tests. | Validate timing and journey/target changes on device; review settings in M5-01. |
| M4-16 | Detector and bundled Latin OCR dependencies are included locally; merged manifest removes INTERNET and ACCESS_NETWORK_STATE. | Fresh airplane-mode install and first-use OCR/inference on device. |
| M4-17 | Frame/crop/OCR data remain in memory; there is no app code that stores or logs frames/OCR. A user action shares only schema/version fields and aggregate counts/latencies. | Inspect storage/network/log behavior on device and review an exported summary. |

## Checks run

JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.0, Kotlin 2.2.10, compile/target SDK 36, minimum API 34. Android SDK environment variables were set to `%LOCALAPPDATA%\Android\Sdk`.

| Check | Result |
| --- | --- |
| `.\gradlew.bat assembleDebug testDebugUnitTest` | **PASSED** — debug APK built; 20 JVM tests passed. |
| `.\gradlew.bat compileDebugAndroidTestKotlin` | **PASSED** — UI and inference instrumentation sources compile. |
| `.\gradlew.bat connectedDebugAndroidTest` — first attempt | **COULD-NOT-RUN** — before USB debugging became available, Gradle reported `No connected devices!`. |
| `.\gradlew.bat connectedDebugAndroidTest` — Tecno KJ8, Android 14/API 34, incremental 250108V1200, patch 2024-12-05 | **PASSED** — 13 tests, 0 failures/errors/skips. Included Results → Scan → Manual → Details journey retention, neutral Explore entry, About versions, unchanged camera permission on unavailable fallback, manifest permissions, and `bundledDetectorLoadsAndRunsOnDevice` on a synthetic gray image. |
| `.\gradlew.bat assembleRelease` | **PASSED** — unsigned release APK built; release lint-vital passed. |
| Merged debug/release manifest inspection | **PASSED** — CAMERA is present and camera hardware is optional; INTERNET and ACCESS_NETWORK_STATE are absent after dependency permissions were removed. |
| Model package inspection | **PASSED** — local ONNX asset is present in the release APK at 12,620,173 bytes. |

The unsigned release APK SHA-256 is `55D7DE8B21055479F2C28FF9B4CEA4F555792B655ED3E335D044FA89BE54A0CD`; it is approximately 205 MB because it bundles the detector/OCR runtime and multiple native libraries. This local APK is not a phone-accepted release.

## Device procedure still open

The Tecno KJ8 is now connected and the instrumentation results above are recorded. Those tests do not exercise live camera frames because `LIVE_SCANNER_ENABLED` is still false. First resolve and rerun parity without relaxing tolerances; only enable the scanner after parity passes. Then install an airplane-mode fresh build and record each remaining step as PASSED, FAILED, or COULD-NOT-RUN with device/app/model versions:

1. Plan → Results → Scan → Details → Manual, confirming the selected journey remains available.
2. Scan neutrally from Explore; verify route details and Plan a journey without green/red compatibility.
3. Grant, deny, revoke, and restore camera permission; check missing/corrupt model fallback.
4. Select one sign, move it out of view, pause/resume, and confirm old OCR/results cannot return.
5. Measure capture-to-display time and inspect offline first launch, storage, logs, and network behavior.

No M3 training/comparison, commit, push, or publication was performed.
