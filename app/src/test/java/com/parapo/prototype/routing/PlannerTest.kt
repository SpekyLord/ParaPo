package com.parapo.prototype.routing

import com.parapo.prototype.PatternSpec
import com.parapo.prototype.StopSpec
import com.parapo.prototype.data.RouteCatalogValidator
import com.parapo.prototype.data.Location
import com.parapo.prototype.data.VerificationStatus
import com.parapo.prototype.patternSpec
import com.parapo.prototype.syntheticCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val fixtures = listOf(
        "SYN-FWD" to patternSpec("fwd", "Route B", StopSpec("A", board = true), StopSpec("B", alight = true), StopSpec("C")),
        "SYN-REVERSE" to patternSpec("reverse", "Reverse", StopSpec("C", alight = true), StopSpec("B"), StopSpec("A", board = true)),
        "SYN-REPEAT-LATE" to patternSpec("repeat", "Repeat", StopSpec("B", alight = true), StopSpec("A", board = true), StopSpec("X"), StopSpec("B", alight = true)),
        "SYN-NO-BOARD" to patternSpec("no-board", "No board", StopSpec("A"), StopSpec("B", alight = true)),
        "SYN-NO-ALIGHT" to patternSpec("no-alight", "No alight", StopSpec("A", board = true), StopSpec("B")),
        "SYN-LOOP-TERMINAL" to patternSpec("terminal", "Finite loop", StopSpec("B", alight = true), StopSpec("A", board = true), StopSpec("Terminal")),
    )

    @Test fun bfsMatchesIndependentScanOnNamedFixtures() {
        fixtures.forEach { (fixtureId, spec) ->
            val catalog = syntheticCatalog(listOf(spec))
            val expected = OrderedScanBaseline(catalog).findDirectJourneys("A", "B")
            val actual = JourneyPlanner(catalog).findDirectJourneys("A", "B")
            assertEquals(fixtureId, expected, actual)
            when (fixtureId) {
                "SYN-FWD", "SYN-REPEAT-LATE" -> assertEquals(fixtureId, 1, actual.size)
                else -> assertTrue("$fixtureId should have no match", actual.isEmpty())
            }
        }
    }

    @Test fun sharedStopsAndBranchesNeverCreateTransfers() {
        val shared = syntheticCatalog(listOf(
            patternSpec("first", "First", StopSpec("A", board = true), StopSpec("Hub")),
            patternSpec("second", "Second", StopSpec("Hub"), StopSpec("B", alight = true)),
        ))
        val branches = syntheticCatalog(listOf(
            patternSpec("branch-a", "Branch A", StopSpec("A", board = true), StopSpec("Split")),
            patternSpec("branch-b", "Branch B", StopSpec("Split"), StopSpec("B", alight = true)),
        ))
        listOf("SYN-SHARED" to shared, "SYN-BRANCH" to branches).forEach { (id, catalog) ->
            assertTrue(id, JourneyPlanner(catalog).findDirectJourneys("A", "B").isEmpty())
            assertEquals(id, OrderedScanBaseline(catalog).findDirectJourneys("A", "B"), JourneyPlanner(catalog).findDirectJourneys("A", "B"))
        }
    }

    @Test fun returnsEveryEligiblePatternInStableOrderAndKeepsOccurrences() {
        val catalog = syntheticCatalog(listOf(
            patternSpec("z-route", "Zulu", StopSpec("A", board = true), StopSpec("B", alight = true)),
            patternSpec("a-route", "Alpha", StopSpec("A", board = true), StopSpec("X"), StopSpec("B", alight = true)),
        ))
        val matches = JourneyPlanner(catalog).findDirectJourneys("A", "B")
        assertEquals(listOf("a-route", "z-route"), matches.map { it.patternId })
        assertEquals(listOf(3, 2), matches.map { it.alighting.sequence })
        assertEquals(OrderedScanBaseline(catalog).findDirectJourneys("A", "B"), matches)
    }

    @Test fun synMultiPatternMatchesTheIndependentOrderedBaseline() {
        val catalog = syntheticCatalog(listOf(
            patternSpec("z-route", "Zulu", StopSpec("A", board = true), StopSpec("B", alight = true)),
            patternSpec("a-route", "Alpha", StopSpec("A", board = true), StopSpec("X"), StopSpec("B", alight = true)),
        ))
        val expected = OrderedScanBaseline(catalog).findDirectJourneys("A", "B")
        val actual = JourneyPlanner(catalog).findDirectJourneys("A", "B")
        assertEquals("SYN-MULTI-PATTERN", expected, actual)
        assertEquals(listOf("a-route", "z-route"), actual.map { it.patternId })
    }

    @Test fun identicalEndpointsNeverProduceAride() {
        val catalog = syntheticCatalog(listOf(
            patternSpec("same", "Same", StopSpec("A", board = true, alight = true), StopSpec("B")),
        ))
        assertTrue(JourneyPlanner(catalog).findDirectJourneys("A", "A").isEmpty())
    }

    @Test fun unverifiedAndInactivePatternsNeverRecommendAride() {
        val verified = patternSpec("verified", "Verified", StopSpec("A", board = true), StopSpec("B", alight = true))
        val inactive = verified.copy(id = "inactive", routeName = "Inactive", active = false)
        val unverified = verified.copy(id = "unverified", routeName = "Unverified", verification = VerificationStatus.UNVERIFIED)
        val catalog = syntheticCatalog(listOf(verified, inactive, unverified))
        assertEquals(listOf("verified"), JourneyPlanner(catalog).findDirectJourneys("A", "B").map { it.patternId })
    }

    @Test fun catalogValidatorAcceptsSyntheticFixtureAndRejectsBrokenGeometryAndCoordinates() {
        val valid = syntheticCatalog(listOf(patternSpec("valid", "Valid", StopSpec("A", board = true), StopSpec("B", alight = true))))
        assertTrue(RouteCatalogValidator.validate(valid).toString(), RouteCatalogValidator.validate(valid).isEmpty())
        val brokenAnchor = valid.copy(geometry = valid.geometry.dropLast(1))
        assertTrue(RouteCatalogValidator.validate(brokenAnchor).any { it.contains("geometry") })
        val badLocation = valid.copy(locations = valid.locations.map { it.copy(latitude = Double.NaN) })
        assertTrue(RouteCatalogValidator.validate(badLocation).any { it.contains("coordinates") })
        val brokenRef = valid.copy(patterns = valid.patterns.map { it.copy(routeId = "missing") })
        assertTrue(RouteCatalogValidator.validate(brokenRef).any { it.contains("missing route") })
    }

    @Test fun catalogValidatorRejectsDuplicateIdsBadSequencesIncompletePatternsAndProvenance() {
        val valid = syntheticCatalog(listOf(patternSpec("valid", "Valid", StopSpec("A", board = true), StopSpec("B", alight = true))))
        assertTrue(RouteCatalogValidator.validate(valid.copy(routes = valid.routes + valid.routes.first())).any { it.contains("duplicate IDs") })
        assertTrue(RouteCatalogValidator.validate(valid.copy(occurrences = valid.occurrences.map { it.copy(sequence = -1) })).any { it.contains("stop ordering") })
        assertTrue(RouteCatalogValidator.validate(valid.copy(occurrences = valid.occurrences.take(1), geometry = valid.geometry.take(1))).any { it.contains("at least two") })
        assertTrue(RouteCatalogValidator.validate(valid.copy(sources = emptyList())).any { it.contains("source") })
        assertTrue(RouteCatalogValidator.validate(valid.copy(occurrences = valid.occurrences.map { it.copy(boardingAllowed = false, alightingAllowed = false) })).any { it.contains("incomplete boarding") })
    }

    @Test fun measurementRunsThePlannerAndAliasResolverAtPlannedSizes() {
        for (count in listOf(100, 1_000, 10_000)) {
            val spec = PatternSpec("bench-$count", "Benchmark", (0 until count).map { i ->
                StopSpec("L$i", board = i == 0, alight = i == count - 1)
            })
            val catalog = syntheticCatalog(listOf(spec))
            val planner = JourneyPlanner(catalog)
            repeat(5) { assertEquals(1, planner.findDirectJourneys("L0", "L${count - 1}").size) }
            val nanos = List(200) { kotlin.system.measureNanoTime { planner.findDirectJourneys("L0", "L${count - 1}") } }.sorted()
            println("BENCH planner nodes=$count median_ns=${nanos[nanos.size / 2]} p95_ns=${nanos[(nanos.size * .95).toInt()]}")
        }
        for (count in listOf(100, 1_000)) {
            val catalog = syntheticCatalog(listOf(patternSpec("alias-benchmark", "Alias benchmark", StopSpec("A", board = true), StopSpec("B", alight = true))))
                .copy(locations = (0 until count).map { i -> Location("L$i", "L$i", emptyList(), 14.0, 122.0, "test") })
            val resolver = LocationResolver(catalog)
            val query = "L${count - 1}"
            repeat(5) { assertTrue(resolver.resolve(query) is LocationResolution.Unique) }
            val nanos = List(200) { kotlin.system.measureNanoTime { resolver.resolve(query) } }.sorted()
            println("BENCH alias aliases=$count median_ns=${nanos[nanos.size / 2]} p95_ns=${nanos[(nanos.size * .95).toInt()]}")
        }
    }
}
