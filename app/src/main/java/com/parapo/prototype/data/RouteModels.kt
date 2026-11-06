package com.parapo.prototype.data

import java.util.Locale

enum class VerificationStatus { VERIFIED, UNVERIFIED }

data class Route(
    val id: String,
    val name: String,
    val aliases: List<String>,
)

data class Pattern(
    val id: String,
    val routeId: String,
    val direction: String,
    val branchHeadsign: String,
    val active: Boolean,
    val verificationStatus: VerificationStatus,
    val verificationDate: String,
    val sourceRef: String,
)

data class Location(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val latitude: Double,
    val longitude: Double,
    val type: String,
)

data class StopOccurrence(
    val patternId: String,
    val sequence: Int,
    val locationId: String,
    val boardingAllowed: Boolean,
    val alightingAllowed: Boolean,
)

data class GeometryPoint(
    val patternId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val stopOccurrenceSequence: Int?,
)

data class SourceRecord(
    val id: String,
    val description: String,
    val reference: String,
    val contactRole: String,
)

data class DatasetInfo(
    val schemaVersion: Int,
    val datasetVersion: String,
    val publicationDate: String,
    val sourceRefs: List<String>,
)

data class RouteCatalog(
    val info: DatasetInfo,
    val routes: List<Route>,
    val patterns: List<Pattern>,
    val locations: List<Location>,
    val occurrences: List<StopOccurrence>,
    val geometry: List<GeometryPoint>,
    val sources: List<SourceRecord>,
) {
    companion object {
        val EMPTY = RouteCatalog(
            info = DatasetInfo(1, "none", "", emptyList()),
            routes = emptyList(),
            patterns = emptyList(),
            locations = emptyList(),
            occurrences = emptyList(),
            geometry = emptyList(),
            sources = emptyList(),
        )
    }
}

data class JourneyMatch(
    val routeId: String,
    val patternId: String,
    val boarding: StopOccurrence,
    val alighting: StopOccurrence,
)

fun normalizeAlias(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
