package com.parapo.prototype.ui

import android.view.View
import android.view.ViewGroup
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parapo.prototype.ParapoApplication
import com.parapo.prototype.R
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val app: ParapoApplication = ApplicationProvider.getApplicationContext()

    @After fun restoreEmptyCatalog() {
        app.catalogLoader = { RouteCatalog.EMPTY }
    }

    @Test fun manualResultDetailsAndScannerFallbackRetainTheChosenJourney() {
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
                assertHasText(activity, "Camera recognition is not included in this M2 build. Your selected journey is saved.")
                assertHasText(activity, "Board at A; get off at C.")
                click(activity, "View details")
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
                assertHasText(activity, "Recognition model: Not included in this M2 build.")
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

    @Test fun appDoesNotRequestCameraOrNetworkPermission() {
        val packageInfo = app.packageManager.getPackageInfo(
            app.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(android.Manifest.permission.CAMERA !in permissions)
        assertTrue(android.Manifest.permission.INTERNET !in permissions)
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
