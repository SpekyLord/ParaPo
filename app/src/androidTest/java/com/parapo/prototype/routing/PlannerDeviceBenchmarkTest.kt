package com.parapo.prototype.routing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parapo.prototype.data.DatasetInfo
import com.parapo.prototype.data.GeometryPoint
import com.parapo.prototype.data.Location
import com.parapo.prototype.data.Pattern
import com.parapo.prototype.data.Route
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.SourceRecord
import com.parapo.prototype.data.StopOccurrence
import com.parapo.prototype.data.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerDeviceBenchmarkTest {
    @Test fun measuresPlannerAndAliasLookupAtPlannedSizes() {
        for (count in listOf(100, 1_000, 10_000)) {
            val catalog = syntheticCatalog(count)
            val planner = JourneyPlanner(catalog)
            repeat(5) { assertEquals(1, planner.findDirectJourneys("L0", "L${count - 1}").size) }
            val nanos = List(200) {
                kotlin.system.measureNanoTime { planner.findDirectJourneys("L0", "L${count - 1}") }
            }.sorted()
            assertEquals(1, planner.findDirectJourneys("L0", "L${count - 1}").size)
            println("BENCH_DEVICE planner nodes=$count median_ns=${nanos[100]} p95_ns=${nanos[190]}")
        }

        for (count in listOf(100, 1_000)) {
            val resolver = LocationResolver(syntheticCatalog(count))
            repeat(5) { assertEquals(LocationResolution.Unique::class, resolver.resolve("L${count - 1}")::class) }
            val nanos = List(200) {
                kotlin.system.measureNanoTime { resolver.resolve("L${count - 1}") }
            }.sorted()
            assertEquals(LocationResolution.Unique::class, resolver.resolve("L${count - 1}")::class)
            println("BENCH_DEVICE alias aliases=$count median_ns=${nanos[100]} p95_ns=${nanos[190]}")
        }
    }

    private fun syntheticCatalog(count: Int): RouteCatalog {
        val locations = List(count) { index ->
            Location("L$index", "L$index", emptyList(), 14.0 + index * 0.000001, 122.0 + index * 0.000001, "landmark")
        }
        val route = Route("SYN-BENCH-ROUTE", "Synthetic benchmark", emptyList())
        val pattern = Pattern("SYN-BENCH-PATTERN", route.id, "Northbound", "Test only", true, VerificationStatus.VERIFIED, "2026-11-06", "SYN-BENCH-SOURCE")
        val source = SourceRecord("SYN-BENCH-SOURCE", "Synthetic benchmark only", "test-only", "test fixture")
        val occurrences = locations.mapIndexed { index, location ->
            StopOccurrence(pattern.id, index + 1, location.id, index == 0, index == count - 1)
        }
        val geometry = locations.mapIndexed { index, location ->
            GeometryPoint(pattern.id, index + 1, location.latitude, location.longitude, index + 1)
        }
        return RouteCatalog(
            DatasetInfo(1, "synthetic-test-only", "2026-11-06", listOf(source.id)),
            listOf(route), listOf(pattern), locations, occurrences, geometry, listOf(source),
        )
    }
}
