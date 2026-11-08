package com.parapo.prototype.scanner

import com.parapo.prototype.StopSpec
import com.parapo.prototype.data.Pattern
import com.parapo.prototype.data.Route
import com.parapo.prototype.data.VerificationStatus
import com.parapo.prototype.patternSpec
import com.parapo.prototype.routing.JourneyPlanner
import com.parapo.prototype.syntheticCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerDecisionsTest {
    private val catalog = run {
        val base = syntheticCatalog(listOf(
            patternSpec("r3-to-wvsu", "Route 3", StopSpec("A", board = true), StopSpec("B", alight = true)),
            patternSpec("r3-to-jaro", "Route 3", StopSpec("B", board = true), StopSpec("A", alight = true)),
            patternSpec("r10-to-wvsu", "Route 10", StopSpec("A", board = true), StopSpec("B", alight = true)),
            patternSpec("r10-to-jaro", "Route 10", StopSpec("B", board = true), StopSpec("A", alight = true)),
        ))
        base.copy(
            routes = listOf(Route("R3", "Route 3", listOf("Route 3", "R3")), Route("R10", "Route 10", listOf("Route 10", "R10"))),
            patterns = listOf(
                Pattern("r3-to-wvsu", "R3", "Jaro to WVSU", "Toward WVSU", true, VerificationStatus.VERIFIED, "2026-11-06", "synthetic-source"),
                Pattern("r3-to-jaro", "R3", "WVSU to Jaro", "Toward Jaro Plaza", true, VerificationStatus.VERIFIED, "2026-11-06", "synthetic-source"),
                Pattern("r10-to-wvsu", "R10", "Jaro to WVSU", "Toward City Proper", true, VerificationStatus.VERIFIED, "2026-11-06", "synthetic-source"),
                Pattern("r10-to-jaro", "R10", "WVSU to Jaro", "Toward Tagbak", true, VerificationStatus.VERIFIED, "2026-11-06", "synthetic-source"),
            ),
        )
    }

    @Test fun routeCodesAndAliasesUseWholeBoundariesAcrossCatalog() {
        assertEquals(RouteTextResolution.Found("R3"), ScannerDecisions.routeFromText("Route 3 toward WVSU", catalog))
        assertEquals(RouteTextResolution.Found("R10"), ScannerDecisions.routeFromText("R10 / Toward City Proper", catalog))
        assertEquals(RouteTextResolution.Ambiguous(setOf("R3", "R10")), ScannerDecisions.routeFromText("Route 3 and Route 10", catalog))
        assertEquals(RouteTextResolution.Unsupported, ScannerDecisions.routeFromText("Route 30", catalog))
        assertEquals(RouteTextResolution.Unsupported, ScannerDecisions.routeFromText("Seat 123", catalog))
        assertEquals(RouteTextResolution.Unreadable, ScannerDecisions.routeFromText("No readable route", catalog))
    }

    @Test fun directionAndJourneyEvidenceStaySeparateAndAnyEligibleRouteCanMatch() {
        val trips = JourneyPlanner(catalog).findDirectJourneys("A", "B")
        val route10 = ScannerDecisions.routeFromText("Route 10 Toward City Proper", catalog)
        val routeId = (route10 as RouteTextResolution.Found).routeId
        val direction = ScannerDecisions.patternFromHeadsign(routeId, "Route 10 Toward City Proper", catalog)
        assertEquals(setOf("r10-to-wvsu"), direction)
        val match = ScannerDecisions.decide(route10, direction, null, catalog, trips, neutral = false)
        assertEquals(DecisionKind.MATCH, match.kind)
        assertEquals("r10-to-wvsu", match.patternId)
        assertEquals(DirectionSource.SIGN, match.directionSource)

        val unresolved = ScannerDecisions.decide(
            RouteTextResolution.Found("R3"), emptySet(), null, catalog, trips, neutral = false,
        )
        assertEquals(DecisionKind.CONFIRM_DIRECTION, unresolved.kind)
        assertEquals(DecisionKind.NO_MATCH, ScannerDecisions.decide(
            route10, direction, null, catalog, trips.filterNot { it.routeId == "R10" }, neutral = false,
        ).kind)
        assertEquals(DecisionKind.IDENTIFIED, ScannerDecisions.decide(
            route10, direction, null, catalog, emptyList(), neutral = true,
        ).kind)
    }

    @Test fun conflictingHeadsignOverridesUserDirectionAndUnverifiedPatternsStayGray() {
        val route = RouteTextResolution.Found("R3")
        val conflict = ScannerDecisions.decide(route, setOf("r3-to-jaro"), "r3-to-wvsu", catalog,
            JourneyPlanner(catalog).findDirectJourneys("A", "B"), neutral = false)
        assertEquals(DecisionKind.UNCERTAIN, conflict.kind)
        assertTrue(ScannerDecisions.conflictsWithConfirmation(route, setOf("r3-to-jaro"), "R3", "r3-to-wvsu", "Route 3 Toward Jaro Plaza", catalog))
        assertFalse(ScannerDecisions.conflictsWithConfirmation(RouteTextResolution.Unreadable, emptySet(), "R3", "r3-to-wvsu", "", catalog))
        assertTrue(ScannerDecisions.conflictsWithConfirmation(RouteTextResolution.Unsupported, emptySet(), "R3", "r3-to-wvsu", "Route 55", catalog))

        val unverified = catalog.copy(patterns = catalog.patterns.map {
            if (it.id == "r3-to-wvsu") it.copy(verificationStatus = VerificationStatus.UNVERIFIED) else it
        })
        val outside = ScannerDecisions.decide(route, emptySet(), null, unverified, emptyList(), neutral = true,
            observedText = "Route 3 Toward WVSU")
        assertEquals(DecisionKind.UNSUPPORTED, outside.kind)
    }

    @Test fun everyDecisionTableStateHasItsOwnResultAndIcon() {
        val journeys = JourneyPlanner(catalog).findDirectJourneys("A", "B")
        val match = ScannerDecisions.decide(RouteTextResolution.Found("R3"), setOf("r3-to-wvsu"), null, catalog, journeys, false)
        val noMatch = ScannerDecisions.decide(RouteTextResolution.Found("R3"), setOf("r3-to-wvsu"), null, catalog, emptyList(), false)
        val confirm = ScannerDecisions.decide(RouteTextResolution.Found("R3"), emptySet(), null, catalog, journeys, false)
        val unreadable = ScannerDecisions.decide(RouteTextResolution.Unreadable, emptySet(), null, catalog, journeys, false)
        val unsupported = ScannerDecisions.decide(RouteTextResolution.Unsupported, emptySet(), null, catalog, journeys, false)
        val uncertain = ScannerDecisions.decide(RouteTextResolution.Ambiguous(setOf("R3", "R10")), emptySet(), null, catalog, journeys, false)
        val identified = ScannerDecisions.decide(RouteTextResolution.Found("R3"), emptySet(), null, catalog, emptyList(), true)

        assertEquals(listOf(DecisionKind.MATCH, DecisionKind.NO_MATCH, DecisionKind.CONFIRM_DIRECTION,
            DecisionKind.UNREADABLE, DecisionKind.UNSUPPORTED, DecisionKind.UNCERTAIN, DecisionKind.IDENTIFIED),
            listOf(match.kind, noMatch.kind, confirm.kind, unreadable.kind, unsupported.kind, uncertain.kind, identified.kind))
        assertTrue(match.message.startsWith("✓"))
        assertTrue(noMatch.message.startsWith("✕"))
        assertTrue(confirm.message.startsWith("?"))
        assertTrue(unreadable.message.startsWith("?"))
        assertTrue(unsupported.message.startsWith("?"))
        assertTrue(uncertain.message.startsWith("!"))
        assertTrue(identified.message.startsWith("ℹ"))
    }

    @Test fun letterboxRotationDimensionsAndPreviewCropMapBoxesWithinBounds() {
        val upright = unletterbox(SignBox(40f, 0f, 280f, 320f), 640, 480)
        assertEquals(SignBox(80f, 0f, 560f, 480f), upright)
        val rotated = unletterbox(SignBox(-40f, 40f, 180f, 300f), 480, 640)
        assertTrue(rotated.left >= 0f && rotated.top >= 0f)
        assertTrue(rotated.right <= 480f && rotated.bottom <= 640f)
        val preview = toPreviewBox(SignBox(0f, 0f, 640f, 480f), 640, 480, 300, 300)
        assertEquals(SignBox(-50f, 0f, 350f, 300f), preview)
        assertEquals(SignBox(0f, 20f, 640f, 480f), clipBox(SignBox(-5f, 20f, 650f, 500f), 640f, 480f))
    }

    @Test fun targetAssociationDoesNotCarryObservationAcrossAmbiguousOrLostTargets() {
        val target = SingleSignTarget()
        val first = SignBox(10f, 10f, 100f, 80f)
        val id = target.select(first)
        assertEquals(SignBox(12f, 10f, 102f, 80f), target.associate(listOf(SignBox(12f, 10f, 102f, 80f))))
        assertEquals(id, target.selectedId)
        assertNull(target.associate(emptyList()))
        assertNull(target.selectedId)

        target.select(first)
        assertNull(target.associate(listOf(SignBox(10f, 10f, 100f, 80f), SignBox(11f, 10f, 101f, 80f))))
        assertNull(target.selectedId)
    }

    @Test fun decoderHandlesBothOnnxLayoutsAndSuppressesDuplicateBoxes() {
        val channelFirst = arrayOf(arrayOf(
            floatArrayOf(160f, 161f), floatArrayOf(160f, 160f), floatArrayOf(100f, 98f),
            floatArrayOf(100f, 98f), floatArrayOf(0.8f, 0.7f),
        ))
        val channelLast = arrayOf(arrayOf(
            floatArrayOf(160f, 160f, 100f, 100f, 0.8f),
            floatArrayOf(161f, 160f, 98f, 98f, 0.7f),
        ))
        val first = decodeYoloOutput(channelFirst, 320, 320)
        val last = decodeYoloOutput(channelLast, 320, 320)
        assertEquals(1, first.size)
        assertEquals(1, last.size)
        assertEquals(0.8f, first.single().score)
        assertEquals(first.single().box, last.single().box)
    }

    @Test fun oldSessionTargetAndJourneyResultsAreRejectedAndFreshnessExpiresWithoutFrames() {
        val tracker = TemporalDecisionTracker()
        val decision = ScannerDecision(DecisionKind.MATCH, "R3", "Route 3", "r3-to-wvsu", "Jaro to WVSU", DirectionSource.SIGN)
        val oldSession = tracker.beginSession("trip-A")
        tracker.selectTarget("target-1")
        assertFalse(tracker.observe(oldSession, "trip-A", "target-1", 10, decision))
        assertTrue(tracker.observe(oldSession, "trip-A", "target-1", 20, decision))
        assertEquals(decision, tracker.current(20))

        val newSession = tracker.updateContext("trip-B")
        tracker.selectTarget("target-1")
        assertFalse(tracker.acceptsCallback(oldSession, "trip-A", "target-1"))
        assertFalse(tracker.observe(oldSession, "trip-A", "target-1", 30, decision))
        assertFalse(tracker.observe(newSession, "trip-B", "target-1", 30, decision))
        assertTrue(tracker.observe(newSession, "trip-B", "target-1", 40, decision))
        assertNull(tracker.current(1_041))
    }

    @Test fun stableEvidenceRefreshesFromLatestFrameAndRequiresNewAgreementAfterExpiry() {
        val tracker = TemporalDecisionTracker()
        val decision = ScannerDecision(DecisionKind.MATCH, "R3", "Route 3", "r3-to-wvsu", "Jaro to WVSU", DirectionSource.SIGN)
        val session = tracker.beginSession("trip")
        tracker.selectTarget("target")
        assertFalse(tracker.observe(session, "trip", "target", 10, decision))
        assertTrue(tracker.observe(session, "trip", "target", 20, decision))
        assertTrue(tracker.observe(session, "trip", "target", 900, decision))
        assertEquals(decision, tracker.current(1_900))
        assertNull(tracker.current(1_901))
        assertFalse(tracker.observe(session, "trip", "target", 2_001, decision))
        assertTrue(tracker.observe(session, "trip", "target", 2_050, decision))
    }
}
