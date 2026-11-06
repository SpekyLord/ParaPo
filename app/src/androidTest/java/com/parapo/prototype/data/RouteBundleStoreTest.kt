package com.parapo.prototype.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class RouteBundleStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clearPriorCatalog() {
        listOf("routes.sqlite", "routes.sqlite.backup", "routes.sqlite.pending").forEach {
            File(context.filesDir, it).delete()
        }
    }

    @Test fun validBundleLoadsAndBadOrInterruptedUpdatePreservesLastCatalogAfterRestart() {
        val store = RouteBundleStore(context)
        assertEquals(BundleReplaceResult.Installed, store.replaceBundle(bundle("test-1")))
        assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)

        assertTrue(store.replaceBundle(ByteArrayInputStream("not a zip".toByteArray())) is BundleReplaceResult.Rejected)
        assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)

        val interrupted = object : InputStream() {
            private var sent = false
            override fun read(): Int {
                if (!sent) { sent = true; return 80 }
                throw IOException("synthetic interrupted stream")
            }
        }
        assertTrue(store.replaceBundle(interrupted) is BundleReplaceResult.Rejected)
        assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)
    }

    @Test fun checksumAndMalformedSqliteAreRejectedWithoutReplacingCatalog() {
        val store = RouteBundleStore(context)
        assertEquals(BundleReplaceResult.Installed, store.replaceBundle(bundle("test-1")))
        assertTrue(store.replaceBundle(bundle("bad-hash", wrongHash = true)) is BundleReplaceResult.Rejected)
        assertTrue(store.replaceBundle(bundle("bad-db", databaseBytes = "not sqlite".toByteArray())) is BundleReplaceResult.Rejected)
        assertTrue(store.replaceBundle(bundle("duplicate-source", duplicateSources = true)) is BundleReplaceResult.Rejected)
        assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)
    }

    @Test fun invalidReferencesCoordinatesSequencesPatternsGeometryAndProvenanceAreRejected() {
        val store = RouteBundleStore(context)
        assertEquals(BundleReplaceResult.Installed, store.replaceBundle(bundle("test-1")))
        val invalidMutations = listOf(
            listOf("UPDATE patterns SET route_id='MISSING' WHERE id='SYN-PATTERN'"),
            listOf("UPDATE locations SET latitude=91 WHERE id='SYN-A'"),
            listOf("UPDATE locations SET latitude='not-a-coordinate' WHERE id='SYN-A'"),
            listOf(
                "UPDATE stop_occurrences SET sequence=-1 WHERE sequence=1",
                "UPDATE geometry_points SET stop_occurrence_sequence=-1 WHERE sequence=1",
            ),
            listOf("DELETE FROM stop_occurrences WHERE sequence=2", "DELETE FROM geometry_points WHERE sequence=2"),
            listOf("UPDATE geometry_points SET stop_occurrence_sequence=NULL WHERE sequence=1"),
            listOf("UPDATE source_records SET reference='' WHERE id='SYN-SOURCE'"),
        )
        invalidMutations.forEach { mutation ->
            assertTrue("Expected rejection for $mutation", store.replaceBundle(bundle("invalid", mutation = mutation)) is BundleReplaceResult.Rejected)
            assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)
        }
    }

    @Test fun startupRecoversLastValidDatabaseIfCurrentFileIsDamaged() {
        val store = RouteBundleStore(context)
        assertEquals(BundleReplaceResult.Installed, store.replaceBundle(bundle("test-1")))
        assertEquals(BundleReplaceResult.Installed, store.replaceBundle(bundle("test-2")))
        File(context.filesDir, "routes.sqlite").writeText("damaged")
        assertEquals("test-1", RouteBundleStore(context).load()?.info?.datasetVersion)
    }

    private fun bundle(
        version: String,
        wrongHash: Boolean = false,
        databaseBytes: ByteArray? = null,
        mutation: List<String> = emptyList(),
        duplicateSources: Boolean = false,
    ): InputStream {
        val sqlite = databaseBytes ?: validDatabase(version, mutation)
        val digest = sha256(sqlite)
        val sources = JSONArray().put("SYN-SOURCE").apply { if (duplicateSources) put("SYN-SOURCE") }
        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("datasetVersion", version)
            .put("publicationDate", "2026-11-06")
            .put("sources", sources)
            .put("sqliteSha256", if (wrongHash) "0".repeat(64) else digest)
            .toString().toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            zip.putNextEntry(ZipEntry("routes.sqlite")); zip.write(sqlite); zip.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun validDatabase(version: String, mutation: List<String> = emptyList()): ByteArray {
        val file = File(context.cacheDir, "${System.nanoTime()}.sqlite")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            RouteDatabaseSchema.create(db)
            db.execSQL("PRAGMA foreign_keys=ON")
            db.insertOrThrow("dataset_info", null, ContentValues().apply {
                put("id", 1); put("schema_version", 1); put("dataset_version", version)
                put("publication_date", "2026-11-06"); put("source_refs_json", "[\"SYN-SOURCE\"]")
            })
            db.insertOrThrow("source_records", null, ContentValues().apply {
                put("id", "SYN-SOURCE"); put("description", "Test fixture only"); put("reference", "test-only"); put("contact_role", "test fixture")
            })
            db.insertOrThrow("routes", null, ContentValues().apply { put("id", "SYN-ROUTE"); put("name", "Synthetic route") })
            db.insertOrThrow("route_aliases", null, ContentValues().apply { put("alias", "SYN"); put("route_id", "SYN-ROUTE") })
            db.insertOrThrow("locations", null, location("SYN-A", 14.0, 122.0))
            db.insertOrThrow("locations", null, location("SYN-B", 14.1, 122.1))
            db.insertOrThrow("location_aliases", null, ContentValues().apply { put("alias", "A"); put("location_id", "SYN-A") })
            db.insertOrThrow("patterns", null, ContentValues().apply {
                put("id", "SYN-PATTERN"); put("route_id", "SYN-ROUTE"); put("direction", "Northbound")
                put("branch_headsign", "Synthetic headsign"); put("active", 1); put("verification_status", "VERIFIED")
                put("verification_date", "2026-11-06"); put("source_ref", "SYN-SOURCE")
            })
            db.insertOrThrow("stop_occurrences", null, occurrence(1, "SYN-A", true, false))
            db.insertOrThrow("stop_occurrences", null, occurrence(2, "SYN-B", false, true))
            db.insertOrThrow("geometry_points", null, geometry(1, 14.0, 122.0, 1))
            db.insertOrThrow("geometry_points", null, geometry(2, 14.1, 122.1, 2))
            if (mutation.isNotEmpty()) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                mutation.forEach { db.execSQL(it) }
            }
        } finally {
            db.close()
        }
        val bytes = file.readBytes()
        file.delete()
        return bytes
    }

    private fun location(id: String, lat: Double, lon: Double) = ContentValues().apply {
        put("id", id); put("name", id); put("latitude", lat); put("longitude", lon); put("type", "landmark")
    }

    private fun occurrence(sequence: Int, location: String, board: Boolean, alight: Boolean) = ContentValues().apply {
        put("pattern_id", "SYN-PATTERN"); put("sequence", sequence); put("location_id", location)
        put("boarding_allowed", if (board) 1 else 0); put("alighting_allowed", if (alight) 1 else 0)
    }

    private fun geometry(sequence: Int, lat: Double, lon: Double, anchor: Int) = ContentValues().apply {
        put("pattern_id", "SYN-PATTERN"); put("sequence", sequence); put("latitude", lat); put("longitude", lon); put("stop_occurrence_sequence", anchor)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
