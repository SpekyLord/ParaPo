# ParaPo Android design implementation

Reference: [ParaPo Figma screens](https://www.figma.com/design/eTi2eZQ5W8FU6JJOTPUbjR/ParaPo?node-id=6-3).

The Android Views activity renders planning and catalog-backed location search, route results, route details, Explore, About / Data, and Scan. A CameraX PreviewView uses the back camera independently of recognition; the current ONNX model stays disabled after its failed parity checks. Selected journey occurrences are used for map markers; Explore remains neutral.

The screen fills were used rather than the written Foundations labels: paper `#FAF7ED`, ink `#1F2124`, blue `#1F528C`, marigold `#FAAD21`, and white cards with `#D4D1C4` borders. Match, no-match, and neutral surfaces are `#E8F5EB`, `#FFEDEB`, and `#EBF2FA`. Atkinson Hyperlegible Next and Barlow Condensed are bundled; their OFL licenses are in `docs/licenses`.

The app uses Android system bars, the actual keyboard and permission controls. Long real route names wrap instead of using the prototype's Route A and sample stops. Scanner fixture screenshots labelled `fixture` remain injected instrumentation states and are not live recognition results.

The home screen puts trip planning first. Results show eligible directional paths on an interactive MapLibre street map, with route colors, route-card selection highlighting, and boarding/alighting markers. Details narrows geometry to the selected stop occurrences and keeps an ordered stop list plus a local schematic fallback. The map uses OpenFreeMap Liberty/OSM-derived tiles over the network and has visible attribution. An offline or tile-failure status offers Retry; route cards, stop details, and local geometry remain available. Results and Details retain their own map camera positions across navigation and recreation. Dashed amber connectors mark unresolved map joins; the map is corridor coverage, not turn-by-turn guidance or a fully offline basemap.

The catalog is versioned `iloilo-jaro-wvsu-2026-11-08`. On launch, packaged data is validated against the installed dataset date/version and safely replaces only older data through `RouteBundleStore.replaceBundle`; same-version content must have the same SQLite checksum. Replacement failure keeps the last valid catalog.

Results, Details, Explore, and the central Scan control share the Scan entry. The back-camera preview is opened after a runtime camera grant and stays available independently of detector readiness. Recognition is currently unavailable; manual results/details remain accessible and no compatibility decision is shown. Camera resources are released when leaving Scan or pausing, and permission state is rechecked on return.

Run from the repository root with `ANDROID_HOME` and `ANDROID_SDK_ROOT` set to the Android SDK:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

The instrumentation suite changes route-bundle files. Run it on an emulator or an isolated test installation, not on the normal app installation. Set `ANDROID_SERIAL` to the intended device, and use `-PparapoIsolatedUiTest` with the connected test command to install the separate `com.parapo.prototype.uitest` package. Its screenshot fixtures stay in that temporary package's app-specific storage instead of the phone gallery. Build the normal APK again without the property afterward.

## Checks performed

- See [the 2026-11-08 evidence note](evidence/m2/map-camera-2026-11-08.md) for the current build, emulator and device results. Historical phone checks above are retained in the dated M2/M4 evidence notes.
