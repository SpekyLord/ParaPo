package com.parapo.prototype.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream

data class RouteBundleManifest(
    val schemaVersion: Int,
    val datasetVersion: String,
    val publicationDate: String,
    val sourceRefs: List<String>,
    val sqliteSha256: String,
)

sealed interface BundleReplaceResult {
    data object Installed : BundleReplaceResult
    data object Unchanged : BundleReplaceResult
    data class Rejected(val reason: String) : BundleReplaceResult
}

class RouteBundleStore(private val context: Context) {
    private val database = File(context.filesDir, "routes.sqlite")
    private val backup = File(context.filesDir, "routes.sqlite.backup")
    private val pending = File(context.filesDir, "routes.sqlite.pending")

    @Synchronized
    fun load(): RouteCatalog? {
        recover()
        val bundled = runCatching { context.assets.open("routes.bundle") }.getOrNull()
        if (bundled != null) replaceBundle(bundled)
        if (!database.exists()) return null
        return runCatching { readValidated(database) }.getOrNull()
    }

    @Synchronized
    fun replaceBundle(input: InputStream): BundleReplaceResult = try {
        recover()
        val archive = input.use { readBounded(it, MAX_BUNDLE_BYTES) }
        val entries = readArchive(archive)
        val manifest = parseManifest(entries.getValue("manifest.json"))
        val sqlite = entries.getValue("routes.sqlite")
        require(sha256(sqlite).equals(manifest.sqliteSha256, ignoreCase = true)) { "SQLite checksum mismatch" }
        FileOutputStream(pending).use { stream -> stream.write(sqlite); stream.fd.sync() }
        val catalog = readValidated(pending)
        require(catalog.info.schemaVersion == manifest.schemaVersion) { "Schema version does not match manifest" }
        require(catalog.info.datasetVersion == manifest.datasetVersion) { "Dataset version does not match manifest" }
        require(catalog.info.publicationDate == manifest.publicationDate) { "Publication date does not match manifest" }
        require(catalog.info.sourceRefs.toSet() == manifest.sourceRefs.toSet()) { "Source references do not match manifest" }
        val unchanged = if (database.exists()) {
            val current = readValidated(database)
            val dateOrder = LocalDate.parse(manifest.publicationDate).compareTo(LocalDate.parse(current.info.publicationDate))
            require(dateOrder >= 0) { "Route data is older than the installed dataset" }
            if (dateOrder == 0) {
                require(manifest.datasetVersion == current.info.datasetVersion) { "Different route data cannot reuse the installed publication date" }
                require(sha256(database.readBytes()).equals(manifest.sqliteSha256, ignoreCase = true)) {
                    "Installed dataset version has different route data"
                }
                true
            } else false
        } else false
        if (unchanged) {
            pending.delete()
            BundleReplaceResult.Unchanged
        } else {
            if (database.exists()) Files.copy(database.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(pending.toPath(), database.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            BundleReplaceResult.Installed
        }
    } catch (error: Exception) {
        pending.delete()
        BundleReplaceResult.Rejected(error.message ?: "Invalid route bundle")
    }

    @Synchronized
    private fun recover() {
        pending.delete()
        if (database.exists() && runCatching { readValidated(database) }.isSuccess) return
        if (backup.exists() && runCatching { readValidated(backup) }.isSuccess) {
            Files.copy(backup.toPath(), database.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readValidated(file: File): RouteCatalog {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            check(db.version == RouteDatabaseSchema.VERSION) { "Unsupported SQLite schema version" }
            check(db.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
                "SQLite integrity check failed"
            }
            check(!db.rawQuery("PRAGMA foreign_key_check", null).use { it.moveToFirst() }) { "SQLite foreign-key check failed" }
            return RouteDatabaseReader.read(db).also { catalog ->
                val errors = RouteCatalogValidator.validate(catalog)
                require(errors.isEmpty()) { errors.joinToString("; ") }
            }
        } finally {
            db.close()
        }
    }

    private fun readArchive(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in ALLOWED_ENTRIES) { "Unexpected route bundle entry" }
                require(entry.name !in entries) { "Duplicate route bundle entry" }
                entries[entry.name] = readBounded(zip, MAX_BUNDLE_BYTES)
                zip.closeEntry()
            }
        }
        require(entries.keys == ALLOWED_ENTRIES) { "Route bundle must contain manifest.json and routes.sqlite" }
        return entries
    }

    private fun parseManifest(bytes: ByteArray): RouteBundleManifest {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val date = json.getString("publicationDate")
        LocalDate.parse(date)
        val sources = json.getJSONArray("sources")
        val refs = List(sources.length()) { sources.getString(it) }
        require(refs.isNotEmpty() && refs.all(String::isNotBlank)) { "Manifest sources are incomplete" }
        require(refs.distinct().size == refs.size) { "Manifest source references contain duplicates" }
        return RouteBundleManifest(
            json.getInt("schemaVersion"), json.getString("datasetVersion"), date, refs,
            json.getString("sqliteSha256").lowercase().also { require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid SQLite checksum" } },
        ).also {
            require(it.schemaVersion == RouteDatabaseSchema.VERSION) { "Unsupported schema version" }
            require(it.datasetVersion.isNotBlank()) { "Missing dataset version" }
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Route bundle exceeds the size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        // shortcut: 32 MiB is ample for 3–5 patterns; raise with measured dataset growth.
        private const val MAX_BUNDLE_BYTES = 32 * 1024 * 1024
        private val ALLOWED_ENTRIES = setOf("manifest.json", "routes.sqlite")
    }
}
