#!/usr/bin/env python3
"""Build the offline SQLite route bundle from the reviewed route source JSON."""

import hashlib
import json
import math
import os
import sqlite3
import tempfile
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data" / "iloilo-jaro-wvsu.json"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "routes.bundle"


def check_catalog(data):
    for table in ("routes", "patterns", "locations", "sources"):
        ids = [row["id"] for row in data[table]]
        assert len(ids) == len(set(ids)) and all(ids), f"duplicate or blank {table} ID"

    routes = {row["id"] for row in data["routes"]}
    locations = {row["id"] for row in data["locations"]}
    source_ids = {row["id"] for row in data["sources"]}
    assert data["sourceRefs"] and set(data["sourceRefs"]) <= source_ids
    assert len(data["sourceRefs"]) == len(set(data["sourceRefs"]))

    for location in data["locations"]:
        assert location["name"].strip() and location["type"].strip()
        assert -90 <= location["latitude"] <= 90
        assert -180 <= location["longitude"] <= 180

    for pattern in data["patterns"]:
        assert pattern["routeId"] in routes and pattern["sourceRef"] in source_ids
        assert pattern["direction"].strip() and pattern["branchHeadsign"].strip()
        assert pattern["verificationStatus"] in ("VERIFIED", "UNVERIFIED")
        assert len(pattern["stops"]) >= 2
        assert any(stop["boardingAllowed"] for stop in pattern["stops"])
        assert any(stop["alightingAllowed"] for stop in pattern["stops"])
        assert all(stop["locationId"] in locations for stop in pattern["stops"])
        assert len(pattern["geometry"]) >= 2
        anchors = [point["stopOccurrenceSequence"] for point in pattern["geometry"]
                   if point.get("stopOccurrenceSequence") is not None]
        assert anchors == list(range(1, len(pattern["stops"]) + 1))
        for point in pattern["geometry"]:
            assert math.isfinite(point["latitude"]) and -90 <= point["latitude"] <= 90
            assert math.isfinite(point["longitude"]) and -180 <= point["longitude"] <= 180

    for source in data["sources"]:
        assert source["description"].strip() and source["reference"].strip() and source["contactRole"].strip()


