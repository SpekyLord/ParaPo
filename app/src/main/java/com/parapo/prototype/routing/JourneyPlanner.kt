package com.parapo.prototype.routing

import com.parapo.prototype.data.JourneyMatch
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus
import java.util.ArrayDeque
import java.util.Locale

data class OccurrenceNode(val patternId: String, val sequence: Int)

class JourneyPlanner(private val catalog: RouteCatalog) {
    private val occurrencesByPattern = catalog.occurrences.groupBy(StopOccurrence::patternId)
        .mapValues { (_, stops) -> stops.sortedBy(StopOccurrence::sequence) }
    private val routesById = catalog.routes.associateBy { it.id }
    private val orderedPatterns = catalog.patterns.sortedWith(
        compareBy({ routesById[it.routeId]?.name.orEmpty().lowercase(Locale.ROOT) }, { it.direction }, { it.id }),
    )
    private val adjacency: Map<OccurrenceNode, List<OccurrenceNode>> = buildMap {
        orderedPatterns.forEach { pattern ->
            val ordered = occurrencesByPattern[pattern.id].orEmpty()
            ordered.zipWithNext().forEach { (from, to) ->
                put(OccurrenceNode(pattern.id, from.sequence), listOf(OccurrenceNode(pattern.id, to.sequence)))
            }
        }
    }

    fun findDirectJourneys(originId: String, destinationId: String): List<JourneyMatch> {
        if (originId == destinationId) return emptyList()
        val results = mutableListOf<JourneyMatch>()
        orderedPatterns.asSequence()
            .filter { it.active && it.verificationStatus == VerificationStatus.VERIFIED }
            .forEach { pattern ->
                val stops = occurrencesByPattern[pattern.id].orEmpty()
                val boarding = stops.firstOrNull { it.locationId == originId && it.boardingAllowed } ?: return@forEach
                val reachable = breadthFirst(OccurrenceNode(pattern.id, boarding.sequence))
                val alighting = stops.firstOrNull {
                    it.locationId == destinationId && it.alightingAllowed &&
                        it.sequence > boarding.sequence && OccurrenceNode(pattern.id, it.sequence) in reachable
                } ?: return@forEach
                results += JourneyMatch(pattern.routeId, pattern.id, boarding, alighting)
            }
        return results
    }

    private fun breadthFirst(start: OccurrenceNode): Set<OccurrenceNode> {
        val visited = linkedSetOf(start)
        val queue = ArrayDeque<OccurrenceNode>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            adjacency[queue.removeFirst()].orEmpty().forEach { next ->
                if (visited.add(next)) queue.addLast(next)
            }
        }
        return visited
    }
}
