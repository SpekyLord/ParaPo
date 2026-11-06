package com.parapo.prototype

import com.parapo.prototype.data.DatasetInfo
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.data.Location
import com.parapo.prototype.data.Pattern
import com.parapo.prototype.data.Route
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.SourceRecord
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus

data class StopSpec(
    val locationId: String,
    val board: Boolean = false,
    val alight: Boolean = false,
)

data class PatternSpec(
    val id: String,
    val routeName: String,
    val stops: List<StopSpec>,
    val direction: String = "Northbound",
    val active: Boolean = true,
    val verification: VerificationStatus = VerificationStatus.VERIFIED,
)

fun syntheticCatalog(specs: List<PatternSpec>, aliases: Map<String, List<String>> = emptyMap()): RouteCatalog {
    val locationIds = specs.flatMap { it.stops }.map(StopSpec::locationId).distinct()
    val locations = locationIds.mapIndexed { index, id ->
        Location(id, id, aliases[id].orEmpty(), 14.0 + index * 0.001, 122.0 + index * 0.001, "landmark")
    }
    val routes = specs.map { Route("route-${it.id}", it.routeName, listOf("alias-${it.id}")) }
    val patterns = specs.map {
        Pattern(it.id, "route-${it.id}", it.direction, "Branch/headsign ${it.direction}", it.active, it.verification, "2026-11-06", "synthetic-source")
    }
    val occurrences = specs.flatMap { spec ->
        spec.stops.mapIndexed { index, stop -> StopOccurrence(spec.id, index + 1, stop.locationId, stop.board, stop.alight) }
    }
    val geometry = specs.flatMap { spec ->
        spec.stops.mapIndexed { index, stop ->
            val location = locations.first { it.id == stop.locationId }
            GeometryPoint(spec.id, index + 1, location.latitude, location.longitude, index + 1)
        }
    }
    return RouteCatalog(
        DatasetInfo(1, "synthetic-test-only", "2026-11-06", listOf("synthetic-source")),
        routes,
        patterns,
        locations,
        occurrences,
        geometry,
        listOf(SourceRecord("synthetic-source", "Synthetic test fixture", "test-only", "test fixture")),
    )
}

fun patternSpec(
    id: String,
    routeName: String = id,
    vararg stops: StopSpec,
) = PatternSpec(id, routeName, stops.toList())
