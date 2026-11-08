package com.parapo.prototype.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

object RouteDatabaseReader {
    fun read(db: SQLiteDatabase): RouteCatalog {
        val info = db.rawQuery("SELECT schema_version, dataset_version, publication_date, source_refs_json FROM dataset_info WHERE id=1", null).use { cursor ->
            check(cursor.moveToFirst()) { "Missing dataset metadata" }
            DatasetInfo(cursor.intValue(0), cursor.getString(1), cursor.getString(2), JSONArray(cursor.getString(3)).strings())
        }
        val sources = db.rawQuery("SELECT id, description, reference, contact_role FROM source_records", null).use { c ->
            c.mapRows { SourceRecord(getString(0), getString(1), getString(2), getString(3)) }
        }
        val routes = db.rawQuery("SELECT id, name FROM routes", null).use { c ->
            val aliases = db.rawQuery("SELECT alias, route_id FROM route_aliases", null).use { a ->
                a.mapRows { getString(1) to getString(0) }.groupBy({ it.first }, { it.second })
            }
            c.mapRows { Route(getString(0), getString(1), aliases[getString(0)].orEmpty()) }
        }
        val locations = db.rawQuery("SELECT id, name, latitude, longitude, type FROM locations", null).use { c ->
            val aliases = db.rawQuery("SELECT alias, location_id FROM location_aliases", null).use { a ->
                a.mapRows { getString(1) to getString(0) }.groupBy({ it.first }, { it.second })
            }
            c.mapRows { Location(getString(0), getString(1), aliases[getString(0)].orEmpty(), numberValue(2), numberValue(3), getString(4)) }
        }
        val patterns = db.rawQuery("SELECT id, route_id, direction, branch_headsign, active, verification_status, verification_date, source_ref FROM patterns", null).use { c ->
            c.mapRows {
                Pattern(getString(0), getString(1), getString(2), getString(3), booleanValue(4),
                    VerificationStatus.valueOf(getString(5)), getString(6), getString(7))
            }
        }
        val occurrences = db.rawQuery("SELECT pattern_id, sequence, location_id, boarding_allowed, alighting_allowed FROM stop_occurrences", null).use { c ->
            c.mapRows { StopOccurrence(getString(0), intValue(1), getString(2), booleanValue(3), booleanValue(4)) }
        }
        val geometry = db.rawQuery("SELECT pattern_id, sequence, latitude, longitude, stop_occurrence_sequence FROM geometry_points", null).use { c ->
            c.mapRows {
                GeometryPoint(getString(0), intValue(1), numberValue(2), numberValue(3), if (isNull(4)) null else intValue(4))
            }
        }
        return RouteCatalog(info, routes, patterns, locations, occurrences, geometry, sources)
    }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun Cursor.intValue(index: Int): Int {
        check(getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Expected an integer database value" }
        return getInt(index)
    }

    private fun Cursor.numberValue(index: Int): Double {
        val type = getType(index)
        check(type == Cursor.FIELD_TYPE_INTEGER || type == Cursor.FIELD_TYPE_FLOAT) { "Expected a numeric database value" }
        return getDouble(index)
    }

    private fun Cursor.booleanValue(index: Int): Boolean = when (intValue(index)) {
        0 -> false
        1 -> true
        else -> error("Expected a boolean database value")
    }

    private inline fun <T> Cursor.mapRows(row: Cursor.() -> T): List<T> = buildList {
        while (moveToNext()) add(row())
    }
}
