# Camera preview and street map — 2026-11-08

## What is available

- Scan requests camera permission when needed and opens the back-camera CameraX `PreviewView`. Permission, preview binding, and lifecycle do not depend on the detector. Recognition remains unavailable because the latest experimental v10 ONNX export failed PyTorch parity; no v10 test-set inference was run. No old boxes or compatibility decisions are shown. Manual route results and details remain available.
- MapLibre Native Android 13.7.1 renders the OpenFreeMap Liberty street style (`https://tiles.openfreemap.org/styles/liberty`) with OSM-derived roads and place labels. The public style needs no key or paid account. OpenFreeMap's mobile guide documents the MapLibre integration: [OpenFreeMap quick start](https://openfreemap.org/quick_start/), [MapLibre Android quick start](https://maplibre.org/maplibre-native/android/examples/getting-started/).
- INTERNET and ACCESS_NETWORK_STATE support map tiles and recovery. The app does not ask for location and has no camera-frame upload path. Nearby attribution names OpenStreetMap contributors, ODbL, OpenMapTiles, and OpenFreeMap, with a link to [OSM copyright/licensing](https://www.openstreetmap.org/copyright). See [OSMF attribution guidance](https://osmfoundation.org/wiki/Attribution_Guidelines).
- Results show all eligible directional paths, route labels/colors, journey markers, and route-card highlighting. Details shows only the selected direction and the segment between selected stop occurrences. Both maps pan and zoom inside the scrolling layout; the view camera is saved separately for Results and Details across navigation/recreation. Map status and Retry explain tile failures. Ordered stops and local route geometry remain usable if tiles cannot load.
- No tile pre-download or complete offline basemap is provided. OpenFreeMap documents limits on offline downloads and requires an identifying User-Agent for those downloads; ParaPo does not claim offline tile coverage.

## Geometry and data update

The source is [the supplied Route 3 and Route 10 maps](source-maps/README.md), their directional street sequences, and OpenStreetMap road-node coordinates checked 2026-11-08. Route 3 follows the documented E. Lopez/Luna streets. Route 10 follows Commission Civil/Del Carmen and the mapped WVSU connector. Outgoing and returning paths remain distinct. Reverse Route 3 no longer draws the out-of-scope La Paz loop after WVSU. Stable pattern/location/stop-occurrence IDs, boarding permissions, and direct-journey rules are retained. GeoJSON boundary conversion is checked as longitude then latitude.

Amber dashed connectors deliberately show the sections that are not established by the available sources: (1) the Jaro Cathedral-area board connector to Route 3 at Jalandoni/Plaza Rizal and its Jaro Plaza return loop; (2) the Route 10 Jaro Plaza approach to Commission Civil and the short Commission Civil-to-Del Carmen join between `10.7201345, 122.5622104` and `10.7188543, 122.5635263`. The exact gaps and required source detail are recorded in the [source-map note](source-maps/README.md). These paths cover only the Jaro Plaza–WVSU corridor and are not turn-by-turn guidance; no independent field observation was made.

The source catalog and packaged bundle are versioned `iloilo-jaro-wvsu-2026-11-08`. On startup the app validates the packaged bundle and updates an older installed catalog through `RouteBundleStore.replaceBundle`. Older data, conflicting same-date data, and same-version/different-checksum data cannot roll back the installed catalog; failed replacement keeps the last valid catalog. The APK update was installed with `adb install -r`, without uninstalling or clearing app data. About / Data showed the new dataset version.

## Acceptance checks

Device: Tecno KJ8, Android 14/API 34, connected over USB debugging.

| Check | Result | Actual result |
| --- | --- | --- |
| 1. Scan permission and live preview | **PASSED** | First Scan showed Android's camera permission dialog. The original 10-second movement check used the then-current front-camera selector; the user reported that lens mismatch and it was corrected below. A temporary clip was deleted after review. |
| 2. Preview while recognition is unavailable | **PASSED** | Preview remained usable with “Camera ready. Route recognition is currently unavailable.” No stale detection box or compatibility decision appeared. |
| 3. Denial, manual planning, and recovery | **PASSED** | Denying the first and second requests retained manual planning; after the repeated denial the UI offered Open app settings. Camera permission was granted in Android Settings and Scan resumed. Permission revocation was also exercised; Android restarted the process and a fresh permission request/recovery path worked. |
| 4. Leave/reopen and background/resume | **PASSED** | Scan was left for Results, Details, and Explore and reopened; HOME/background and return to the app restored the saved journey and return screen. Preview was rebound on return and released when leaving Scan. Explore remained neutral. |
| 5. Both supported route directions | **PASSED** | Jaro Plaza → WVSU and WVSU → Jaro Plaza were planned. Routes 3 and 10 appeared with separate direction labels; each forward/reverse Details screen showed the appropriate boarding/alighting names and route segment. Dashed gaps remain visibly unresolved as documented above. |
| 6. Select, inspect, pan/zoom, return | **PASSED** | Selecting a route highlighted its path and corresponding markers. Details showed the selected route; map pan and double-tap zoom worked inside the scrolling page. Back returned to Results with Route 3 still selected and its view restored. Results also rendered after a device landscape rotation. |
| 7. Connectivity loss and recovery | **PASSED** | Wi-Fi and mobile data were disabled; Android reported no active default network. The map displayed an offline/cached-tile warning and Retry while route paths, markers, cards, ordered stops, and schematic stayed usable. Restoring connectivity and retrying cleared the warning and loaded tiles. Cached streets are not full offline coverage. |
| 8. Update an existing install | **PASSED** | The normal APK was installed over the existing package with `adb install -r`; app data was not cleared. About / Data showed `iloilo-jaro-wvsu-2026-11-08` after update. |
| Unavailable camera hardware and inference-fault injection | **COULD-NOT-RUN** | Camera hardware failure was not simulated. Recognition is intentionally disabled because model parity is unresolved, so a live inference error could not be triggered; preview-only operation and stale-result clearing were checked. |

## Follow-up: back-camera correction

After the user reported that Scan opened the front camera, the CameraX selector was changed to `CameraSelector.DEFAULT_BACK_CAMERA`. `assembleDebug testDebugUnitTest` passed (22 tests), all 21 isolated connected tests passed on TECNO KJ8 / Android 14, and the normal debug APK was rebuilt without the isolated-test property and installed using `adb install -r` (app data preserved). Scan showed the ready preview. Android's camera service reported the active ParaPo client on camera ID 0; that device's camera characteristics report camera ID 0 as `Facing: Back` and `android.lens.facing: BACK`. The earlier retained preview still and movement clip predate this correction; no new rear-camera clip was retained.

## Automated and build checks

| Check | Result |
| --- | --- |
| `python tools/build_route_bundle.py` | **PASSED** — generated `iloilo-jaro-wvsu-2026-11-08` with four directional patterns. |
| `.\gradlew.bat assembleDebug testDebugUnitTest` | **PASSED** — 22/22 unit tests, 0 failures; includes route coordinate ordering and geometry helpers. |
| `.\gradlew.bat -PparapoIsolatedUiTest connectedDebugAndroidTest` | **PASSED** — 21/21 tests on TECNO KJ8 / Android 14, 0 skipped, 0 failed. Isolated package used. |
| `.\gradlew.bat assembleDebug testDebugUnitTest assembleRelease` | **PASSED** — normal debug and unsigned release APKs built; the 22 passing unit tests were up-to-date. |
| Final normal APK install | **PASSED** — `adb install -r`, package `com.parapo.prototype`, versionCode 2, existing app data preserved. |
| `git diff --check` | **PASSED** — no whitespace errors; Git reported only its standard LF-to-CRLF working-copy notices. |

The installable normal debug APK is `app/build/outputs/apk/debug/app-debug.apk`. No commit or push was made.

## Phone evidence

- [Preview-only screen](screenshots/cropped/tecno-scanner-preview.png) and [preview movement check](screenshots/cropped/tecno-scanner-preview-motion-check.png) (both captured before the back-camera correction above)
- [Permission recovery](screenshots/cropped/tecno-scanner-permission-recovered.png) and [preview after resume](screenshots/cropped/tecno-scanner-resume.png)
- [Street map with labels and markers](screenshots/cropped/tecno-results-labeled.png), [selected route](screenshots/cropped/tecno-results-selected.png), and [reverse journey results](screenshots/cropped/tecno-results-reverse-final.png)
- [Route 3 forward details](screenshots/cropped/tecno-details-route3.png), [Route 10 forward](screenshots/cropped/tecno-details-route10-forward.png), [Route 10 reverse](screenshots/cropped/tecno-details-route10-reverse.png), and [Route 3 reverse after the camera-state fix](screenshots/cropped/tecno-details-route3-reverse-final.png)
- [Pan and zoom](screenshots/cropped/tecno-details-pan-zoom.png), [selected route restored on return](screenshots/cropped/tecno-results-returned-route3.png), and [offline fallback](screenshots/cropped/tecno-offline-fallback.png)

The images linked above are presentation crops with only the Android status and system-navigation bars removed. Original source captures remain unmodified beside them.

Older screenshots and evidence are preserved; only the files linked above are cited for this implementation. The recognition model remains unready, exact Route 3/10 joins remain unresolved, and fresh basemap tiles require network access.
