package com.parapo.prototype.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
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

class MainActivity : Activity() {
    private val backNavigationCallback = OnBackInvokedCallback { handleBackNavigation() }
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
    private var statusMessage: String? = null

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
        selected?.let {
            outState.putString(KEY_PATTERN, it.patternId)
            outState.putInt(KEY_BOARD, it.boarding.sequence)
            outState.putInt(KEY_ALIGHT, it.alighting.sequence)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backNavigationCallback)
        super.onDestroy()
    }

    private fun handleBackNavigation() {
        when (screen) {
            Screen.DETAILS -> go(if (selected != null) Screen.RESULTS else Screen.EXPLORE)
            Screen.SCAN_FALLBACK -> go(Screen.RESULTS)
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
            Screen.SCAN_FALLBACK -> renderScanFallback()
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
            card.addView(button("Details") { selected = match; go(Screen.DETAILS) })
            card.addView(button("Scan") { selected = match; showScanFallback() })
            add(card)
        }
        statusMessage?.let { addBody(it) }
        addButton("Back to plan") { go(Screen.HOME) }
    }

    private fun showScanFallback() {
        go(Screen.SCAN_FALLBACK)
    }

    private fun renderScanFallback() {
        addHeading("Scanner unavailable")
        addBody("Camera recognition is not included in this M2 build. Your selected journey is saved.")
        selected?.let { match ->
            val route = catalog.routes.firstOrNull { it.id == match.routeId }
            val pattern = catalog.patterns.firstOrNull { it.id == match.patternId }
            addBody("${route?.name.orEmpty()} · ${pattern?.direction.orEmpty()}")
            addBody("Board at ${locationName(match.boarding.locationId)}; get off at ${locationName(match.alighting.locationId)}.")
        }
        addButton("View details") { go(Screen.DETAILS) }
        addButton("Back to results") { go(Screen.RESULTS) }
    }

    private fun renderDetails() {
        val match = selected
        val patternId = match?.patternId ?: explorePatternId
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
        addButton("Back") { go(if (selected != null) Screen.RESULTS else Screen.EXPLORE) }
    }

    private fun renderExplore() {
        addHeading("Explore routes")
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
            list.addView(button("Open ${route.name} details") { selected = null; explorePatternId = pattern.id; go(Screen.DETAILS) })
        }
        content.addView(list)
    }

    private fun renderAbout() {
        addHeading("About / Data")
        addBody("App version: ${BuildConfig.VERSION_NAME}")
        addBody("Dataset version: ${catalog.info.datasetVersion}")
        addBody("Verified route patterns: ${catalog.patterns.count { it.active && it.verificationStatus == VerificationStatus.VERIFIED }}")
        addBody("Recognition model: Not included in this M2 build.")
        addBody("Coverage is limited to the Jaro Plaza–WVSU corridor on Routes 3 and 10. The route maps show streets, not schedules or service frequency.")
        addBody("The offline route diagram is schematic, not turn-by-turn navigation. Manual planning does not request camera or network permission.")
        addBody("Sources: Iloilo City Planning route maps and LTFRB MC 2023-036; stop coordinates © OpenStreetMap contributors. Route direction was project-lead confirmed.")
        addBody("Route-data policy: the project lead rechecks these sources at least every 90 days, before each data release, and when a route change is reported. A conflicting or unconfirmed pattern is marked unavailable until checked again.")
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

    private enum class Screen { HOME, RESULTS, DETAILS, EXPLORE, ABOUT, SCAN_FALLBACK }

    companion object {
        private const val KEY_ORIGIN_TEXT = "origin_text"
        private const val KEY_DESTINATION_TEXT = "destination_text"
        private const val KEY_ORIGIN_ID = "origin_id"
        private const val KEY_DESTINATION_ID = "destination_id"
        private const val KEY_SCREEN = "screen"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_BOARD = "board"
        private const val KEY_ALIGHT = "alight"
        private const val EXPLORE_LIST_TAG = "explore-list"
    }
}
