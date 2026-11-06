package com.parapo.prototype.routing

import com.parapo.prototype.data.JourneyMatch
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus
import java.util.Locale

/** Independent correctness baseline: scans each finite ordered pattern without graph helpers. */
class OrderedScanBaseline(catalog: RouteCatalog) {
    private val routes = catalog.routes.associateBy { it.id }
    private val orderedPatterns = catalog.patterns.sortedWith(
        compareBy({ routes[it.routeId]?.name.orEmpty().lowercase(Locale.ROOT) }, { it.direction }, { it.id }),
    )
    private val stopsByPattern = catalog.occurrences.groupBy(StopOccurrence::patternId)
        .mapValues { (_, stops) -> stops.sortedBy(StopOccurrence::sequence) }

    fun findDirectJourneys(originId: String, destinationId: String): List<JourneyMatch> {
        if (originId == destinationId) return emptyList()
        return orderedPatterns
            .filter { it.active && it.verificationStatus == VerificationStatus.VERIFIED }
            .mapNotNull { pattern ->
                val stops = stopsByPattern[pattern.id].orEmpty()
                val boardingIndex = stops.indexOfFirst { it.locationId == originId && it.boardingAllowed }
                if (boardingIndex < 0) return@mapNotNull null
                val boarding = stops[boardingIndex]
                val alighting = stops.drop(boardingIndex + 1).firstOrNull {
                    it.locationId == destinationId && it.alightingAllowed
                } ?: return@mapNotNull null
                JourneyMatch(pattern.routeId, pattern.id, boarding, alighting)
            }
    }
}
