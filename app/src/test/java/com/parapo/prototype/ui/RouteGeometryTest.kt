package com.parapo.prototype.ui

import com.parapo.prototype.data.GeometryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    @Test fun geoJsonCoordinatesUseLongitudeThenLatitude() {
        assertEquals(listOf(listOf(122.5, 10.7)), geoJsonCoordinates(listOf(GeometryPoint("p", 1, 10.7, 122.5, 1))))
    }

    @Test fun selectedRouteSegmentIsInclusiveAndRejectsReversedAnchors() {
        val points = listOf(
            GeometryPoint("p", 1, 10.0, 122.0, 1),
            GeometryPoint("p", 2, 10.1, 122.1, null),
            GeometryPoint("p", 3, 10.2, 122.2, 2),
        )
        assertEquals(points, routeSegment(points, 1, 2))
        assertTrue(routeSegment(points, 2, 1).isEmpty())
    }
}
