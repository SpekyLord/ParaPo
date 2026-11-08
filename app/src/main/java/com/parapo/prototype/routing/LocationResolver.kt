package com.parapo.prototype.routing

import com.parapo.prototype.data.Location
import com.parapo.prototype.data.RouteCatalog
import com.parapo.prototype.data.normalizeAlias

sealed interface LocationResolution {
    data object Missing : LocationResolution
    data class Unsupported(val query: String) : LocationResolution
    data class Unique(val location: Location) : LocationResolution
    data class Ambiguous(val candidates: List<Location>) : LocationResolution
}

class LocationResolver(catalog: RouteCatalog) {
    private val locationsByAlias: Map<String, List<Location>> = buildMap {
        catalog.locations.forEach { location ->
            (location.aliases + location.name).forEach { alias ->
                val key = normalizeAlias(alias)
                if (key.isNotEmpty()) put(key, (get(key).orEmpty() + location).distinctBy(Location::id))
            }
        }
    }

    fun resolve(query: String?): LocationResolution {
        if (query.isNullOrBlank()) return LocationResolution.Missing
        val candidates = locationsByAlias[normalizeAlias(query)].orEmpty().sortedBy(Location::name)
        return when (candidates.size) {
            0 -> LocationResolution.Unsupported(query.trim())
            1 -> LocationResolution.Unique(candidates.single())
            else -> LocationResolution.Ambiguous(candidates)
        }
    }
}
