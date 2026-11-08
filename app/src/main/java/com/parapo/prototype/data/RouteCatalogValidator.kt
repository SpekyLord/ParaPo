package com.parapo.prototype.data

import java.time.LocalDate

object RouteCatalogValidator {
    fun validate(catalog: RouteCatalog): List<String> {
        val errors = mutableListOf<String>()
        if (catalog.info.schemaVersion != 1) errors += "Unsupported schema version"
        if (catalog.info.datasetVersion.isBlank()) errors += "Missing dataset version"
        if (runCatching { LocalDate.parse(catalog.info.publicationDate) }.isFailure) {
            errors += "Invalid publication date"
        }

        fun uniqueIds(label: String, ids: List<String>) {
            if (ids.any(String::isBlank)) errors += "$label contains a blank ID"
            if (ids.size != ids.toSet().size) errors += "$label contains duplicate IDs"
        }

        uniqueIds("Routes", catalog.routes.map(Route::id))
        uniqueIds("Patterns", catalog.patterns.map(Pattern::id))
        uniqueIds("Locations", catalog.locations.map(Location::id))
        uniqueIds("Sources", catalog.sources.map(SourceRecord::id))

        val routes = catalog.routes.associateBy(Route::id)
        val locations = catalog.locations.associateBy(Location::id)
        val patterns = catalog.patterns.associateBy(Pattern::id)
        val sources = catalog.sources.associateBy(SourceRecord::id)

        catalog.routes.forEach { route ->
            if (route.name.isBlank()) errors += "Route ${route.id} has no display name"
            if (route.aliases.any(String::isBlank)) errors += "Route ${route.id} has a blank alias"
        }
        catalog.locations.forEach { location ->
            if (location.name.isBlank() || location.type.isBlank()) errors += "Location ${location.id} is incomplete"
            if (location.aliases.any(String::isBlank)) errors += "Location ${location.id} has a blank alias"
            if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
                !location.longitude.isFinite() || location.longitude !in -180.0..180.0
            ) errors += "Location ${location.id} has invalid coordinates"
        }

        val occurrencesByPattern = catalog.occurrences.groupBy(StopOccurrence::patternId)
        val geometryByPattern = catalog.geometry.groupBy(GeometryPoint::patternId)
        val sourceRefs = catalog.sources.map(SourceRecord::id).toSet()
        if (catalog.info.sourceRefs.isEmpty() || catalog.info.sourceRefs.any { it !in sourceRefs }) {
            errors += "Dataset source references are incomplete"
        }
        if (catalog.info.sourceRefs.distinct().size != catalog.info.sourceRefs.size) {
            errors += "Dataset source references contain duplicates"
        }
        catalog.sources.forEach { source ->
            if (source.description.isBlank() || source.reference.isBlank() || source.contactRole.isBlank()) {
                errors += "Source ${source.id} is incomplete"
            }
        }

        catalog.patterns.forEach { pattern ->
            if (pattern.routeId !in routes) errors += "Pattern ${pattern.id} references a missing route"
            if (pattern.direction.isBlank() || pattern.branchHeadsign.isBlank()) {
                errors += "Pattern ${pattern.id} must state direction and branch/head sign meaning"
            }
            if (pattern.sourceRef !in sourceRefs) errors += "Pattern ${pattern.id} references a missing source"
            if (runCatching { LocalDate.parse(pattern.verificationDate) }.isFailure) {
                errors += "Pattern ${pattern.id} has an invalid verification date"
            }
            val stops = occurrencesByPattern[pattern.id].orEmpty().sortedBy(StopOccurrence::sequence)
            if (stops.size < 2) errors += "Pattern ${pattern.id} needs at least two stop occurrences"
            if (stops.none(StopOccurrence::boardingAllowed) || stops.none(StopOccurrence::alightingAllowed)) {
                errors += "Pattern ${pattern.id} has incomplete boarding or alighting coverage"
            }
            if (stops.map(StopOccurrence::sequence).distinct().size != stops.size ||
                stops.any { it.sequence < 0 } || stops.zipWithNext().any { (a, b) -> a.sequence >= b.sequence }
            ) errors += "Pattern ${pattern.id} has invalid stop ordering"
            stops.forEach { if (it.locationId !in locations) errors += "Pattern ${pattern.id} references a missing location" }

            val points = geometryByPattern[pattern.id].orEmpty().sortedBy(GeometryPoint::sequence)
            if (points.size < 2 || points.zipWithNext().any { (a, b) -> a.sequence >= b.sequence }) {
                errors += "Pattern ${pattern.id} has invalid geometry ordering"
            }
            points.forEach {
                if (!it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                    !it.longitude.isFinite() || it.longitude !in -180.0..180.0
                ) errors += "Pattern ${pattern.id} has invalid geometry coordinates"
            }
            val anchors = points.mapNotNull(GeometryPoint::stopOccurrenceSequence)
            if (anchors.toSet().size != anchors.size || anchors != stops.map(StopOccurrence::sequence)) {
                errors += "Pattern ${pattern.id} geometry does not anchor every stop in order"
            }
        }
        if (catalog.occurrences.any { it.patternId !in patterns }) errors += "Stop occurrence references a missing pattern"
        if (catalog.geometry.any { it.patternId !in patterns }) errors += "Geometry references a missing pattern"
        return errors.distinct()
    }
}
