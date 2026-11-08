package com.parapo.prototype.data

import android.database.sqlite.SQLiteDatabase

object RouteDatabaseSchema {
    const val VERSION = 1

    fun create(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("""CREATE TABLE dataset_info (
            id INTEGER PRIMARY KEY CHECK(id = 1), schema_version INTEGER NOT NULL,
            dataset_version TEXT NOT NULL, publication_date TEXT NOT NULL, source_refs_json TEXT NOT NULL
        )""")
        db.execSQL("CREATE TABLE source_records (id TEXT PRIMARY KEY, description TEXT NOT NULL, reference TEXT NOT NULL, contact_role TEXT NOT NULL)")
        db.execSQL("CREATE TABLE routes (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE route_aliases (alias TEXT NOT NULL, route_id TEXT NOT NULL REFERENCES routes(id), PRIMARY KEY(alias, route_id))")
        db.execSQL("""CREATE TABLE locations (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, type TEXT NOT NULL
        )""")
        db.execSQL("CREATE TABLE location_aliases (alias TEXT NOT NULL, location_id TEXT NOT NULL REFERENCES locations(id), PRIMARY KEY(alias, location_id))")
        db.execSQL("""CREATE TABLE patterns (
            id TEXT PRIMARY KEY, route_id TEXT NOT NULL REFERENCES routes(id), direction TEXT NOT NULL,
            branch_headsign TEXT NOT NULL, active INTEGER NOT NULL CHECK(active IN (0, 1)), verification_status TEXT NOT NULL,
            verification_date TEXT NOT NULL, source_ref TEXT NOT NULL REFERENCES source_records(id)
        )""")
        db.execSQL("""CREATE TABLE stop_occurrences (
            pattern_id TEXT NOT NULL REFERENCES patterns(id), sequence INTEGER NOT NULL, location_id TEXT NOT NULL REFERENCES locations(id),
            boarding_allowed INTEGER NOT NULL CHECK(boarding_allowed IN (0, 1)),
            alighting_allowed INTEGER NOT NULL CHECK(alighting_allowed IN (0, 1)), PRIMARY KEY(pattern_id, sequence)
        )""")
        db.execSQL("""CREATE TABLE geometry_points (
            pattern_id TEXT NOT NULL REFERENCES patterns(id), sequence INTEGER NOT NULL, latitude REAL NOT NULL,
            longitude REAL NOT NULL, stop_occurrence_sequence INTEGER, PRIMARY KEY(pattern_id, sequence),
            FOREIGN KEY(pattern_id, stop_occurrence_sequence) REFERENCES stop_occurrences(pattern_id, sequence)
        )""")
        db.version = VERSION
    }
}