def create_database(path, data):
    db = sqlite3.connect(path)
    try:
        db.execute("PRAGMA foreign_keys=ON")
        db.executescript("""
            CREATE TABLE dataset_info (
                id INTEGER PRIMARY KEY CHECK(id = 1), schema_version INTEGER NOT NULL,
                dataset_version TEXT NOT NULL, publication_date TEXT NOT NULL, source_refs_json TEXT NOT NULL
            );
            CREATE TABLE source_records (
                id TEXT PRIMARY KEY, description TEXT NOT NULL, reference TEXT NOT NULL, contact_role TEXT NOT NULL
            );
            CREATE TABLE routes (id TEXT PRIMARY KEY, name TEXT NOT NULL);
            CREATE TABLE route_aliases (
                alias TEXT NOT NULL, route_id TEXT NOT NULL REFERENCES routes(id), PRIMARY KEY(alias, route_id)
            );
            CREATE TABLE locations (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, type TEXT NOT NULL
            );
            CREATE TABLE location_aliases (
                alias TEXT NOT NULL, location_id TEXT NOT NULL REFERENCES locations(id), PRIMARY KEY(alias, location_id)
            );
            CREATE TABLE patterns (
                id TEXT PRIMARY KEY, route_id TEXT NOT NULL REFERENCES routes(id), direction TEXT NOT NULL,
                branch_headsign TEXT NOT NULL, active INTEGER NOT NULL CHECK(active IN (0, 1)),
                verification_status TEXT NOT NULL, verification_date TEXT NOT NULL,
                source_ref TEXT NOT NULL REFERENCES source_records(id)
            );
            CREATE TABLE stop_occurrences (
                pattern_id TEXT NOT NULL REFERENCES patterns(id), sequence INTEGER NOT NULL,
                location_id TEXT NOT NULL REFERENCES locations(id), boarding_allowed INTEGER NOT NULL CHECK(boarding_allowed IN (0, 1)),
                alighting_allowed INTEGER NOT NULL CHECK(alighting_allowed IN (0, 1)), PRIMARY KEY(pattern_id, sequence)
            );
            CREATE TABLE geometry_points (
                pattern_id TEXT NOT NULL REFERENCES patterns(id), sequence INTEGER NOT NULL, latitude REAL NOT NULL,
                longitude REAL NOT NULL, stop_occurrence_sequence INTEGER, PRIMARY KEY(pattern_id, sequence),
                FOREIGN KEY(pattern_id, stop_occurrence_sequence) REFERENCES stop_occurrences(pattern_id, sequence)
            );
        """)
        db.execute("PRAGMA user_version=1")
        db.execute("INSERT INTO dataset_info VALUES (1, ?, ?, ?, ?)", (
            data["schemaVersion"], data["datasetVersion"], data["publicationDate"],
            json.dumps(data["sourceRefs"], separators=(",", ":")),
        ))
        db.executemany("INSERT INTO source_records VALUES (?, ?, ?, ?)", [
            (row["id"], row["description"], row["reference"], row["contactRole"])
            for row in data["sources"]
        ])
        db.executemany("INSERT INTO routes VALUES (?, ?)", [(row["id"], row["name"]) for row in data["routes"]])
        db.executemany("INSERT INTO route_aliases VALUES (?, ?)", [
            (alias, row["id"]) for row in data["routes"] for alias in row.get("aliases", [])
        ])
        db.executemany("INSERT INTO locations VALUES (?, ?, ?, ?, ?)", [
            (row["id"], row["name"], row["latitude"], row["longitude"], row["type"])
            for row in data["locations"]
        ])
        db.executemany("INSERT INTO location_aliases VALUES (?, ?)", [
            (alias, row["id"]) for row in data["locations"] for alias in row.get("aliases", [])
        ])
        db.executemany("INSERT INTO patterns VALUES (?, ?, ?, ?, ?, ?, ?, ?)", [
            (row["id"], row["routeId"], row["direction"], row["branchHeadsign"], int(row["active"]),
             row["verificationStatus"], row["verificationDate"], row["sourceRef"])
            for row in data["patterns"]
        ])
        db.executemany("INSERT INTO stop_occurrences VALUES (?, ?, ?, ?, ?)", [
            (pattern["id"], sequence, stop["locationId"], int(stop["boardingAllowed"]), int(stop["alightingAllowed"]))
            for pattern in data["patterns"] for sequence, stop in enumerate(pattern["stops"], 1)
        ])
        db.executemany("INSERT INTO geometry_points VALUES (?, ?, ?, ?, ?)", [
            (pattern["id"], sequence, point["latitude"], point["longitude"], point.get("stopOccurrenceSequence"))
            for pattern in data["patterns"] for sequence, point in enumerate(pattern["geometry"], 1)
        ])
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert not db.execute("PRAGMA foreign_key_check").fetchall()
        db.commit()
    finally:
        db.close()


def main():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    check_catalog(data)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=OUTPUT.parent) as temporary:
        temporary = Path(temporary)
        database = temporary / "routes.sqlite"
        bundle = temporary / "routes.bundle"
        create_database(database, data)
        sqlite_bytes = database.read_bytes()
        manifest = {
            "schemaVersion": data["schemaVersion"],
            "datasetVersion": data["datasetVersion"],
            "publicationDate": data["publicationDate"],
            "sources": data["sourceRefs"],
            "sqliteSha256": hashlib.sha256(sqlite_bytes).hexdigest(),
        }
        with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
            archive.writestr("routes.sqlite", sqlite_bytes)
        os.replace(bundle, OUTPUT)
    print(f"Built {OUTPUT.relative_to(ROOT)}: {data['datasetVersion']} ({len(data['patterns'])} patterns)")


if __name__ == "__main__":
    main()
