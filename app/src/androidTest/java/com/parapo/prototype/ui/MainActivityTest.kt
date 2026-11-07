package com.parapo.prototype.ui

import android.view.View
import android.view.ViewGroup
import android.content.pm.PackageManager
import java.io.File
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val app: ParapoApplication = ApplicationProvider.getApplicationContext()

    @After fun restoreEmptyCatalog() {
        app.catalogLoader = { RouteCatalog.EMPTY }
    }

    @Test fun manualResultDetailsAndScannerRetainTheChosenJourney() {
        app.catalogLoader = { uiFixture() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.home_origin).setText("A")
                activity.findViewById<EditText>(R.id.home_destination).setText("C")
                click(activity, "Find routes")
                assertHasText(activity, "Synthetic Route · Northbound")
                assertHasText(activity, "Board at A")
                assertHasText(activity, "Get off at C")
                click(activity, "Scan")
                assertHasText(activity, "Scan a route sign")
                assertHasText(activity, "Detector unavailable: export parity failed or the model asset is missing/corrupt. Manual planning is still available.")
                assertHasText(activity, "Checking Synthetic Route · Northbound")
                assertHasText(activity, "Board at A; get off at C.")
                click(activity, "Manual planning")
                click(activity, "Details")
                assertHasText(activity, "Synthetic Route")
                assertHasText(activity, "Direction: Northbound. Test headsign")
                assertHasText(activity, "Ordered stops")
                assertHasText(activity, "1. A · Board here")
                assertHasText(activity, "3. C · Get off here")
                assertHasText(activity, "Source: Synthetic test fixture")
                assertHasText(activity, "Verified: 2026-11-06")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteDiagramView })
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
                click(activity, "About / Data")
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
                click(activity, "Find routes")
                assertHasText(activity, "Route 3 — Ungka to City Proper via CPU · Jaro Plaza to WVSU via E. Lopez Street")
                assertHasText(activity, "Route 10 — Buntatala/Tagbak to City Proper · Jaro Plaza to WVSU via Commission Civil and Del Carmen Streets")
                click(activity, "Details")
                assertHasText(activity, "Source: Iloilo City Planning route maps for Route 3 Ungka–CPU and Route 10 Buntatala/Tagbak; project lead approved the Jaro Plaza–WVSU corridor and confirmed the Jaro Plaza direction")
                assertTrue(descendants(activity.findViewById(R.id.app_root)).any { it is RouteDiagramView })
                click(activity, "Back")
                click(activity, "Back to plan")
                activity.findViewById<EditText>(R.id.home_origin).setText("WVSU")
                activity.findViewById<EditText>(R.id.home_destination).setText("Jaro Plaza")
                click(activity, "Find routes")
                assertHasText(activity, "Route 3 — Ungka to City Proper via CPU · WVSU to Jaro Plaza via La Paz Market and E. Lopez Street")
                assertHasText(activity, "Route 10 — Buntatala/Tagbak to City Proper · WVSU to Jaro Plaza via La Paz Market and Commission Civil")
                click(activity, "Explore")
                assertHasText(activity, "Route 3 — Ungka to City Proper via CPU · Jaro Plaza to WVSU via E. Lopez Street")
                assertHasText(activity, "Route 10 — Buntatala/Tagbak to City Proper · WVSU to Jaro Plaza via La Paz Market and Commission Civil")
                click(activity, "About / Data")
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
                click(activity, "Find routes")
                assertHasText(activity, "We can't confirm coverage for 'A'. Try a supported location or browse Explore.")
                assertTrue(app.catalogLoader(ApplicationProvider.getApplicationContext()) == RouteCatalog.EMPTY)
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
                click(activity, "Find routes")
                click(activity, "Explore")
                click(activity, "Scan a sign")
                assertHasText(activity, "Neutral route identification — any earlier journey selection is ignored for matching.")
                assertHasText(activity, "Detector unavailable: export parity failed or the model asset is missing/corrupt. Manual planning is still available.")
                assertHasText(activity, "Back to Explore")
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
                click(activity, "Find routes")
                click(activity, "Scan")
                assertHasText(activity, "Detector unavailable: export parity failed or the model asset is missing/corrupt. Manual planning is still available.")
                assertHasText(activity, "Checking Synthetic Route · Northbound")
                assertHasText(activity, "Manual planning")
                assertEquals(cameraPermissionBefore, activity.checkSelfPermission(android.Manifest.permission.CAMERA))
            }
        }
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
