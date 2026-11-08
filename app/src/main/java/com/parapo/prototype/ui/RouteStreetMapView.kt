package com.parapo.prototype.ui

import android.content.Context
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.R
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.MapLibre
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal data class StreetMapPath(val id: String, val label: String, val color: Int, val points: List<GeometryPoint>, val incompleteEdges: Set<Int> = emptySet())
internal data class StreetMapMarker(val label: String, val point: GeometryPoint, val color: Int)

internal class RouteStreetMapView(
    context: Context,
    private val paths: List<StreetMapPath>,
    private var markers: List<StreetMapMarker>,
    selectedPathId: String?,
    private val initialCamera: CameraPosition?,
    private val cameraChanged: (CameraPosition) -> Unit,
) : FrameLayout(context) {
    private lateinit var mapView: MapView
    private val status = TextView(context)
    private val retry = Button(context)
    private var map: MapLibreMap? = null
    private var style: org.maplibre.android.maps.Style? = null
    private var selectedPathId = selectedPathId
    private var fitted = false
    private var destroyed = false

    init {
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context)
        minimumHeight = (240 * resources.displayMetrics.density).toInt()
        setBackgroundColor(context.getColor(R.color.neutral_surface))
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        status.apply {
            text = "Loading street map…"
            setTextColor(context.getColor(R.color.ink))
            setBackgroundColor(0xEEF8F7F3.toInt())
            textSize = 14f
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL))
        retry.apply {
            text = "Retry map"
            visibility = View.GONE
            setOnClickListener { loadStyle() }
        }
        addView(retry, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
        addView(TextView(context).apply {
            text = "© OpenStreetMap contributors · ODbL · OpenMapTiles · OpenFreeMap"
            textSize = 10f
            setTextColor(Color.DKGRAY)
            setBackgroundColor(0xEEF8F7F3.toInt())
            setPadding(dp(5), dp(3), dp(5), dp(3))
            contentDescription = "Map data © OpenStreetMap contributors under ODbL. Style by OpenMapTiles via OpenFreeMap."
            isClickable = true
            setOnClickListener { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM or android.view.Gravity.START))
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            if (destroyed) return@getMapAsync
            this.map = map
            map.addOnCameraMoveListener { cameraChanged(map.cameraPosition) }
            mapView.addOnDidFinishLoadingMapListener { updateNetworkStatus() }
            mapView.addOnDidFailLoadingMapListener {
                status.text = "Street tiles did not load. Route lines and stops remain below."
                status.visibility = View.VISIBLE
                retry.visibility = View.VISIBLE
            }
            if (initialCamera != null) map.cameraPosition = initialCamera
            loadStyle()
        }
    }

    private fun loadStyle() {
        status.text = "Loading street map…"
        status.visibility = View.VISIBLE
        retry.visibility = View.GONE
        map?.setStyle(STYLE_URL) { style ->
            this.style = style
            val features = mutableListOf<Feature>()
            val gaps = mutableListOf<Feature>()
            paths.forEach { path ->
                var chunk = mutableListOf<GeometryPoint>()
                (0 until path.points.lastIndex).forEach { index ->
                    val from = path.points[index]
                    val to = path.points[index + 1]
                    if (index in path.incompleteEdges) {
                        if (chunk.size > 1) features += lineFeature(path, chunk)
                        chunk = mutableListOf(to)
                        gaps += Feature.fromGeometry(LineString.fromLngLats(geoJsonCoordinates(listOf(from, to)).map { Point.fromLngLat(it[0], it[1]) })).apply { addStringProperty("path_id", path.id) }
                    } else chunk.addAll(listOf(from, to))
                }
                if (chunk.size > 1) features += lineFeature(path, chunk)
            }
            style.addSource(GeoJsonSource(PATH_SOURCE, FeatureCollection.fromFeatures(features)))
            paths.forEach { path ->
                style.addLayer(LineLayer("path-${path.id}", PATH_SOURCE).withFilter(
                    org.maplibre.android.style.expressions.Expression.eq(
                        org.maplibre.android.style.expressions.Expression.get("path_id"),
                        org.maplibre.android.style.expressions.Expression.literal(path.id),
                    ),
                ).withProperties(PropertyFactory.lineOpacity(0.82f)))
            }
            if (gaps.isNotEmpty()) {
                style.addSource(GeoJsonSource(GAP_SOURCE, FeatureCollection.fromFeatures(gaps)))
                paths.forEach { path -> style.addLayer(LineLayer("gap-${path.id}", GAP_SOURCE).withFilter(
                    org.maplibre.android.style.expressions.Expression.eq(org.maplibre.android.style.expressions.Expression.get("path_id"), org.maplibre.android.style.expressions.Expression.literal(path.id)),
                ).withProperties(PropertyFactory.lineColor(0xFFFFC04A.toInt()), PropertyFactory.lineWidth(4f), PropertyFactory.lineDasharray(arrayOf(1f, 2f)))) }
            }
            if (features.isNotEmpty()) {
                val labelFeatures = paths.filter { it.points.isNotEmpty() }.map { path ->
                    val point = path.points[path.points.size / 2]
                    Feature.fromGeometry(geoJsonCoordinates(listOf(point)).single().let { Point.fromLngLat(it[0], it[1]) }).apply {
                        addStringProperty("label", path.label)
                        addStringProperty("path_id", path.id)
                    }
                }
                style.addSource(GeoJsonSource(LABEL_SOURCE, FeatureCollection.fromFeatures(labelFeatures)))
                style.addLayer(SymbolLayer(PATH_LABELS, LABEL_SOURCE).withProperties(
                    PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f), PropertyFactory.textColor(Color.BLACK),
                    PropertyFactory.textHaloColor(Color.WHITE), PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true),
                ))
            }
            val markerFeatures = markerFeatures()
            if (markerFeatures.isNotEmpty()) {
                style.addSource(GeoJsonSource(MARKER_SOURCE, FeatureCollection.fromFeatures(markerFeatures)))
                style.addLayer(CircleLayer(MARKER_CIRCLES, MARKER_SOURCE).withProperties(
                    PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
                    PropertyFactory.circleStrokeColor(Color.WHITE), PropertyFactory.circleStrokeWidth(2f),
                ))
                style.addLayer(SymbolLayer(MARKER_LABELS, MARKER_SOURCE).withProperties(
                    PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f), PropertyFactory.textColor(Color.BLACK),
                    PropertyFactory.textHaloColor(Color.WHITE), PropertyFactory.textHaloWidth(2f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                    PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true),
                ))
            }
            status.visibility = View.GONE
            updateSelection()
            if (initialCamera == null && !fitted) fitBounds()
        }
    }

    fun selectPath(pathId: String, markers: List<StreetMapMarker>) {
        selectedPathId = pathId
        this.markers = markers
        updateSelection()
        (style?.getSourceAs<GeoJsonSource>(MARKER_SOURCE))?.setGeoJson(FeatureCollection.fromFeatures(markerFeatures()))
    }

    private fun updateSelection() {
        paths.forEach { path ->
            (style?.getLayer("path-${path.id}") as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(if (path.id == selectedPathId) 0xFFFFB02E.toInt() else path.color),
                PropertyFactory.lineWidth(if (path.id == selectedPathId) 8f else 5f),
                PropertyFactory.lineOpacity(if (path.id == selectedPathId) 1f else 0.82f),
            )
        }
    }

    private fun fitBounds() {
        val coords = paths.flatMap { it.points }.map { LatLng(it.latitude, it.longitude) } +
            markers.map { LatLng(it.point.latitude, it.point.longitude) }
        if (coords.isEmpty()) return
        val bounds = LatLngBounds.Builder().also { builder -> coords.forEach(builder::include) }.build()
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(24), dp(24), dp(24), dp(88)))
        fitted = true
    }

    private fun updateNetworkStatus() {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivity.activeNetwork
        val online = network != null && connectivity.getNetworkCapabilities(network)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        if (online) {
            status.visibility = View.GONE
            retry.visibility = View.GONE
        } else {
            status.text = "Offline: cached street tiles may be incomplete. Retry when connected."
            status.visibility = View.VISIBLE
            retry.visibility = View.VISIBLE
        }
    }

    private fun lineFeature(path: StreetMapPath, points: List<GeometryPoint>) = Feature.fromGeometry(
        LineString.fromLngLats(geoJsonCoordinates(points).map { Point.fromLngLat(it[0], it[1]) }),
    ).apply { addStringProperty("path_id", path.id); addStringProperty("label", path.label) }

    private fun markerFeatures() = markers.map { marker ->
        Feature.fromGeometry(geoJsonCoordinates(listOf(marker.point)).single().let { Point.fromLngLat(it[0], it[1]) }).apply {
            addStringProperty("label", marker.label)
            addStringProperty("color", "#%06X".format(marker.color and 0xFFFFFF))
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }

    fun onStart() = mapView.onStart()
    fun onResume() = mapView.onResume()
    fun onPause() = mapView.onPause()
    fun onStop() = mapView.onStop()
    fun onDestroy() { destroyed = true; mapView.onDestroy() }
    fun onLowMemory() = mapView.onLowMemory()
    fun saveState(outState: android.os.Bundle) = mapView.onSaveInstanceState(outState)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val PATH_SOURCE = "parapo-paths"
        private const val GAP_SOURCE = "parapo-route-gaps"
        private const val LABEL_SOURCE = "parapo-path-labels"
        private const val MARKER_SOURCE = "parapo-journey-markers"
        private const val PATH_LABELS = "parapo-path-labels-layer"
        private const val MARKER_CIRCLES = "parapo-marker-circles"
        private const val MARKER_LABELS = "parapo-marker-labels"
    }
}
