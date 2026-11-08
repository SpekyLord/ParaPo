package com.parapo.prototype.routing

import com.parapo.prototype.patternSpec
import com.parapo.prototype.syntheticCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationResolverTest {
    @Test fun reportsMissingUnsupportedUniqueAndAmbiguousInputs() {
        val catalog = syntheticCatalog(
            listOf(patternSpec("one", "One", com.parapo.prototype.StopSpec("A", true), com.parapo.prototype.StopSpec("B", alight = true))),
            aliases = mapOf("A" to listOf("Shared name"), "B" to listOf("Shared name")),
        )
        val resolver = LocationResolver(catalog)
        assertEquals(LocationResolution.Missing, resolver.resolve("  "))
        assertTrue(resolver.resolve("unknown") is LocationResolution.Unsupported)
        assertTrue(resolver.resolve("A") is LocationResolution.Unique)
        assertEquals(2, (resolver.resolve("Shared name") as LocationResolution.Ambiguous).candidates.size)
    }

    @Test fun identicalAliasOnOneLocationRemainsUnique() {
        val catalog = syntheticCatalog(
            listOf(patternSpec("one", "One", com.parapo.prototype.StopSpec("A", true), com.parapo.prototype.StopSpec("B", alight = true))),
            aliases = mapOf("A" to listOf("A")),
        )
        assertTrue(LocationResolver(catalog).resolve("A") is LocationResolution.Unique)
    }
}
