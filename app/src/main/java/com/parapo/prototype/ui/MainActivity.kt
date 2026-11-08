package com.parapo.prototype.ui

import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import org.maplibre.android.camera.CameraPosition
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.parapo.prototype.BuildConfig
import com.parapo.prototype.ParapoApplication
import com.parapo.prototype.R
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.data.JourneyMatch
import com.parapo.prototype.data.Location
import com.parapo.prototype.data.Pattern
import com.parapo.prototype.data.Route
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus
import com.parapo.prototype.routing.JourneyPlanner
import com.parapo.prototype.routing.LocationResolution
import com.parapo.prototype.routing.LocationResolver
import com.parapo.prototype.scanner.DecisionKind
import com.parapo.prototype.scanner.DirectionSource
import com.parapo.prototype.scanner.RouteTextResolution
import com.parapo.prototype.scanner.ScannerDecision
import com.parapo.prototype.scanner.ScannerDecisions
import com.parapo.prototype.scanner.ScannerPipeline
import com.parapo.prototype.scanner.ScannerSettings
import com.parapo.prototype.scanner.SignBox
import com.parapo.prototype.scanner.TemporalDecisionTracker

class MainActivity : ComponentActivity(), ScannerPipeline.Listener {
    private val backNavigationCallback = OnBackInvokedCallback { handleBackNavigation() }
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scannerPermissionInFlight = false
        if (granted) {
            scannerPermissionDenied = false
            scannerPermissionPermanentlyDenied = false
            if (screen == Screen.SCANNER) { render(); startScannerCamera() }
        } else if (screen == Screen.SCANNER) showCameraPermissionDenied()
    }
    private val scannerHandler = Handler(Looper.getMainLooper())
    private val temporalTracker = TemporalDecisionTracker(ScannerSettings())
    private lateinit var catalog: RouteCatalog
    private lateinit var resolver: LocationResolver
    private lateinit var planner: JourneyPlanner
    private lateinit var content: LinearLayout
    private lateinit var coverage: TextView
    private var screen = Screen.HOME
    private var originText = ""
    private var destinationText = ""
    private var origin: Location? = null
    private var destination: Location? = null
    private var results: List<JourneyMatch> = emptyList()
    private var selected: JourneyMatch? = null
    private var explorePatternId: String? = null
    private var detailsPatternOverride: String? = null
    private var detailsTextOnly = false
    private var aboutReturnScreen = Screen.HOME
    private var searchingOrigin = true
    private var locationQuery = ""
    private var statusMessage: String? = null
    private var scannerNeutral = false
    private var scannerReturnScreen = Screen.RESULTS
    private var scannerContextKey = ""
    private var scannerSessionId = 0L
    private var scannerPermissionDenied = false
    private var scannerPermissionPermanentlyDenied = false
    private var scannerGeneration = 0L
    private var scannerStarting = false
    private var scannerBound = false
    private var scannerModelHash = ""
    private var scannerUnavailableReason: String? = null
    private var scannerPermissionInFlight = false
    private var scannerPipeline: ScannerPipeline? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var routeMap: RouteStreetMapView? = null
    private var selectedPathButton: Button? = null
    private var resultsMapCamera: CameraPosition? = null
    private var detailsMapCamera: CameraPosition? = null
    private var activityStarted = false
    private var activityResumed = false
    private var scannerPreview: PreviewView? = null
    private var scannerOverlay: ScannerOverlayView? = null
    private var scannerStatus: TextView? = null
    private var scannerActions: LinearLayout? = null
    private var scannerBoxes: List<SignBox> = emptyList()
    private var scannerSelectedBox: SignBox? = null
    private var scannerFrameWidth = 0
    private var scannerFrameHeight = 0
    private var scannerTargetId: String? = null
    private var lastScannerDecision: ScannerDecision? = null
    private var userConfirmedRouteId: String? = null
    private var userConfirmedPatternId: String? = null
    private var scanReturnLabel = "See my routes"
    private var analyzedFrameCount = 0
    private var recognizedCropCount = 0
    private val decisionLatenciesMs = mutableListOf<Long>()
    private val scannerExpiry = object : Runnable {
        override fun run() {
            val active = lastScannerDecision
            if (screen != Screen.SCANNER || active == null) return
            if (temporalTracker.current(android.os.SystemClock.elapsedRealtime()) == null) {
                lastScannerDecision = null
                updateScannerStatus("? Result expired — select one sign again", Color.GRAY)
                renderScannerActions(null)
            } else scannerHandler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backNavigationCallback)
        catalog = (application as ParapoApplication).catalogLoader(this) ?: RouteCatalog.EMPTY
        resolver = LocationResolver(catalog)
        planner = JourneyPlanner(catalog)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_root)) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
            view.setPadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom)
            insets
        }
        content = findViewById(R.id.screen_content)
        coverage = findViewById(R.id.coverage_notice)
        (coverage.parent as ViewGroup).removeView(coverage)
        findViewById<Button>(R.id.app_info).apply {
            text = getString(R.string.info)
            isAllCaps = false
            typeface = Typeface.create(resources.getFont(R.font.atkinson_hyperlegible_next), Typeface.BOLD)
            setTextColor(getColor(R.color.blue))
            background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), rounded(R.color.card_white, R.color.blue, 14), null)
            contentDescription = "About ParaPo and route data"
            setOnClickListener { aboutReturnScreen = if (screen == Screen.EXPLORE) Screen.EXPLORE else Screen.HOME; go(Screen.ABOUT) }
        }
        findViewById<Button>(R.id.app_back).apply {
            typeface = Typeface.create(resources.getFont(R.font.atkinson_hyperlegible_next), Typeface.BOLD)
            setTextColor(getColor(R.color.blue))
            background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), rounded(R.color.card_white, R.color.blue, 14), null)
            setOnClickListener { handleBackNavigation() }
        }
        originText = savedInstanceState?.getString(KEY_ORIGIN_TEXT).orEmpty()
        destinationText = savedInstanceState?.getString(KEY_DESTINATION_TEXT).orEmpty()
        val originId = savedInstanceState?.getString(KEY_ORIGIN_ID)
        val destinationId = savedInstanceState?.getString(KEY_DESTINATION_ID)
        origin = catalog.locations.firstOrNull { it.id == originId }
        destination = catalog.locations.firstOrNull { it.id == destinationId }
        if (origin != null && destination != null && origin?.id != destination?.id) {
            results = planner.findDirectJourneys(origin!!.id, destination!!.id)
        }
        val patternId = savedInstanceState?.getString(KEY_PATTERN)
        val boardSequence = savedInstanceState?.getInt(KEY_BOARD, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val alightSequence = savedInstanceState?.getInt(KEY_ALIGHT, Int.MIN_VALUE) ?: Int.MIN_VALUE
        selected = if (patternId == null || boardSequence == Int.MIN_VALUE || alightSequence == Int.MIN_VALUE) null else {
            val board = catalog.occurrences.firstOrNull { it.patternId == patternId && it.sequence == boardSequence }
            val alight = catalog.occurrences.firstOrNull { it.patternId == patternId && it.sequence == alightSequence }
            val pattern = catalog.patterns.firstOrNull { it.id == patternId }
            if (board != null && alight != null && pattern != null) JourneyMatch(pattern.routeId, pattern.id, board, alight) else null
        }
        screen = savedInstanceState?.getString(KEY_SCREEN)?.let { runCatching { Screen.valueOf(it) }.getOrNull() } ?: Screen.HOME
        explorePatternId = savedInstanceState?.getString(KEY_EXPLORE_PATTERN)
        detailsPatternOverride = savedInstanceState?.getString(KEY_DETAILS_PATTERN)
        detailsTextOnly = savedInstanceState?.getBoolean(KEY_DETAILS_TEXT_ONLY) ?: false
        searchingOrigin = savedInstanceState?.getBoolean(KEY_SEARCH_ORIGIN) ?: true
        locationQuery = savedInstanceState?.getString(KEY_LOCATION_QUERY).orEmpty()
        aboutReturnScreen = savedInstanceState?.getString(KEY_ABOUT_RETURN)?.let {
            runCatching { Screen.valueOf(it) }.getOrNull()
        } ?: Screen.HOME
        scannerNeutral = savedInstanceState?.getBoolean(KEY_SCANNER_NEUTRAL) ?: false
        scannerReturnScreen = savedInstanceState?.getString(KEY_SCANNER_RETURN)?.let {
            runCatching { Screen.valueOf(it) }.getOrNull()
        } ?: Screen.RESULTS
        resultsMapCamera = savedInstanceState?.getParcelable(KEY_RESULTS_MAP_CAMERA, CameraPosition::class.java)
        detailsMapCamera = savedInstanceState?.getParcelable(KEY_DETAILS_MAP_CAMERA, CameraPosition::class.java)
        scanReturnLabel = scannerReturnLabel(scannerReturnScreen)
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.beginSession(scannerContextKey)
        updateCoverage()
        render()
        findViewById<View>(R.id.navigation).also { nav ->
            addNavButton(nav as LinearLayout, getString(R.string.nav_home)) { go(Screen.HOME) }
            addNavButton(nav, "Scan") {
                val returnTo = when (screen) {
                    Screen.RESULTS -> Screen.RESULTS
                    Screen.DETAILS -> Screen.DETAILS
                    Screen.EXPLORE -> Screen.EXPLORE
                    else -> Screen.HOME
                }
                openScanner(neutral = returnTo != Screen.DETAILS || selected == null, returnTo = returnTo)
            }
            addNavButton(nav, getString(R.string.nav_explore)) { go(Screen.EXPLORE) }
        }
        updateNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ORIGIN_TEXT, originText)
        outState.putString(KEY_DESTINATION_TEXT, destinationText)
        outState.putString(KEY_ORIGIN_ID, origin?.id)
        outState.putString(KEY_DESTINATION_ID, destination?.id)
        outState.putString(KEY_SCREEN, screen.name)
        outState.putString(KEY_EXPLORE_PATTERN, explorePatternId)
        outState.putString(KEY_DETAILS_PATTERN, detailsPatternOverride)
        outState.putBoolean(KEY_DETAILS_TEXT_ONLY, detailsTextOnly)
        outState.putBoolean(KEY_SEARCH_ORIGIN, searchingOrigin)
        outState.putString(KEY_LOCATION_QUERY, locationQuery)
        outState.putString(KEY_ABOUT_RETURN, aboutReturnScreen.name)
        outState.putBoolean(KEY_SCANNER_NEUTRAL, scannerNeutral)
        outState.putString(KEY_SCANNER_RETURN, scannerReturnScreen.name)
        selected?.let {
            outState.putString(KEY_PATTERN, it.patternId)
            outState.putInt(KEY_BOARD, it.boarding.sequence)
            outState.putInt(KEY_ALIGHT, it.alighting.sequence)
        }
        resultsMapCamera?.let { outState.putParcelable(KEY_RESULTS_MAP_CAMERA, it) }
        detailsMapCamera?.let { outState.putParcelable(KEY_DETAILS_MAP_CAMERA, it) }
        routeMap?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        routeMap?.onStart()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        routeMap?.onResume()
        if (screen == Screen.SCANNER) {
            val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                scannerPermissionDenied = false
                scannerPermissionPermanentlyDenied = false
                if (scannerPreview == null) render()
                startScannerCamera()
            } else if (!scannerPermissionInFlight) {
                val asked = getSharedPreferences(PREFS_SCANNER, MODE_PRIVATE).getBoolean(KEY_CAMERA_ASKED, false)
                if (!asked) requestScannerPermission()
                else if (scannerPreview == null) {
                    scannerPermissionPermanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    scannerPermissionDenied = true
                    render()
                }
            }
        }
    }

    override fun onPause() {
        activityResumed = false
        routeMap?.onPause()
        if (screen == Screen.SCANNER) stopScannerResources()
        super.onPause()
    }

    override fun onStop() {
        activityStarted = false
        routeMap?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        releaseRouteMap()
        stopScannerResources()
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backNavigationCallback)
        super.onDestroy()
    }

    private fun handleBackNavigation() {
        when (screen) {
            Screen.DETAILS -> go(detailsBackScreen())
            Screen.SCANNER -> go(scannerReturnScreen)
            Screen.ABOUT -> go(aboutReturnScreen)
            Screen.RESULTS, Screen.NO_ROUTE, Screen.SEARCH, Screen.EXPLORE -> go(Screen.HOME)
            Screen.HOME -> finish()
        }
    }

    private fun updateCoverage() {
        val count = catalog.patterns.count { it.active && it.verificationStatus == VerificationStatus.VERIFIED }
        coverage.text = if (count > 0 && catalog.info.datasetVersion.startsWith("iloilo-jaro-wvsu-")) {
            "Jaro Plaza ↔ WVSU · Routes 3 and 10 only"
        } else if (count > 0) {
            "Verified coverage: $count supported route patterns. Other routes and transfers are not covered."
        } else {
            "Verified route coverage is unavailable in this build. No route recommendations can be made."
        }
    }

    private fun render() {
        releaseRouteMap()
        selectedPathButton = null
        content.removeAllViews()
        coverage.visibility = if (screen == Screen.HOME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.app_back).visibility = if (screen == Screen.HOME || screen == Screen.EXPLORE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.app_info).visibility = if (screen == Screen.HOME || screen == Screen.EXPLORE) View.VISIBLE else View.GONE
        updateNavigation()
        when (screen) {
            Screen.HOME -> renderHome()
            Screen.RESULTS -> renderResults()
            Screen.SEARCH -> renderLocationSearch()
            Screen.NO_ROUTE -> renderNoRoute()
            Screen.DETAILS -> renderDetails()
            Screen.EXPLORE -> renderExplore()
            Screen.ABOUT -> renderAbout()
            Screen.SCANNER -> renderScanner()
        }
    }

    private fun openLocationSearch(originField: Boolean) {
        searchingOrigin = originField
        locationQuery = if (originField) originText else destinationText
        go(Screen.SEARCH)
    }

    private fun renderLocationSearch() {
        addHeading("Choose a place")
        addBody(if (searchingOrigin) "Search for your starting point." else "Search for your destination.")
        val search = editField("Search places", locationQuery, R.id.location_search) { query ->
            locationQuery = query
            showLocationMatches(query)
        }
        add(search)
        showLocationMatches(locationQuery)
        search.requestFocus()
        search.post { if (screen == Screen.SEARCH) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showLocationMatches(query: String) {
        content.findViewWithTag<View>(LOCATION_LIST_TAG)?.let(content::removeView)
        val list = verticalContainer().apply { tag = LOCATION_LIST_TAG }
        val needle = query.trim().lowercase(Locale.ROOT)
        val matches = catalog.locations.filter { location ->
            needle.isEmpty() || (listOf(location.name) + location.aliases).any { it.lowercase(Locale.ROOT).contains(needle) }
        }.sortedBy { it.name }
        if (matches.isEmpty()) list.addView(label("No matching place. Try another name."))
        matches.forEach { location ->
            list.addView(secondaryButton(location.name) {
                if (searchingOrigin) { originText = location.name; origin = location }
                else { destinationText = location.name; destination = location }
                selected = null
                temporalTracker.invalidate()
                go(Screen.HOME)
            })
        }
        content.addView(list)
    }

    private fun renderHome() {
        addHeading("Where are you going?")
        addBody("Choose two places. We’ll show your routes.")
        addLocationField(getString(R.string.starting_point), "Choose a place", originText, R.id.home_origin) {
            if (originText != it) { originText = it; selected = null; temporalTracker.invalidate() }
        }
        addLocationField(getString(R.string.destination), "Choose a place", destinationText, R.id.home_destination) {
            if (destinationText != it) { destinationText = it; selected = null; temporalTracker.invalidate() }
        }
        addButton(getString(R.string.find_route)) { findRoutes() }
        statusMessage?.let { addBody(it) }
        coverage.background = rounded(R.color.coverage_surface, R.color.coverage_surface, 12)
        coverage.setPadding(dp(12), dp(12), dp(12), dp(12))
        add(coverage)
    }

    private fun findRoutes() {
        when (val from = resolver.resolve(originText)) {
            LocationResolution.Missing -> showStatus("Enter an origin location.")
            is LocationResolution.Unsupported -> showStatus("We can't confirm coverage for '${from.query}'. Try a supported location or browse Explore.")
            is LocationResolution.Ambiguous -> chooseLocation("Choose origin", from.candidates) { origin = it; resolveDestination() }
            is LocationResolution.Unique -> { origin = from.location; resolveDestination() }
        }
    }

    private fun resolveDestination() {
        when (val to = resolver.resolve(destinationText)) {
            LocationResolution.Missing -> showStatus("Enter a destination location.")
            is LocationResolution.Unsupported -> showStatus("We can't confirm coverage for '${to.query}'. Try a supported location or browse Explore.")
            is LocationResolution.Ambiguous -> chooseLocation("Choose destination", to.candidates) { destination = it; planJourney() }
            is LocationResolution.Unique -> { destination = to.location; planJourney() }
        }
    }

    private fun planJourney() {
        val from = origin ?: return showStatus("Choose a supported origin.")
        val to = destination ?: return showStatus("Choose a supported destination.")
        if (from.id == to.id) return showStatus("Your origin and destination are the same. No ride is needed.")
        resetMapCamera()
        results = planner.findDirectJourneys(from.id, to.id)
        selected = null
        if (results.isEmpty()) { statusMessage = null; go(Screen.NO_ROUTE); return }
        statusMessage = null
        go(Screen.RESULTS)
    }

    private fun renderResults() {
        addHeading("Your routes")
        addBody("${origin?.name.orEmpty()} → ${destination?.name.orEmpty()}")
        val paths = results.map { match -> catalog.geometry.filter { it.patternId == match.patternId }.sortedBy(GeometryPoint::sequence) }
        if (paths.any { it.size >= 2 }) {
            val mapPaths = results.mapIndexed { index, match ->
                val route = catalog.routes.first { it.id == match.routeId }
                StreetMapPath(match.patternId, route.name.substringBefore(" —"), getColor(if (index % 2 == 0) R.color.blue else R.color.outcome_red), paths[index], incompleteMapEdges(match.patternId, paths[index]))
            }
            val match = selected ?: results.first()
            addRouteMap(mapPaths, journeyMarkers(match), selected?.patternId)
            addBody("Route lines follow mapped streets. The connector at Jaro Plaza is approximate; this is not turn-by-turn guidance.")
            add(RouteDiagramView(this).apply {
                routes = paths
                background = rounded(R.color.neutral_surface, R.color.card_border, 16)
                minimumHeight = dp(130)
                contentDescription = "Offline schematic fallback of available route paths."
            })
        } else addBody("Route map unavailable. See the route descriptions below.")
        results.forEachIndexed { index, match ->
            val pattern = catalog.patterns.first { it.id == match.patternId }
            val route = catalog.routes.first { it.id == match.routeId }
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = rounded(R.color.card_white, R.color.card_border, 16)
                clipToOutline = true
            }
            wrapper.addView(View(this).apply { setBackgroundColor(getColor(if (index % 2 == 0) R.color.blue else R.color.outcome_red)) }, LinearLayout.LayoutParams(dp(7), ViewGroup.LayoutParams.MATCH_PARENT))
            val card = verticalContainer()
            card.setPadding(dp(14), dp(12), dp(14), dp(12))
            val routeHeading = Regex("^Route\\s+\\d+", RegexOption.IGNORE_CASE).find(route.name)?.value ?: route.name
            card.addView(label(routeHeading).apply {
                typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 21f
                setPadding(0, 0, 0, dp(2))
            })
            card.addView(label(pattern.direction).apply {
                typeface = Typeface.create(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.blue))
                setPadding(0, dp(2), 0, dp(4))
            })
            var selectionButton: Button? = null
            selectionButton = secondaryButton(if (selected?.patternId == match.patternId) "Selected on map" else "Show path on map") {
                selected = match
                routeMap?.selectPath(match.patternId, journeyMarkers(match))
                selectedPathButton?.text = "Show path on map"
                selectionButton?.text = "Selected on map"
                selectedPathButton = selectionButton
            }.apply { contentDescription = "Show $routeHeading path on map" }
            if (selected?.patternId == match.patternId) selectedPathButton = selectionButton
            card.addView(selectionButton)
            card.addView(secondaryButton("More details") { selected = match; detailsPatternOverride = null; detailsTextOnly = false; go(Screen.DETAILS) }.apply {
                contentDescription = "More details for $routeHeading"
            })
            wrapper.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            add(wrapper)
        }
        statusMessage?.let { addBody(it) }
    }

    private fun renderNoRoute() {
        addHeading("No direct route found")
        addBody("No direct route found in supported coverage. Transfers and routes outside coverage are not evaluated.")
        addButton("Change a place") { go(Screen.HOME) }
        addButton("Explore routes") { go(Screen.EXPLORE) }
    }

    private fun renderDetails() {
        val match = if (detailsPatternOverride != null && scannerNeutral) null else selected
        val patternId = detailsPatternOverride ?: match?.patternId ?: explorePatternId
        val pattern = catalog.patterns.firstOrNull { it.id == patternId }
        if (pattern == null) {
            addHeading("Route details")
            addBody("No verified directional route details are available in this build.")
            addButton("Back") { go(detailsBackScreen()) }
            return
        }
        val route = catalog.routes.firstOrNull { it.id == pattern.routeId }
        addHeading("Stops and direction")
        route?.let { addBody(it.name) }
        addBody("Direction: ${pattern.direction}. ${pattern.branchHeadsign}")
        val patternPoints = catalog.geometry.filter { it.patternId == pattern.id }.sortedBy(GeometryPoint::sequence)
        val pathPoints = match?.let { routeSegment(patternPoints, it.boarding.sequence, it.alighting.sequence) } ?: patternPoints
        if (pathPoints.size >= 2 && !detailsTextOnly) {
            val routeName = route?.name?.substringBefore(" —") ?: "Route"
            val markers = if (match != null) listOf(
                routeMarker("Board · ${locationName(match.boarding.locationId)}", pattern.id, match.boarding.sequence, R.color.marigold),
                routeMarker("Get off · ${locationName(match.alighting.locationId)}", pattern.id, match.alighting.sequence, R.color.outcome_green),
            ) else catalog.occurrences.filter { it.patternId == pattern.id }.sortedBy(StopOccurrence::sequence).mapNotNull { stop ->
                when {
                    stop.boardingAllowed -> routeMarker("Board · ${locationName(stop.locationId)}", pattern.id, stop.sequence, R.color.marigold)
                    stop.alightingAllowed -> routeMarker("Get off · ${locationName(stop.locationId)}", pattern.id, stop.sequence, R.color.outcome_green)
                    else -> null
                }
            }
            addRouteMap(listOf(StreetMapPath(pattern.id, routeName, getColor(R.color.blue), pathPoints, incompleteMapEdges(pattern.id, pathPoints))), markers, pattern.id)
            addBody("Street map · pan and pinch to zoom. Dashed amber sections are approximate connectors; do not use this map as turn-by-turn guidance.")
            val diagram = RouteDiagramView(this).apply {
                points = pathPoints
                boardingSequence = match?.boarding?.sequence
                alightingSequence = match?.alighting?.sequence
                background = rounded(R.color.card_white, R.color.card_border, 16)
                contentDescription = "Offline schematic fallback. The ordered stop list below gives direction and selected stops."
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                minimumHeight = dp(160)
            }
            add(diagram)
            addButton("See stops without a map") { detailsTextOnly = true; render() }
        } else if (pathPoints.size < 2) {
            addBody("Local route geometry is unavailable; use the ordered stop list below.")
        } else {
            addButton("Show route diagram") { detailsTextOnly = false; render() }
        }
        addHeading("Ordered stops")
        catalog.occurrences.filter { it.patternId == pattern.id }.sortedBy(StopOccurrence::sequence).forEachIndexed { index, stop ->
            val markers = when (stop.sequence) {
                match?.boarding?.sequence -> " · Board here"
                match?.alighting?.sequence -> " · Get off here"
                else -> ""
            }
            add(label("${index + 1}. ${locationName(stop.locationId)}$markers").apply {
                background = rounded(R.color.card_white, R.color.card_border, 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            })
        }
        addBody("Source: ${catalog.sources.firstOrNull { it.id == pattern.sourceRef }?.description ?: "Unavailable"}")
        addBody("Verified: ${pattern.verificationDate}")
    }

    private fun detailsBackScreen(): Screen =
        if (selected == null || (detailsPatternOverride != null && scannerNeutral)) Screen.EXPLORE else Screen.RESULTS

    private fun renderExplore() {
        addHeading("Explore routes")
        addBody("Find a route or scan a sign without planning a trip.")
        val search = editField("Search routes", "", R.id.explore_search) { query ->
            showExploreMatches(query)
        }
        add(search)
        showExploreMatches(search.text.toString())
    }

    private fun showExploreMatches(query: String) {
        val old = content.findViewWithTag<View>(EXPLORE_LIST_TAG)
        if (old != null) content.removeView(old)
        val list = verticalContainer().apply { tag = EXPLORE_LIST_TAG }
        val needle = query.trim().lowercase(Locale.ROOT)
        val matches = catalog.patterns.filter { pattern ->
            pattern.active && pattern.verificationStatus == VerificationStatus.VERIFIED &&
                (needle.isEmpty() || catalog.routes.firstOrNull { it.id == pattern.routeId }?.let { route ->
                    (route.name + " " + route.aliases.joinToString(" ") + " " + pattern.direction + " " + pattern.branchHeadsign)
                        .lowercase(Locale.ROOT).contains(needle)
                } == true)
        }
        if (matches.isEmpty()) list.addView(label("No verified routes are available to browse."))
        matches.forEach { pattern ->
            val route = catalog.routes.first { it.id == pattern.routeId }
            val card = verticalContainer().apply { background = rounded(R.color.card_white, R.color.card_border, 16) }
            card.addView(label("${route.name} · ${pattern.direction}"))
            card.addView(secondaryButton("See route stops") { selected = null; detailsPatternOverride = null; detailsTextOnly = false; explorePatternId = pattern.id; go(Screen.DETAILS) }.apply {
                contentDescription = "See stops for ${route.name}, ${pattern.direction}"
            })
            list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        content.addView(list)
    }

    private fun renderAbout() {
        addHeading("About ParaPo")
        val modelAvailable = modelAssetAvailable()
        addAboutSection("Coverage", listOf(
            "Coverage is limited to the Jaro Plaza–WVSU corridor on Routes 3 and 10. The route maps show streets, not schedules or service frequency.",
            "Verified route patterns: ${catalog.patterns.count { it.active && it.verificationStatus == VerificationStatus.VERIFIED }}",
            "The interactive street map uses MapLibre and OpenFreeMap Liberty tiles over the internet. It uses network-state access to handle tile availability. Manual routes and stops stay local; ParaPo does not upload camera frames.",
            "Camera preview works independently of route recognition. Recognition remains disabled after the latest ONNX parity check failed. Camera access is requested only from Scan.",
        ))
        addAboutSection("Route data", listOf(
            "Dataset version: ${catalog.info.datasetVersion}",
            "Sources: Iloilo City Planning route maps and LTFRB MC 2023-036; stop coordinates © OpenStreetMap contributors. See the route data for direction provenance.",
            "Route data should be rechecked every 90 days, before a data release, and when a route change is reported. Mark a conflicting or unconfirmed pattern unavailable until checked again.",
        ))
        addAboutSection("App and scanner", listOf(
            "App version: ${BuildConfig.VERSION_NAME}",
            "Camera preview is available independently. Recognition remains unavailable because ONNX export parity failed. Manual planning remains available.",
            "Recognition model: ${if (modelAvailable) ScannerPipeline.MODEL_VERSION else "Unavailable — local model asset missing"}",
            "Model check: latest v10 validation used three photos; export parity failed and no v10 test-set inference ran. Recognition is disabled.",
            "Model SHA-256: ${if (modelAvailable) EXPECTED_MODEL_HASH else "not available"}",
            "Inference runtime: ${ScannerPipeline.RUNTIME_VERSION}",
            "Offline OCR: ${ScannerPipeline.OCR_VERSION}",
            "Scanner settings: ${ScannerSettings().version}; 2 observations / 1 s window; 1 s result freshness (provisional).",
        ))
        addButton("Share evaluation summary") { shareEvaluationSummary() }
    }

    private fun addAboutSection(title: String, lines: List<String>) {
        val card = verticalContainer().apply {
            background = rounded(R.color.card_white, R.color.card_border, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(label(title).apply {
            textSize = 14f
            typeface = Typeface.create(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.blue))
        })
        lines.forEach { card.addView(label(it)) }
        add(card)
    }

    private fun renderScanner() {
        addHeading("Scan a route sign")
        addBody(if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            "Camera preview is available. Route recognition is currently unavailable, so use the route cards and stops below."
        } else "Allow camera access to preview a sign. Route recognition is currently unavailable; manual planning works without camera access.")
        if (scannerNeutral) addBody(if (scannerReturnScreen == Screen.RESULTS) "Your trip is saved. Scan results won't assume a route." else "No trip is selected. Browse routes to see their stops.")
        else selected?.let { match ->
            val route = catalog.routes.firstOrNull { it.id == match.routeId }
            val pattern = catalog.patterns.firstOrNull { it.id == match.patternId }
            addBody("Your route: ${route?.name.orEmpty()} · ${pattern?.direction.orEmpty()}")
            addBody("Board at ${locationName(match.boarding.locationId)}; get off at ${locationName(match.alighting.locationId)}.")
        }
        scannerStatus = label("Camera preview is starting…")
        add(scannerStatus!!)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            updateScannerStatus("Camera permission is needed for preview. Manual planning works without it.", Color.GRAY)
            addScannerManualActions()
            if (scannerPermissionPermanentlyDenied) {
                addButton("Open app settings") {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    })
                }
            } else {
                addButton(if (scannerPermissionDenied) "Try camera permission again" else "Allow camera") { requestScannerPermission() }
            }
            return
        }

        val previewBox = FrameLayout(this)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320))
        previewBox.layoutParams = params
        val preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "Live camera preview. Point at one route sign while stationary."
        }
        previewBox.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        add(previewBox)
        scannerPreview = preview
        scannerOverlay = null
        if (scannerUnavailableReason != null) {
            updateScannerStatus(scannerUnavailableReason!!, Color.GRAY)
            addButton("Try camera again") { scannerUnavailableReason = null; scannerBound = false; startScannerCamera() }
        }
        scannerActions = verticalContainer()
        content.addView(scannerActions)
        addScannerManualActions()
        if (!scannerStarting && !scannerBound) startScannerCamera()
    }

    private fun addScannerManualActions() {
        scannerActions?.let { actions ->
            if (actions.parent == null) content.addView(actions)
            actions.removeAllViews()
            actions.addView(button(scanReturnLabel) { go(scannerReturnScreen) })
        } ?: addButton(scanReturnLabel) { go(scannerReturnScreen) }
    }

    private fun addRouteMap(paths: List<StreetMapPath>, markers: List<StreetMapMarker>, selectedPathId: String?) {
        val camera = when (screen) {
            Screen.RESULTS -> resultsMapCamera
            Screen.DETAILS -> detailsMapCamera
            else -> null
        }
        val map = RouteStreetMapView(this, paths, markers, selectedPathId, camera) { position ->
            when (screen) {
                Screen.RESULTS -> resultsMapCamera = position
                Screen.DETAILS -> detailsMapCamera = position
                else -> Unit
            }
        }.apply {
            background = rounded(R.color.neutral_surface, R.color.card_border, 16)
            contentDescription = "Interactive street map with supported jeepney route paths and journey stops"
        }
        routeMap = map
        content.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)).apply { bottomMargin = dp(8) })
        if (activityStarted) map.onStart()
        if (activityResumed) map.onResume()
    }

    private fun routeMarker(label: String, patternId: String, sequence: Int, color: Int): StreetMapMarker {
        val point = catalog.geometry.firstOrNull { it.patternId == patternId && it.stopOccurrenceSequence == sequence }
        val occurrence = catalog.occurrences.firstOrNull { it.patternId == patternId && it.sequence == sequence }
        val location = occurrence?.let { catalog.locations.firstOrNull { loc -> loc.id == it.locationId } }
        return StreetMapMarker(label, point ?: GeometryPoint(patternId, sequence, location?.latitude ?: 10.7237, location?.longitude ?: 122.5565, sequence), getColor(color))
    }

    private fun journeyMarkers(match: JourneyMatch) = listOf(
        routeMarker("Board · ${locationName(match.boarding.locationId)}", match.patternId, match.boarding.sequence, R.color.marigold),
        routeMarker("Get off · ${locationName(match.alighting.locationId)}", match.patternId, match.alighting.sequence, R.color.outcome_green),
    )

    private fun incompleteMapEdges(patternId: String, points: List<GeometryPoint>): Set<Int> = when (patternId) {
        "ILO-03-JARO-TO-WVSU", "ILO-10-JARO-TO-WVSU" -> if (patternId.startsWith("ILO-10")) setOf(0, 9) else setOf(0)
        "ILO-03-WVSU-TO-JARO" -> setOf(points.lastIndex - 1)
        "ILO-10-WVSU-TO-JARO" -> setOf(points.lastIndex - 1, points.lastIndex - 10)
        else -> emptySet()
    }.filter { it in 0 until points.lastIndex }.toSet()

    private fun releaseRouteMap() {
        routeMap?.let { map ->
            if (activityResumed) map.onPause()
            if (activityStarted) map.onStop()
            map.onDestroy()
        }
        routeMap = null
    }

    private fun renderScannerActions(decision: ScannerDecision?) {
        val actions = scannerActions ?: return
        actions.removeAllViews()
        if (decision?.kind == DecisionKind.CONFIRM_DIRECTION && decision.routeId != null && !scannerNeutral) {
            catalog.patterns.filter {
                it.routeId == decision.routeId && it.active && it.verificationStatus == VerificationStatus.VERIFIED
            }.forEach { pattern ->
                actions.addView(button("Confirm this sign is heading ${pattern.direction}") {
                    userConfirmedRouteId = decision.routeId
                    userConfirmedPatternId = pattern.id
                    scannerContextKey = contextKey()
                    scannerSessionId = temporalTracker.updateContext(scannerContextKey)
                    scannerTargetId?.let(temporalTracker::selectTarget)
                    scannerPipeline?.updateContext(scannerSessionId, scannerContextKey)
                    lastScannerDecision = null
                    updateScannerStatus("Direction confirmation applies to this selected sign. Reading it again…", Color.GRAY)
                    scannerHandler.removeCallbacks(scannerExpiry)
                })
            }
        }
        val patternId = decision?.patternId
        if (patternId != null) actions.addView(button("Route details") {
            detailsPatternOverride = patternId
            go(Screen.DETAILS)
        })
        if (decision?.kind == DecisionKind.IDENTIFIED && decision.patternId == null && decision.routeId != null) {
            catalog.patterns.filter {
                it.routeId == decision.routeId && it.active && it.verificationStatus == VerificationStatus.VERIFIED
            }.forEach { pattern ->
                actions.addView(button("Details — ${pattern.direction}") {
                    detailsPatternOverride = pattern.id
                    go(Screen.DETAILS)
                })
            }
        }
        if (scannerNeutral) actions.addView(button("Plan a journey") { go(Screen.HOME) })
        actions.addView(button(scanReturnLabel) { go(scannerReturnScreen) })
        if (scannerPermissionDenied) actions.addView(button("Try camera permission again") { requestScannerPermission() })
    }

    private fun openScanner(neutral: Boolean, returnTo: Screen) {
        if (screen == Screen.SCANNER) stopScannerResources()
        scannerNeutral = neutral
        scannerReturnScreen = returnTo
        scanReturnLabel = scannerReturnLabel(returnTo)
        scannerPermissionDenied = false
        scannerPermissionPermanentlyDenied = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
            getSharedPreferences(PREFS_SCANNER, MODE_PRIVATE).getBoolean(KEY_CAMERA_ASKED, false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        scannerPermissionDenied = scannerPermissionPermanentlyDenied
        scannerUnavailableReason = null
        scannerBoxes = emptyList()
        scannerSelectedBox = null
        scannerTargetId = null
        lastScannerDecision = null
        userConfirmedRouteId = null
        userConfirmedPatternId = null
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.beginSession(scannerContextKey)
        screen = Screen.SCANNER
        render()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScannerCamera()
        } else if (!scannerPermissionPermanentlyDenied) requestScannerPermission()
    }

    private fun scannerReturnLabel(returnTo: Screen): String =
        when (returnTo) { Screen.DETAILS -> "Back to stops"; Screen.RESULTS -> "See my routes"; Screen.EXPLORE -> "Back to Explore"; else -> "Back to Home" }

    private fun requestScannerPermission() {
        if (scannerPermissionInFlight) return
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return startScannerCamera()
        scannerPermissionDenied = false
        getSharedPreferences(PREFS_SCANNER, MODE_PRIVATE).edit().putBoolean(KEY_CAMERA_ASKED, true).apply()
        scannerPermissionInFlight = true
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showCameraPermissionDenied() {
        scannerPermissionInFlight = false
        scannerPermissionDenied = true
        scannerPermissionPermanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        scannerUnavailableReason = null
        if (screen == Screen.SCANNER) render()
    }

    private fun startScannerCamera() {
        val previewView = scannerPreview ?: return
        if (screen != Screen.SCANNER || scannerStarting || scannerBound ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) return
        scannerStarting = true
        scannerPermissionInFlight = false
        scannerUnavailableReason = null
        scannerBoxes = emptyList()
        scannerSelectedBox = null
        scannerTargetId = null
        lastScannerDecision = null
        val generation = ++scannerGeneration
        scannerSessionId = temporalTracker.beginSession(contextKey())
        updateScannerStatus("Starting camera preview…", Color.GRAY)
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (generation != scannerGeneration || screen != Screen.SCANNER || scannerPreview !== previewView) return@addListener
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                cameraProvider = provider
                scannerBound = true
                scannerStarting = false
                scannerUnavailableReason = null
                updateScannerStatus("Camera ready. Route recognition is currently unavailable.", Color.GRAY)
            } catch (_: Exception) {
                scannerStarting = false
                scannerUnavailableReason = "Camera is unavailable or already in use. You can retry or keep planning manually."
                updateScannerStatus(scannerUnavailableReason!!, Color.GRAY)
                if (screen == Screen.SCANNER) renderScannerActions(null)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun selectScannerTarget(box: SignBox) {
        val id = scannerPipeline?.selectTarget(box) ?: return
        if (id != scannerTargetId) {
            userConfirmedRouteId = null
            userConfirmedPatternId = null
            scannerContextKey = contextKey()
            scannerSessionId = temporalTracker.updateContext(scannerContextKey)
            scannerPipeline?.updateContext(scannerSessionId, scannerContextKey)
            temporalTracker.selectTarget(id)
            scannerTargetId = id
            lastScannerDecision = null
            scannerHandler.removeCallbacks(scannerExpiry)
            updateScannerStatus("Sign selected — checking two matching readings…", Color.DKGRAY)
            renderScannerActions(null)
        }
        scannerSelectedBox = box
        scannerOverlay?.update(scannerBoxes, scannerSelectedBox, scannerFrameWidth, scannerFrameHeight)
    }

    private fun selectCenteredScannerTarget() {
        val pipeline = scannerPipeline ?: return
        val id = pipeline.selectNearestCenter(scannerBoxes, scannerFrameWidth, scannerFrameHeight)
        if (id == null) {
            updateScannerStatus("Move one sign into the center and try again.", Color.GRAY)
            return
        }
        scannerTargetId = id
        userConfirmedRouteId = null
        userConfirmedPatternId = null
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.updateContext(scannerContextKey)
        pipeline.updateContext(scannerSessionId, scannerContextKey)
        temporalTracker.selectTarget(id)
        lastScannerDecision = null
        scannerSelectedBox = pipeline.currentTargetBox()
        scannerOverlay?.update(scannerBoxes, scannerSelectedBox, scannerFrameWidth, scannerFrameHeight)
        updateScannerStatus("Sign selected — checking two matching readings…", Color.DKGRAY)
        renderScannerActions(null)
    }

    private fun showScannerUnavailable(reason: String) {
        scannerUnavailableReason = reason
        scannerBoxes = emptyList()
        scannerSelectedBox = null
        scannerTargetId = null
        lastScannerDecision = null
        temporalTracker.invalidate()
        scannerOverlay?.update(emptyList(), null, 0, 0)
        updateScannerStatus("? $reason", Color.GRAY)
        renderScannerActions(null)
    }

    private fun stopCameraBinding() {
        scannerGeneration++
        cameraProvider?.unbindAll()
        cameraProvider = null
        scannerPipeline?.close()
        scannerPipeline = null
        scannerStarting = false
        scannerBound = false
        scannerHandler.removeCallbacks(scannerExpiry)
        temporalTracker.invalidate()
        scannerTargetId = null
        scannerSelectedBox = null
        scannerBoxes = emptyList()
        scannerOverlay?.update(emptyList(), null, 0, 0)
    }

    private fun stopScannerResources() {
        stopCameraBinding()
        userConfirmedRouteId = null
        userConfirmedPatternId = null
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.beginSession(scannerContextKey)
        lastScannerDecision = null
        scannerPreview = null
        scannerOverlay = null
        scannerStatus = null
        scannerActions = null
    }

    private fun contextKey(): String = if (scannerNeutral) "neutral" else listOf(
        "journey", origin?.id.orEmpty(), destination?.id.orEmpty(), selected?.patternId.orEmpty(),
        selected?.boarding?.sequence?.toString().orEmpty(), selected?.alighting?.sequence?.toString().orEmpty(),
        userConfirmedRouteId.orEmpty(), userConfirmedPatternId.orEmpty(),
    ).joinToString("|")

    private fun updateScannerStatus(message: String, color: Int) {
        val status = scannerStatus ?: return
        if (status.text.toString() != message) {
            status.text = message
            status.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
        }
        val isMatch = color == getColor(R.color.outcome_green)
        val isMismatch = color == getColor(R.color.outcome_red)
        val surface = when { isMatch -> R.color.match_surface; isMismatch -> R.color.mismatch_surface; else -> R.color.neutral_surface }
        status.background = rounded(surface, surface, 14)
        status.setPadding(dp(14), dp(14), dp(14), dp(14))
        status.setTextColor(if (isMatch || isMismatch) color else getColor(R.color.ink))
    }

    override fun onModelReady(modelHash: String) {
        if (screen != Screen.SCANNER || scannerPipeline == null) return
        scannerModelHash = modelHash
        if (screen == Screen.SCANNER && scannerUnavailableReason == null) {
            updateScannerStatus("Experimental detector ready. Tap one sign outline to read it.", Color.DKGRAY)
        }
    }

    override fun onModelUnavailable(reason: String) {
        if (screen != Screen.SCANNER || scannerPipeline == null) return
        scannerModelHash = ""
        showScannerUnavailable(reason)
    }

    override fun onDetections(
        sessionId: Long,
        contextKey: String,
        boxes: List<SignBox>,
        selectedBox: SignBox?,
        targetId: String?,
        captureElapsedMs: Long,
        frameWidth: Int,
        frameHeight: Int,
    ) {
        if (screen != Screen.SCANNER || scannerPipeline == null || sessionId != scannerSessionId || contextKey != scannerContextKey) return
        analyzedFrameCount++
        scannerBoxes = boxes
        scannerFrameWidth = frameWidth
        scannerFrameHeight = frameHeight
        if (scannerTargetId != null && targetId == null) {
            scannerTargetId = null
            scannerSelectedBox = null
            userConfirmedRouteId = null
            userConfirmedPatternId = null
            temporalTracker.selectTarget(null)
            temporalTracker.invalidate()
            lastScannerDecision = null
            scannerHandler.removeCallbacks(scannerExpiry)
            updateScannerStatus("? Sign lost — select one sign again", Color.GRAY)
            renderScannerActions(null)
        } else if (targetId != null) {
            scannerTargetId = targetId
            scannerSelectedBox = selectedBox
        }
        scannerOverlay?.update(boxes, scannerSelectedBox, frameWidth, frameHeight)
    }

    override fun onText(sessionId: Long, contextKey: String, targetId: String, captureElapsedMs: Long, text: String) {
        if (screen != Screen.SCANNER || scannerPipeline == null || !temporalTracker.acceptsCallback(sessionId, contextKey, targetId)) return
        recognizedCropCount++
        val routeResolution = ScannerDecisions.routeFromText(text, catalog)
        val routeId = (routeResolution as? RouteTextResolution.Found)?.routeId
        val matchedPatterns = routeId?.let { ScannerDecisions.patternFromHeadsign(it, text, catalog) }.orEmpty()
        var observationSession = sessionId
        var observationContext = contextKey
        if (ScannerDecisions.conflictsWithConfirmation(
                routeResolution, matchedPatterns, userConfirmedRouteId, userConfirmedPatternId, text, catalog,
            )) {
            userConfirmedRouteId = null
            userConfirmedPatternId = null
            scannerContextKey = contextKey()
            scannerSessionId = temporalTracker.updateContext(scannerContextKey)
            scannerTargetId?.let(temporalTracker::selectTarget)
            scannerPipeline?.updateContext(scannerSessionId, scannerContextKey)
            observationSession = scannerSessionId
            observationContext = scannerContextKey
            lastScannerDecision = null
            scannerHandler.removeCallbacks(scannerExpiry)
            updateScannerStatus("Conflicting sign evidence cleared the direction confirmation. Checking again…", Color.GRAY)
            renderScannerActions(null)
        }
        val decision = ScannerDecisions.decide(
            routeResolution,
            matchedPatterns,
            userConfirmedPatternId,
            catalog,
            if (scannerNeutral) emptyList() else results,
            scannerNeutral,
            text,
        )
        if (temporalTracker.observe(observationSession, observationContext, targetId, captureElapsedMs, decision)) {
            lastScannerDecision = decision
            val latency = (android.os.SystemClock.elapsedRealtime() - captureElapsedMs).coerceAtLeast(0)
            if (decisionLatenciesMs.size == MAX_RECORDED_LATENCIES) decisionLatenciesMs.removeAt(0)
            decisionLatenciesMs += latency
            updateScannerStatus(decisionDescription(decision), decisionColor(decision.kind))
            renderScannerActions(decision)
            scannerHandler.removeCallbacks(scannerExpiry)
            scannerHandler.postDelayed(scannerExpiry, ScannerSettings().freshnessMs + 10)
        } else if (temporalTracker.current(captureElapsedMs) == null) {
            lastScannerDecision = null
            scannerHandler.removeCallbacks(scannerExpiry)
            updateScannerStatus("Reading sign — waiting for a second matching observation…", Color.DKGRAY)
            renderScannerActions(null)
        }
    }

    override fun onFailure() {
        if (screen != Screen.SCANNER || scannerPipeline == null) return
        temporalTracker.invalidate()
        lastScannerDecision = null
        showScannerUnavailable("Inference failed. Return to manual planning and try again later.")
    }

    private fun decisionDescription(decision: ScannerDecision): String = buildString {
        append(decision.message)
        decision.routeName?.let { append("\nRoute: ").append(it) }
        decision.direction?.let { append("\nDirection: ").append(it) }
        decision.directionSource?.let {
            append("\nEvidence: ").append(if (it == DirectionSource.SIGN) "sign-confirmed headsign" else "user-confirmed direction for this sign")
        }
    }

    private fun decisionColor(kind: DecisionKind): Int = when (kind) {
        DecisionKind.MATCH -> getColor(R.color.outcome_green)
        DecisionKind.NO_MATCH -> getColor(R.color.outcome_red)
        DecisionKind.IDENTIFIED -> Color.DKGRAY
        else -> Color.GRAY
    }

    private fun modelAssetAvailable(): Boolean = runCatching { assets.open(ScannerPipeline.MODEL_ASSET).use { true } }.getOrDefault(false)

    private fun shareEvaluationSummary() {
        val sorted = decisionLatenciesMs.sorted()
        fun percentile(value: Double): Long? = sorted.takeIf { it.isNotEmpty() }?.let {
            it[((it.size - 1) * value).toInt().coerceIn(0, it.lastIndex)]
        }
        val exportedModelHash = if (modelAssetAvailable()) scannerModelHash.ifBlank { EXPECTED_MODEL_HASH } else null
        val modelHashJson = exportedModelHash?.let { "\"$it\"" } ?: "null"
        val summary = """{"schema_version":1,"app_version":"${BuildConfig.VERSION_NAME}","dataset_version":"${catalog.info.datasetVersion}","model_version":"${ScannerPipeline.MODEL_VERSION}","model_sha256":$modelHashJson,"runtime":"${ScannerPipeline.RUNTIME_VERSION}","ocr":"${ScannerPipeline.OCR_VERSION}","settings":"${ScannerSettings().version}","analyzed_frames":$analyzedFrameCount,"recognized_crops":$recognizedCropCount,"latency_samples":${decisionLatenciesMs.size},"capture_to_display_median_ms":${percentile(0.5) ?: "null"},"capture_to_display_p95_ms":${percentile(0.95) ?: "null"}}"""
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
        }, "Share evaluation summary"))
    }

    private fun chooseLocation(title: String, candidates: List<Location>, selected: (Location) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(candidates.map(Location::name).toTypedArray()) { _, which -> selected(candidates[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatus(message: String) {
        statusMessage = message
        render()
    }

    private fun go(next: Screen) {
        if (next == Screen.DETAILS && screen != Screen.DETAILS) detailsMapCamera = null
        if (screen != next) {
            currentFocus?.let { focused ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focused.windowToken, 0)
                focused.clearFocus()
            }
        }
        if (screen == Screen.SCANNER && next != Screen.SCANNER) stopScannerResources()
        screen = next
        render()
        findViewById<android.widget.ScrollView>(R.id.screen_scroll).scrollTo(0, 0)
    }

    private fun resetMapCamera() {
        resultsMapCamera = null
        detailsMapCamera = null
    }

    private fun editField(hintText: String, value: String, id: Int, onChange: (String) -> Unit): EditText = AutoCompleteTextView(this).apply {
        this.id = id
        hint = hintText
        setText(value)
        textSize = 16f
        minHeight = dp(56)
        typeface = resources.getFont(R.font.atkinson_hyperlegible_next)
        setTextColor(getColor(R.color.ink))
        background = rounded(R.color.card_white, R.color.card_border, 12)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        contentDescription = hintText
        if (id == R.id.home_origin || id == R.id.home_destination) {
            val choices = catalog.locations.flatMap { listOf(it.name) + it.aliases }.distinct().sorted()
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, choices))
            threshold = 1
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun addLocationField(title: String, hint: String, value: String, id: Int, onChange: (String) -> Unit) {
        val field = editField(hint, value, id, onChange)
        val container = verticalContainer().apply {
            background = rounded(R.color.card_white, R.color.card_border, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(label(title).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, 0) })
            field.background = null
            field.setPadding(0, 0, 0, 0)
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this@MainActivity).apply {
                text = if (value.isBlank()) "Choose" else getString(R.string.change); textSize = 16f; setTextColor(getColor(R.color.blue)); minHeight = resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
                gravity = android.view.Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0)
                contentDescription = "$text $title"; isClickable = true; isFocusable = true
                val highlight = android.util.TypedValue()
                if (theme.resolveAttribute(android.R.attr.selectableItemBackground, highlight, true)) setBackgroundResource(highlight.resourceId)
                setOnClickListener { openLocationSearch(id == R.id.home_origin) }
            })
            addView(row)
        }
        add(container)
    }

    private fun addNavButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val control = button(text, action).apply {
            setTextColor(getColor(R.color.blue))
            background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), rounded(R.color.card_white, R.color.blue, 14), null)
        }
        parent.addView(control, LinearLayout.LayoutParams(0, dp(64), 1f).apply { if (parent.childCount < 2) marginEnd = dp(6) })
    }

    private fun updateNavigation() {
        val nav = findViewById<LinearLayout>(R.id.navigation)
        if (nav.childCount < 3) return
        val explore = screen == Screen.EXPLORE || (screen == Screen.DETAILS && detailsBackScreen() == Screen.EXPLORE) || (screen == Screen.SCANNER && scannerNeutral) || (screen == Screen.ABOUT && aboutReturnScreen == Screen.EXPLORE)
        for (i in 0 until nav.childCount) {
            val control = nav.getChildAt(i) as Button
            val active = when (i) { 0 -> !explore && screen != Screen.SCANNER; 1 -> screen == Screen.SCANNER; else -> explore && screen != Screen.SCANNER }
            control.isSelected = active
            control.contentDescription = if (active) "${control.text}, current tab" else control.text
            control.setTextColor(if (active) Color.WHITE else getColor(R.color.blue))
            control.background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), if (active || i == 1) rounded(R.color.blue, R.color.blue, 16) else rounded(R.color.card_white, R.color.card_border, 16), null)
            if (i == 1) {
                control.setTextColor(Color.WHITE)
                control.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_camera, 0, 0)
                control.compoundDrawablePadding = 0
                control.setPadding(dp(4), dp(5), dp(4), dp(5))
                control.textSize = 14f
                control.contentDescription = if (active) "Scan a jeepney sign, current screen" else "Scan a jeepney sign"
            }
        }
    }

    private fun addButton(text: String, action: () -> Unit) = add(button(text, action))

    private fun secondaryButton(text: String, action: () -> Unit) = button(text, action).apply {
        setTextColor(getColor(R.color.blue))
        background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), rounded(R.color.card_white, R.color.blue, 14), null)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.create(resources.getFont(R.font.atkinson_hyperlegible_next), Typeface.BOLD)
        setTextColor(Color.WHITE)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(0x443F6FAA), rounded(R.color.blue, R.color.blue, 14), null)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(getColor(fill))
        setStroke(dp(1), getColor(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    private fun addHeading(text: String) = add(TextView(this).apply {
        this.text = text
        textSize = 24f
        typeface = Typeface.create(resources.getFont(R.font.atkinson_hyperlegible_next), Typeface.BOLD)
        setTextColor(getColor(R.color.ink))
        setPadding(0, dp(12), 0, dp(8))
        isAccessibilityHeading = true
    })

    private fun addBody(text: String) = add(label(text).apply {
        if (screen == Screen.ABOUT) {
            background = rounded(R.color.card_white, R.color.card_border, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
    })

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = resources.getFont(R.font.atkinson_hyperlegible_next)
        setTextColor(getColor(R.color.ink))
        setPadding(dp(4), dp(8), dp(4), dp(8))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun verticalContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    private fun locationName(id: String): String = catalog.locations.firstOrNull { it.id == id }?.name ?: "Unknown location"
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Screen { HOME, SEARCH, RESULTS, NO_ROUTE, DETAILS, EXPLORE, ABOUT, SCANNER }

    companion object {
        private const val KEY_ORIGIN_TEXT = "origin_text"
        private const val KEY_DESTINATION_TEXT = "destination_text"
        private const val KEY_ORIGIN_ID = "origin_id"
        private const val KEY_DESTINATION_ID = "destination_id"
        private const val KEY_SCREEN = "screen"
        private const val KEY_EXPLORE_PATTERN = "explore_pattern"
        private const val KEY_DETAILS_PATTERN = "details_pattern"
        private const val KEY_DETAILS_TEXT_ONLY = "details_text_only"
        private const val KEY_SEARCH_ORIGIN = "search_origin"
        private const val KEY_LOCATION_QUERY = "location_query"
        private const val KEY_ABOUT_RETURN = "about_return"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_BOARD = "board"
        private const val KEY_ALIGHT = "alight"
        private const val KEY_SCANNER_NEUTRAL = "scanner_neutral"
        private const val KEY_SCANNER_RETURN = "scanner_return"
        private const val KEY_RESULTS_MAP_CAMERA = "results_map_camera"
        private const val KEY_DETAILS_MAP_CAMERA = "details_map_camera"
        private const val EXPLORE_LIST_TAG = "explore-list"
        private const val LOCATION_LIST_TAG = "location-list"
        private const val MAX_RECORDED_LATENCIES = 100
        private const val EXPECTED_MODEL_HASH = "183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8"
        private const val PREFS_SCANNER = "scanner_permissions"
        private const val KEY_CAMERA_ASKED = "camera_asked"
    }
}
