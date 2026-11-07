package com.parapo.prototype.ui

import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
            if (screen == Screen.SCANNER) render()
            startScannerCamera()
        } else showCameraPermissionDenied()
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
    private var statusMessage: String? = null
    private var scannerNeutral = false
    private var scannerReturnScreen = Screen.RESULTS
    private var scannerContextKey = ""
    private var scannerSessionId = 0L
    private var scannerPermissionDenied = false
    private var scannerStarting = false
    private var scannerBound = false
    private var scannerModelHash = ""
    private var scannerUnavailableReason: String? = null
    private var scannerPermissionInFlight = false
    private var scannerPipeline: ScannerPipeline? = null
    private var cameraProvider: ProcessCameraProvider? = null
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
    private var scanReturnLabel = "Manual planning"
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
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.screen_content)
        coverage = findViewById(R.id.coverage_notice)
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
        scannerNeutral = savedInstanceState?.getBoolean(KEY_SCANNER_NEUTRAL) ?: false
        scannerReturnScreen = savedInstanceState?.getString(KEY_SCANNER_RETURN)?.let {
            runCatching { Screen.valueOf(it) }.getOrNull()
        } ?: Screen.RESULTS
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.beginSession(scannerContextKey)
        updateCoverage()
        render()
        findViewById<View>(R.id.navigation).also { nav ->
            addNavButton(nav as LinearLayout, "Home") { go(Screen.HOME) }
            addNavButton(nav, "Explore") { go(Screen.EXPLORE) }
            addNavButton(nav, "About / Data") { go(Screen.ABOUT) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ORIGIN_TEXT, originText)
        outState.putString(KEY_DESTINATION_TEXT, destinationText)
        outState.putString(KEY_ORIGIN_ID, origin?.id)
        outState.putString(KEY_DESTINATION_ID, destination?.id)
        outState.putString(KEY_SCREEN, screen.name)
        outState.putBoolean(KEY_SCANNER_NEUTRAL, scannerNeutral)
        outState.putString(KEY_SCANNER_RETURN, scannerReturnScreen.name)
        selected?.let {
            outState.putString(KEY_PATTERN, it.patternId)
            outState.putInt(KEY_BOARD, it.boarding.sequence)
            outState.putInt(KEY_ALIGHT, it.alighting.sequence)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (screen == Screen.SCANNER) {
            if (!LIVE_SCANNER_ENABLED) {
                if (scannerPreview == null) render()
                return
            }
            val cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) scannerPermissionDenied = false
            if (scannerPreview == null) render()
            if (cameraGranted) startScannerCamera()
            else if (!scannerPermissionInFlight && !scannerPermissionDenied) showCameraPermissionDenied()
        }
    }

    override fun onPause() {
        if (screen == Screen.SCANNER) stopScannerResources()
        super.onPause()
    }

    override fun onDestroy() {
        stopScannerResources()
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backNavigationCallback)
        super.onDestroy()
    }

    private fun handleBackNavigation() {
        when (screen) {
            Screen.DETAILS -> go(if (selected != null) Screen.RESULTS else Screen.EXPLORE)
            Screen.SCANNER -> go(scannerReturnScreen)
            Screen.RESULTS, Screen.EXPLORE, Screen.ABOUT -> go(Screen.HOME)
            Screen.HOME -> finish()
        }
    }

    private fun updateCoverage() {
        coverage.text = if (catalog.patterns.any { it.active && it.verificationStatus == VerificationStatus.VERIFIED }) {
            "Verified coverage: Jaro Plaza ↔ WVSU on Routes 3 and 10 only. Other routes and transfers are not covered."
        } else {
            "Verified route coverage is unavailable in this build. No route recommendations can be made."
        }
    }

    private fun render() {
        content.removeAllViews()
        when (screen) {
            Screen.HOME -> renderHome()
            Screen.RESULTS -> renderResults()
            Screen.DETAILS -> renderDetails()
            Screen.EXPLORE -> renderExplore()
            Screen.ABOUT -> renderAbout()
            Screen.SCANNER -> renderScanner()
        }
    }

    private fun renderHome() {
        addHeading("Plan a journey")
        val originField = editField("Origin", originText, R.id.home_origin) { originText = it }
        val destinationField = editField("Destination", destinationText, R.id.home_destination) { destinationText = it }
        add(originField)
        add(destinationField)
        addButton("Find routes") { findRoutes() }
        addButton("Explore routes") { go(Screen.EXPLORE) }
        statusMessage?.let { addBody(it) }
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
        results = planner.findDirectJourneys(from.id, to.id)
        selected = null
        if (results.isEmpty()) return showStatus("No direct route found in supported coverage. Transfers and routes outside coverage are not evaluated.")
        statusMessage = null
        go(Screen.RESULTS)
    }

    private fun renderResults() {
        addHeading("Direct routes")
        addBody("${origin?.name.orEmpty()} → ${destination?.name.orEmpty()}")
        results.forEach { match ->
            val pattern = catalog.patterns.first { it.id == match.patternId }
            val route = catalog.routes.first { it.id == match.routeId }
            val card = verticalContainer()
            card.addView(label("${route.name} · ${pattern.direction}"))
            card.addView(label("Board at ${locationName(match.boarding.locationId)}"))
            card.addView(label("Get off at ${locationName(match.alighting.locationId)}"))
            card.addView(label("Verified ${pattern.verificationDate}"))
            card.addView(button("Details") { selected = match; detailsPatternOverride = null; go(Screen.DETAILS) })
            card.addView(button("Scan") { selected = match; openScanner(neutral = false, returnTo = Screen.RESULTS) })
            add(card)
        }
        statusMessage?.let { addBody(it) }
        addButton("Back to plan") { go(Screen.HOME) }
    }

    private fun renderDetails() {
        val match = selected
        val patternId = detailsPatternOverride ?: match?.patternId ?: explorePatternId
        val pattern = catalog.patterns.firstOrNull { it.id == patternId }
        if (pattern == null) {
            addHeading("Route details")
            addBody("No verified directional route details are available in this build.")
            addButton("Back") { go(if (selected != null) Screen.RESULTS else Screen.EXPLORE) }
            return
        }
        val route = catalog.routes.firstOrNull { it.id == pattern.routeId }
        addHeading(route?.name ?: "Route details")
        addBody("Direction: ${pattern.direction}. ${pattern.branchHeadsign}")
        val pathPoints = catalog.geometry.filter { it.patternId == pattern.id }.sortedBy(GeometryPoint::sequence)
        if (pathPoints.size >= 2) {
            val diagram = RouteDiagramView(this).apply {
                points = pathPoints
                contentDescription = "Directional route diagram. The ordered stop list below gives the route direction and selected stops."
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                minimumHeight = dp(160)
            }
            add(diagram)
        } else {
            addBody("Local route geometry is unavailable; use the ordered stop list below.")
        }
        addHeading("Ordered stops")
        catalog.occurrences.filter { it.patternId == pattern.id }.sortedBy(StopOccurrence::sequence).forEachIndexed { index, stop ->
            val markers = when (stop.sequence) {
                match?.boarding?.sequence -> " · Board here"
                match?.alighting?.sequence -> " · Get off here"
                else -> ""
            }
            addBody("${index + 1}. ${locationName(stop.locationId)}$markers")
        }
        addBody("Source: ${catalog.sources.firstOrNull { it.id == pattern.sourceRef }?.description ?: "Unavailable"}")
        addBody("Verified: ${pattern.verificationDate}")
        addButton("Scan a sign") {
            openScanner(neutral = selected == null, returnTo = if (selected == null) Screen.EXPLORE else Screen.DETAILS)
        }
        addButton("Back") { go(if (selected != null) Screen.RESULTS else Screen.EXPLORE) }
    }

    private fun renderExplore() {
        addHeading("Explore routes")
        addButton("Scan a sign") { openScanner(neutral = true, returnTo = Screen.EXPLORE) }
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
            list.addView(label("${route.name} · ${pattern.direction}"))
            list.addView(button("Open ${route.name} details") { selected = null; detailsPatternOverride = null; explorePatternId = pattern.id; go(Screen.DETAILS) })
        }
        content.addView(list)
    }

    private fun renderAbout() {
        addHeading("About / Data")
        addBody("App version: ${BuildConfig.VERSION_NAME}")
        addBody("Dataset version: ${catalog.info.datasetVersion}")
        addBody("Verified route patterns: ${catalog.patterns.count { it.active && it.verificationStatus == VerificationStatus.VERIFIED }}")
        val modelAvailable = modelAssetAvailable()
        addBody("Recognition model: ${if (modelAvailable) ScannerPipeline.MODEL_VERSION else "Unavailable — local model asset missing"}")
        addBody("Model check: 0/3 M3 test signs detected; ONNX export parity failed. Phone smoke inference ran on a synthetic image; sign accuracy is unverified.")
        addBody("Live scanning is unavailable until export parity is resolved. Manual planning remains available.")
        addBody("Model SHA-256: ${if (modelAvailable) EXPECTED_MODEL_HASH else "not available"}")
        addBody("Inference runtime: ${ScannerPipeline.RUNTIME_VERSION}")
        addBody("Offline OCR: ${ScannerPipeline.OCR_VERSION}")
        addBody("Scanner settings: ${ScannerSettings().version}; 2 observations / 1 s window; 1 s result freshness (provisional).")
        addBody("Coverage is limited to the Jaro Plaza–WVSU corridor on Routes 3 and 10. The route maps show streets, not schedules or service frequency.")
        addBody("The offline route diagram is schematic, not turn-by-turn navigation. Camera access is requested only when scanning. Frames and OCR stay in memory.")
        addBody("Sources: Iloilo City Planning route maps and LTFRB MC 2023-036; stop coordinates © OpenStreetMap contributors. See the route data for direction provenance.")
        addBody("Route data should be rechecked every 90 days, before a data release, and when a route change is reported. Mark a conflicting or unconfirmed pattern unavailable until checked again.")
        addButton("Share evaluation summary") { shareEvaluationSummary() }
    }

    private fun renderScanner() {
        addHeading("Scan a route sign")
        addBody("Scan while stationary at a verified boarding area. Aim at one sign and select its outline.")
        if (scannerNeutral) addBody("Neutral route identification — any earlier journey selection is ignored for matching.")
        else selected?.let { match ->
            val route = catalog.routes.firstOrNull { it.id == match.routeId }
            val pattern = catalog.patterns.firstOrNull { it.id == match.patternId }
            addBody("Checking ${route?.name.orEmpty()} · ${pattern?.direction.orEmpty()}")
            addBody("Board at ${locationName(match.boarding.locationId)}; get off at ${locationName(match.alighting.locationId)}.")
        }
        scannerStatus = label("Camera scanner starting…")
        add(scannerStatus!!)
        if (!LIVE_SCANNER_ENABLED) {
            updateScannerStatus(LIVE_SCANNER_UNAVAILABLE, Color.GRAY)
            addScannerManualActions()
            return
        }
        if (scannerPermissionDenied) {
            updateScannerStatus("? Camera permission denied — manual planning is still available", Color.GRAY)
            addScannerManualActions()
            addButton("Try camera permission again") { requestScannerPermission() }
            addButton("Open app settings") {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                })
            }
            return
        }
        if (scannerUnavailableReason != null) {
            updateScannerStatus("? ${scannerUnavailableReason}", Color.GRAY)
            addScannerManualActions()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            updateScannerStatus("Camera access is needed for scanning. Manual planning works without it.", Color.GRAY)
            addScannerManualActions()
            addButton("Allow camera") { requestScannerPermission() }
            return
        }

        val previewBox = FrameLayout(this)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320))
        previewBox.layoutParams = params
        val preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            contentDescription = "Live camera preview. Point at one route sign while stationary."
        }
        val overlay = ScannerOverlayView(this).apply {
            onSelectBox = ::selectScannerTarget
        }
        previewBox.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        previewBox.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        add(previewBox)
        scannerPreview = preview
        scannerOverlay = overlay
        addButton("Select centered sign") { selectCenteredScannerTarget() }
        scannerActions = verticalContainer()
        content.addView(scannerActions)
        scannerOverlay?.update(scannerBoxes, scannerSelectedBox, scannerFrameWidth, scannerFrameHeight)
        addScannerManualActions()
        if (scannerPipeline == null && !scannerStarting && !scannerBound) startScannerCamera()
    }

    private fun addScannerManualActions() {
        scannerActions?.let { actions ->
            if (actions.parent == null) content.addView(actions)
            actions.removeAllViews()
            actions.addView(button(scanReturnLabel) { go(scannerReturnScreen) })
        } ?: addButton(scanReturnLabel) { go(scannerReturnScreen) }
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
        scanReturnLabel = if (neutral) "Back to Explore" else "Manual planning"
        scannerPermissionDenied = false
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
        if (!LIVE_SCANNER_ENABLED) return
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScannerCamera()
        } else requestScannerPermission()
    }

    private fun requestScannerPermission() {
        if (scannerPermissionInFlight) return
        scannerPermissionDenied = false
        scannerPermissionInFlight = true
        updateScannerStatus("Camera permission is requested only for sign scanning.", Color.GRAY)
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showCameraPermissionDenied() {
        scannerPermissionInFlight = false
        scannerPermissionDenied = true
        scannerUnavailableReason = null
        if (screen == Screen.SCANNER) render()
    }

    private fun startScannerCamera() {
        if (!LIVE_SCANNER_ENABLED || screen != Screen.SCANNER || scannerStarting || scannerBound ||
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) return
        scannerStarting = true
        scannerPermissionInFlight = false
        scannerUnavailableReason = null
        scannerContextKey = contextKey()
        scannerSessionId = temporalTracker.beginSession(scannerContextKey)
        updateScannerStatus("Loading bundled detector and starting camera…", Color.GRAY)
        val currentPipeline = ScannerPipeline(this, this)
        scannerPipeline = currentPipeline
        currentPipeline.updateContext(scannerSessionId, scannerContextKey)
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (screen != Screen.SCANNER || scannerPipeline !== currentPipeline) return@addListener
            try {
                val provider = providerFuture.get()
                val previewView = scannerPreview ?: error("Preview is unavailable")
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                currentPipeline.setAnalyzer(analysis)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cameraProvider = provider
                scannerBound = true
                scannerStarting = false
                updateScannerStatus("Aim at one sign. Tap its outline or center it and select it.", Color.DKGRAY)
            } catch (_: Exception) {
                scannerStarting = false
                showScannerUnavailable("Camera is unavailable. Your journey is saved for manual planning.")
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
        stopCameraBinding()
        updateScannerStatus("? $reason", Color.GRAY)
        renderScannerActions(null)
    }

    private fun stopCameraBinding() {
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
        status.setTextColor(color)
    }

    override fun onModelReady(modelHash: String) {
        scannerModelHash = modelHash
        if (screen == Screen.SCANNER && scannerUnavailableReason == null) {
            updateScannerStatus("Experimental detector ready. Tap one sign outline to read it.", Color.DKGRAY)
        }
    }

    override fun onModelUnavailable(reason: String) {
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
        if (screen != Screen.SCANNER || sessionId != scannerSessionId || contextKey != scannerContextKey) return
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
        if (screen != Screen.SCANNER || !temporalTracker.acceptsCallback(sessionId, contextKey, targetId)) return
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
        DecisionKind.MATCH -> Color.rgb(0, 115, 45)
        DecisionKind.NO_MATCH -> Color.rgb(180, 35, 35)
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
        if (screen == Screen.SCANNER && next != Screen.SCANNER) stopScannerResources()
        screen = next
        render()
    }

    private fun editField(hintText: String, value: String, id: Int, onChange: (String) -> Unit): EditText = EditText(this).apply {
        this.id = id
        hint = hintText
        setText(value)
        textSize = 16f
        minHeight = dp(56)
        contentDescription = hintText
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun addNavButton(parent: LinearLayout, text: String, action: () -> Unit) {
        parent.addView(button(text, action), LinearLayout.LayoutParams(0, dp(52), 1f))
    }

    private fun addButton(text: String, action: () -> Unit) = add(button(text, action))

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun addHeading(text: String) = add(TextView(this).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(8))
        isAccessibilityHeading = true
    })

    private fun addBody(text: String) = add(label(text))

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(dp(4), dp(8), dp(4), dp(8))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun verticalContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun locationName(id: String): String = catalog.locations.firstOrNull { it.id == id }?.name ?: "Unknown location"
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Screen { HOME, RESULTS, DETAILS, EXPLORE, ABOUT, SCANNER }

    companion object {
        private const val KEY_ORIGIN_TEXT = "origin_text"
        private const val KEY_DESTINATION_TEXT = "destination_text"
        private const val KEY_ORIGIN_ID = "origin_id"
        private const val KEY_DESTINATION_ID = "destination_id"
        private const val KEY_SCREEN = "screen"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_BOARD = "board"
        private const val KEY_ALIGHT = "alight"
        private const val KEY_SCANNER_NEUTRAL = "scanner_neutral"
        private const val KEY_SCANNER_RETURN = "scanner_return"
        private const val EXPLORE_LIST_TAG = "explore-list"
        private const val MAX_RECORDED_LATENCIES = 100
        private const val EXPECTED_MODEL_HASH = "183a212b84273fc58870bf4dfd10bb76f52cecabf9e0c7ac5b8f1d01563116d8"
        private const val LIVE_SCANNER_ENABLED = false // shortcut: export parity is unresolved; enable after a successful PyTorch comparison.
        private const val LIVE_SCANNER_UNAVAILABLE = "Detector unavailable: export parity failed or the model asset is missing/corrupt. Manual planning is still available."
    }
}
