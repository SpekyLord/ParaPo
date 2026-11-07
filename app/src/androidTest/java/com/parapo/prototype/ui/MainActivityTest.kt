package com.parapo.prototype.ui

import android.view.View
import android.view.ViewGroup
import android.content.pm.PackageManager
import java.io.File
import android.widget.Button
import android.widget.EditText
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parapo.prototype.ParapoApplication
import com.parapo.prototype.R
import com.parapo.prototype.data.RouteBundleStore
import com.parapo.prototype.data.DatasetInfo
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.data.Location
import com.parapo.prototype.data.Pattern
import com.parapo.prototype.data.Route
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.SourceRecord
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus
import com.parapo.prototype.scanner.DecisionKind
import com.parapo.prototype.scanner.ScannerDecision
import com.parapo.prototype.scanner.DirectionSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val app: ParapoApplication = ApplicationProvider.getApplicationContext()
    private val evidenceRun = System.currentTimeMillis().toString()

    @After fun restoreEmptyCatalog() {
        app.catalogLoader = { RouteCatalog.EMPTY }
    }

    @Test fun manualResultDetailsAndScannerRetainTheChosenJourney() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                assertHasText(activity, "Synthetic Route")
                assertHasText(activity, "Northbound")
                assertHasText(activity, "Board at A")
                assertHasText(activity, "Get off at C")
                click(activity, "Scan sign")
                assertHasText(activity, "Scanner unavailable")
                assertHasText(activity, "Scanner is unavailable right now. You can still see your routes and stops.")
                assertHasText(activity, "Your route: Synthetic Route · Northbound")
                assertHasText(activity, "Board at A; get off at C.")
                click(activity, "See my routes")
                click(activity, "See stops")
                assertHasText(activity, "Synthetic Route")
                assertHasText(activity, "Direction: Northbound. Test headsign")
                assertHasText(activity, "Ordered stops")
                assertHasText(activity, "1. A · Board here")
                assertHasText(activity, "3. C · Get off here")
                assertHasText(activity, "Source: Synthetic test fixture")
                assertHasText(activity, "Verified: 2026-11-06")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteDiagramView })
                val diagram = descendants(activity.findViewById(R.id.app_root)).filterIsInstance<RouteDiagramView>().single()
                assertEquals(1, diagram.boardingSequence)
                assertEquals(3, diagram.alightingSequence)
                click(activity, "See stops without a map")
                assertHasText(activity, "1. A · Board here")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).none { it is RouteDiagramView })
                click(activity, "Show route diagram")
            }
        }
    }

    @Test fun exploreAndAboutWorkWithoutAnActiveJourneyAndEmptyCatalogIsClear() {
        app.catalogLoader = { RouteCatalog.EMPTY }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Verified route coverage is unavailable in this build. No route recommendations can be made.")
                click(activity, "Explore")
                assertHasText(activity, "No verified routes are available to browse.")
                click(activity, "Info")
                assertHasText(activity, "Dataset version: none")
                assertHasText(activity, "Recognition model: YOLOv8n fine-tuned M3 demo-v3; provisional")
            }
        }
    }

    @Test fun bundledRouteMapsPlanJaroPlazaToWvsuInBothDirections() {
        listOf("routes.sqlite", "routes.sqlite.backup", "routes.sqlite.pending").forEach {
            File(app.filesDir, it).delete()
        }
        app.catalogLoader = { context -> RouteBundleStore(context).load() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Verified coverage: Jaro Plaza ↔ WVSU on Routes 3 and 10 only. Other routes and transfers are not covered.")
                activity.findViewById<EditText>(R.id.home_origin).setText("Jaro Cathedral")
                activity.findViewById<EditText>(R.id.home_destination).setText("WVSU")
                click(activity, "Find my route")
                assertHasText(activity, "Route 3")
                assertHasText(activity, "Jaro Plaza to WVSU via E. Lopez Street")
                assertHasText(activity, "Route 10")
                assertHasText(activity, "Jaro Plaza to WVSU via Commission Civil and Del Carmen Streets")
                click(activity, "See stops")
                assertHasText(activity, "Source: Iloilo City Planning route maps for Route 3 Ungka–CPU and Route 10 Buntatala/Tagbak; project lead approved the Jaro Plaza–WVSU corridor and confirmed the Jaro Plaza direction")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteDiagramView })
                click(activity, "Back")
                click(activity, "Back to plan")
                activity.findViewById<EditText>(R.id.home_origin).setText("WVSU")
                activity.findViewById<EditText>(R.id.home_destination).setText("Jaro Plaza")
                click(activity, "Find my route")
                assertHasText(activity, "WVSU to Jaro Plaza via La Paz Market and E. Lopez Street")
                assertHasText(activity, "WVSU to Jaro Plaza via La Paz Market and Commission Civil")
                click(activity, "Explore")
                assertHasText(activity, "Route 3 — Ungka to City Proper via CPU · Jaro Plaza to WVSU via E. Lopez Street")
                assertHasText(activity, "Route 10 — Buntatala/Tagbak to City Proper · WVSU to Jaro Plaza via La Paz Market and Commission Civil")
                click(activity, "Info")
                assertHasText(activity, "Dataset version: iloilo-jaro-wvsu-2026-11-06")
                assertHasText(activity, "Verified route patterns: 4")
                assertHasText(activity, "Recognition model: YOLOv8n fine-tuned M3 demo-v3; provisional")
                assertHasText(activity, "Model check: 0/3 M3 test signs detected; ONNX export parity failed. Phone smoke inference ran on a synthetic image; sign accuracy is unverified.")
                assertHasText(activity, "Model SHA-256: 183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8")
                assertHasText(activity, "Inference runtime: ONNX Runtime Android 1.31.1")
                assertHasText(activity, "Offline OCR: ML Kit bundled Latin 16.0.1")
                assertHasText(activity, "Scanner settings: scanner-v1; 2 observations / 1 s window; 1 s result freshness (provisional).")
                assertHasText(activity, "Live scanning is unavailable until export parity is resolved. Manual planning remains available.")
            }
        }
    }

    @Test fun unknownEndpointIsNotPresentedAsNoService() {
        app.catalogLoader = { RouteCatalog.EMPTY }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("B")
                click(activity, "Find my route")
                assertHasText(activity, "We can't confirm coverage for 'A'. Try a supported location or browse Explore.")
                assertTrue(app.catalogLoader(ApplicationProvider.getApplicationContext()) == RouteCatalog.EMPTY)
            }
        }
    }

    @Test fun noDirectRouteCanReturnToPlanning() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("C")
                activity.findViewById<EditText>(R.id.home_destination).setText("A")
                click(activity, "Find my route")
                assertHasText(activity, "No direct route found")
                click(activity, "Change a place")
                assertEquals("C", activity.findViewById<EditText>(R.id.home_origin).text.toString())
                assertEquals("A", activity.findViewById<EditText>(R.id.home_destination).text.toString())
            }
        }
    }

    @Test fun changePlaceSearchSelectsARealCatalogLocation() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val change = descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "Change Starting point" }
                change.performClick()
                activity.findViewById<EditText>(R.id.location_search).setText("B")
                click(activity, "B")
                assertEquals("B", activity.findViewById<EditText>(R.id.home_origin).text.toString())
            }
        }
    }

    @Test fun locationSuggestionsUseTheCatalogAndTextOnlyDetailsSurviveRecreation() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val origin = activity.findViewById<AutoCompleteTextView>(R.id.home_origin)
                assertEquals(3, origin.adapter.count)
                origin.setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "See stops")
                click(activity, "See stops without a map")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertHasText(activity, "1. A · Board here")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).none { it is RouteDiagramView })
                click(activity, "Back")
                assertHasText(activity, "Board at A")
            }
        }
    }

    @Test fun appRequestsCameraButNotNetworkPermission() {
        val packageInfo = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(android.Manifest.permission.CAMERA in permissions)
        assertTrue(android.Manifest.permission.INTERNET !in permissions)
        assertTrue("android.permission.ACCESS_NETWORK_STATE" !in permissions)
    }

    @Test fun exploreScannerStartsWithNeutralContextEvenAfterPlanning() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "Explore")
                click(activity, "Scan a sign")
                assertHasText(activity, "No trip is selected. Browse routes to see their stops.")
                assertHasText(activity, "Scanner is unavailable right now. You can still see your routes and stops.")
                assertHasText(activity, "Back to Explore")
            }
        }
    }

    @Test fun exploreDetailsCanOpenNeutralScanner() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                click(activity, "Explore")
                click(activity, "See route stops")
                assertHasText(activity, "Ordered stops")
                click(activity, "Scan a sign")
                assertHasText(activity, "No trip is selected. Browse routes to see their stops.")
                click(activity, "Back to Explore")
                assertHasText(activity, "Explore routes")
            }
        }
    }

    @Test fun unavailableScannerKeepsManualFallbackWithoutRequestingCamera() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cameraPermissionBefore = activity.checkSelfPermission(android.Manifest.permission.CAMERA)
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "Scan sign")
                assertHasText(activity, "Scanner is unavailable right now. You can still see your routes and stops.")
                assertHasText(activity, "Your route: Synthetic Route · Northbound")
                assertHasText(activity, "See my routes")
                assertEquals(cameraPermissionBefore, activity.checkSelfPermission(android.Manifest.permission.CAMERA))
            }
        }
    }

    @Test fun scannerOpenedFromStopsReturnsToTheSameStops() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "See stops")
                click(activity, "Scan a sign")
                assertHasText(activity, "Scanner unavailable")
                click(activity, "Back to stops")
                assertHasText(activity, "1. A · Board here")
                assertHasText(activity, "3. C · Get off here")
            }
        }
    }

    @Test fun scannerOutcomePresentationCanBeInspectedWithFixtures() {
        app.catalogLoader = { uiFixture() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "Scan sign")
                val content = activity.findViewById<LinearLayout>(R.id.screen_content)
                descendants(content).filterIsInstance<Button>().firstOrNull { it.text.toString() == "See my routes" }?.let { content.removeView(it) }
                val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                content.addView(actions)
                MainActivity::class.java.getDeclaredField("scannerActions").apply { isAccessible = true }.set(activity, actions)
            }
            for (kind in DecisionKind.entries) {
                scenario.onActivity { activity ->
                    val decision = ScannerDecision(kind, "SYN-ROUTE", "Synthetic Route", "SYN-UI", "Northbound", DirectionSource.SIGN)
                    val description = MainActivity::class.java.getDeclaredMethod("decisionDescription", ScannerDecision::class.java).apply { isAccessible = true }.invoke(activity, decision) as String
                    val color = MainActivity::class.java.getDeclaredMethod("decisionColor", DecisionKind::class.java).apply { isAccessible = true }.invoke(activity, kind) as Int
                    MainActivity::class.java.getDeclaredMethod("updateScannerStatus", String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, description, color)
                    MainActivity::class.java.getDeclaredMethod("renderScannerActions", ScannerDecision::class.java).apply { isAccessible = true }.invoke(activity, decision)
                    assertHasText(activity, description)
                    assertHasText(activity, "See my routes")
                }
                instrumentation.waitForIdleSync()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    saveEvidence("scanner-${kind.name.lowercase()}-fixture", screenshot)
                    screenshot.recycle()
                }
            }
        }
    }

    @Test fun captureActualScreensForDesignReview() {
        app.catalogLoader = { context -> RouteBundleStore(context.createDeviceProtectedStorageContext()).load() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun capture(name: String) {
            instrumentation.waitForIdleSync()
            val image = instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("No screenshot for $name")
            saveEvidence(name, image)
            image.recycle()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            capture("home")
            scenario.onActivity { activity ->
                descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "Change Starting point" }.performClick()
                activity.findViewById<EditText>(R.id.location_search).setText("Jaro")
            }
            capture("search-keyboard")
            scenario.onActivity { activity ->
                click(activity, "Jaro Plaza (Cathedral area)")
                activity.findViewById<EditText>(R.id.home_destination).setText("WVSU")
                click(activity, "Find my route")
            }
            capture("results")
            scenario.onActivity { click(it, "See stops") }
            capture("details")
            scenario.onActivity { click(it, "Explore") }
            capture("explore")
            scenario.onActivity { click(it, "Info") }
            capture("about")
            scenario.onActivity { activity -> click(activity, "Explore"); click(activity, "Scan a sign") }
            capture("scanner-unavailable")
        }
    }

    private fun saveEvidence(name: String, image: Bitmap) {
        // Keep the physical test phone's shared gallery unchanged.
        if (app.packageName.endsWith(".uitest")) {
            val folder = File(app.getExternalFilesDir(null), "design-review").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ParaPo-design-review/$evidenceRun")
        }
        val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Could not save screenshot $name")
        app.contentResolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: throw AssertionError("Could not open screenshot $name")
    }

    private fun uiFixture(): RouteCatalog {
        val locations = listOf(
            Location("A", "A", emptyList(), 14.0, 122.0, "landmark"),
            Location("B", "B", emptyList(), 14.1, 122.1, "landmark"),
            Location("C", "C", emptyList(), 14.2, 122.2, "landmark"),
        )
        val stops = listOf(
            StopOccurrence("SYN-UI", 1, "A", true, false),
            StopOccurrence("SYN-UI", 2, "B", false, false),
            StopOccurrence("SYN-UI", 3, "C", false, true),
        )
        return RouteCatalog(
            DatasetInfo(1, "synthetic-test-only", "2026-11-06", listOf("SYN-SOURCE")),
            listOf(Route("SYN-ROUTE", "Synthetic Route", emptyList())),
            listOf(Pattern("SYN-UI", "SYN-ROUTE", "Northbound", "Test headsign", true, VerificationStatus.VERIFIED, "2026-11-06", "SYN-SOURCE")),
            locations,
            stops,
            locations.mapIndexed { index, location -> GeometryPoint("SYN-UI", index + 1, location.latitude, location.longitude, index + 1) },
            listOf(SourceRecord("SYN-SOURCE", "Synthetic test fixture", "test-only", "test fixture")),
        )
    }

    private fun click(activity: MainActivity, label: String) {
        val target = descendants(activity.findViewById(R.id.app_root)).filterIsInstance<Button>()
            .first { it.text.toString() == label }
        target.performClick()
    }

    private fun assertHasText(activity: MainActivity, expected: String) {
        assertTrue("Missing text: $expected", descendants(activity.findViewById(R.id.app_root))
            .filterIsInstance<TextView>().any { it.text.toString() == expected })
    }

    private fun descendants(view: View): List<View> = if (view is ViewGroup) {
        listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else listOf(view)
}
