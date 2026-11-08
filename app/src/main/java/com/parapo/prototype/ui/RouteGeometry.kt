package com.parapo.prototype.ui

import com.parapo.prototype.data.GeometryPoint

internal fun geoJsonCoordinates(points: List<GeometryPoint>): List<List<Double>> =
    points.map { listOf(it.longitude, it.latitude) }

internal fun routeSegment(points: List<GeometryPoint>, boardingSequence: Int, alightingSequence: Int): List<GeometryPoint> {
    val start = points.indexOfFirst { it.stopOccurrenceSequence == boardingSequence }
    val end = points.indexOfFirst { it.stopOccurrenceSequence == alightingSequence }
    return if (start >= 0 && end >= start) points.subList(start, end + 1) else emptyList()
}
