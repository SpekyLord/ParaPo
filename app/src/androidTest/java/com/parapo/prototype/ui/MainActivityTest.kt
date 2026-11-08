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
import androidx.test.rule.GrantPermissionRule
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
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)
    private val app: ParapoApplication = ApplicationProvider.getApplicationContext()
    private val evidenceRun = System.currentTimeMillis().toString()

    @After fun restoreEmptyCatalog() {
        app.catalogLoader = { RouteCatalog.EMPTY }
    }

    @Test fun bottomNavigationButtonsFitTheirContainer() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<LinearLayout>(R.id.navigation)
                assertEquals(3, nav.childCount)
                for (i in 0 until nav.childCount) {
                    val button = nav.getChildAt(i)
                    assertTrue(button.top >= 0 && button.bottom <= nav.height)
                    assertTrue(button.height >= (48 * activity.resources.displayMetrics.density).toInt())
                }
            }
        }
    }

    @Test fun manualResultAndDetailsRetainTheChosenJourney() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                assertHasText(activity, "Synthetic Route")
                assertHasText(activity, "Northbound")
                assertHasText(activity, "A → C")
                click(activity, "More details")
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

    @Test fun bundledRouteMapsShowSelectablePathsAndSpecificDetails() {
        listOf("routes.sqlite", "routes.sqlite.backup", "routes.sqlite.pending").forEach {
            File(app.filesDir, it).delete()
        }
        app.catalogLoader = { context -> RouteBundleStore(context).load() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Jaro Plaza ↔ WVSU · Routes 3 and 10 only")
                activity.findViewById<EditText>(R.id.home_origin).setText("Jaro Cathedral")
                activity.findViewById<EditText>(R.id.home_destination).setText("WVSU")
                click(activity, "Find my route")
                assertHasText(activity, "Route 3")
                assertHasText(activity, "Jaro Plaza to WVSU via E. Lopez Street")
                assertHasText(activity, "Route 10")
                assertHasText(activity, "Jaro Plaza to WVSU via Commission Civil and Del Carmen Streets")
                val overview = descendants(activity.findViewById(R.id.app_root)).filterIsInstance<RouteDiagramView>().single()
                assertEquals(2, overview.routes.size)
                assertTrue(overview.routes.all { it.size >= 2 })
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteStreetMapView })
                descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "Show Route 10 path on map" }.performClick()
                assertHasText(activity, "Selected on map")
                assertHasText(activity, "Route lines follow mapped streets")
                descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "More details for Route 10" }.performClick()
                assertHasText(activity, "Source: Iloilo City Planning route maps for Route 3 Ungka–CPU and Route 10 Buntatala/Tagbak; project lead approved the Jaro Plaza–WVSU corridor and confirmed the Jaro Plaza direction")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteDiagramView })
                assertHasText(activity, "Direction:")
                assertHasText(activity, "Ordered stops")
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
                val change = descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "Choose Starting point" }
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
                click(activity, "More details")
                click(activity, "See stops without a map")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertHasText(activity, "1. A · Board here")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).none { it is RouteDiagramView })
                click(activity, "Back")
                assertHasText(activity, "A → C")
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
        assertTrue(android.Manifest.permission.INTERNET in permissions)
        assertTrue(android.Manifest.permission.ACCESS_NETWORK_STATE in permissions)
    }

    @Test fun exploreScannerStartsWithNeutralContextEvenAfterPlanning() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "Explore")
                click(activity, "Scan")
                assertHasText(activity, "No trip is selected. Browse routes to see their stops.")
                assertHasText(activity, "Route recognition is currently unavailable")
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
                click(activity, "Scan")
                assertHasText(activity, "No trip is selected. Browse routes to see their stops.")
                click(activity, "Back to stops")
                assertHasText(activity, "Ordered stops")
            }
        }
    }

    @Test fun cameraPreviewRemainsUsableWhenRecognitionIsUnavailable() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find my route")
                click(activity, "Scan")
                assertHasText(activity, "Route recognition is currently unavailable")
                assertHasText(activity, "Your trip is saved. Scan results won't assume a route.")
                assertHasText(activity, "See my routes")
                assertEquals(PackageManager.PERMISSION_GRANTED, activity.checkSelfPermission(android.Manifest.permission.CAMERA))
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is androidx.camera.view.PreviewView })
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
                click(activity, "More details")
                click(activity, "Scan")
                assertHasText(activity, "Scan a route sign")
                click(activity, "Back to stops")
                assertHasText(activity, "1. A · Board here")
                assertHasText(activity, "3. C · Get off here")
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
                descendants(activity.findViewById(R.id.app_root)).first { it.contentDescription == "Choose Starting point" }.performClick()
                activity.findViewById<EditText>(R.id.location_search).setText("Jaro")
            }
            capture("search-keyboard")
            scenario.onActivity { activity ->
                click(activity, "Jaro Plaza (Cathedral area)")
                activity.findViewById<EditText>(R.id.home_destination).setText("WVSU")
                click(activity, "Find my route")
            }
            capture("results")
            scenario.onActivity { click(it, "More details") }
            capture("details")
            scenario.onActivity { click(it, "Explore") }
            capture("explore")
            scenario.onActivity { click(it, "Info") }
            capture("about")
            scenario.onActivity { activity -> click(activity, "Explore"); click(activity, "Scan") }
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
            .filterIsInstance<TextView>().any { it.text.toString().contains(expected) })
    }

    private fun descendants(view: View): List<View> = if (view is ViewGroup) {
        listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else listOf(view)
}
