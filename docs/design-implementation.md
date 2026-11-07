# ParaPo Android design implementation

Reference: [ParaPo Figma screens](https://www.figma.com/design/eTi2eZQ5W8FU6JJOTPUbjR/ParaPo?node-id=6-3).

The existing Android Views activity renders planning and catalog-backed location search, direct/no-direct results, route details, Explore, About / Data, and scanner availability and decision states. The local route catalog supplies names, directions, stops, geometry, sources and dates. Selected journey occurrences are used for the diagram markers; Explore remains neutral.

The screen fills were used rather than the written Foundations labels: paper `#FAF7ED`, ink `#1F2124`, blue `#1F528C`, marigold `#FAAD21`, and white cards with `#D4D1C4` borders. Match, no-match, and neutral surfaces are `#E8F5EB`, `#FFEDEB`, and `#EBF2FA`. Atkinson Hyperlegible Next and Barlow Condensed are bundled; their OFL licenses are in `docs/licenses`.

The app uses Android system bars, the actual keyboard and permission controls. Long real route names wrap instead of using the prototype's Route A and sample stops. The drawn camera and demo controls are omitted. Live scanning remains unavailable until detector export parity is resolved; scanner result screenshots labelled `fixture` are injected by instrumentation and do not show live recognition.

Run from the repository root with `ANDROID_HOME` and `ANDROID_SDK_ROOT` set to the Android SDK:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

The instrumentation suite changes route-bundle files. Run it on an emulator or an isolated test installation, not on the normal app installation. Set `ANDROID_SERIAL` to the intended device, and use `-PparapoIsolatedUiTest` with the connected test command to install the separate `com.parapo.prototype.uitest` package. Its screenshot fixtures stay in that temporary package's app-specific storage instead of the phone gallery. Build the normal APK again without the property afterward.

## Checks performed

- PASSED: `assembleDebug`, `testDebugUnitTest`, and `assembleRelease`.
- PASSED: `connectedDebugAndroidTest` on the Tecno KJ8 using the isolated package (20 tests).
- PASSED: manual 360 dp phone walkthrough of location search, route results, stops, unavailable scanner recovery, Explore, and About. The selected trip remained available after scanner recovery.
- PASSED: 393 dp emulator screenshots of the normal-size screens; real long route labels wrap. The phone search was also viewed with the keyboard open.
- COULD-NOT-RUN: complete large-text and TalkBack reviews, separate offline walkthrough, and live-camera validation. The scanner fixture screenshot run stalled on the emulator; injected scanner states were exercised in the passing phone test suite.
