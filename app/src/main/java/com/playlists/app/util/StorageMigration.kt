package com.playlists.app.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

object StorageMigration {
    private const val TAG = "StorageMigration"

    fun runIfNeeded(context: Context): Boolean {
        if (!StageManagerStorage.hasAccess(context)) return false
        if (!StageManagerStorage.ensureDirectories()) return false

        val marker = StageManagerStorage.migrationMarker()
        val externalDb = StageManagerStorage.dbFile()
        val internalDb = StageManagerStorage.legacyInternalDbFile(context)
        val internalSongs = StageManagerStorage.legacyInternalSongsDir(context)
        val externalSongs = StageManagerStorage.songsDir()

        if (marker.isFile && externalDb.isFile) {
            StageManagerState.exportFromSharedPreferences(context)
            return true
        }

        if (externalDb.isFile && !internalDb.isFile) {
            StageManagerState.exportFromSharedPreferences(context)
            marker.writeText("v1")
            Log.i(TAG, "Restored Stage Manager data from ${StageManagerStorage.root().absolutePath}")
            return true
        }

        return try {
            val pathMap = migrateSongFiles(internalSongs, externalSongs)
            migrateDatabase(internalDb, externalDb, pathMap, internalSongs, externalSongs)
            StageManagerState.exportFromSharedPreferences(context)
            marker.writeText("v1")
            Log.i(TAG, "Migrated Stage Manager data to ${StageManagerStorage.root().absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Storage migration failed", e)
            false
        }
    }

    fun migrateSongFiles(internalSongs: File, externalSongs: File): Map<String, String> {
        val pathMap = linkedMapOf<String, String>()
        if (!internalSongs.isDirectory) return pathMap
        externalSongs.mkdirs()
        internalSongs.listFiles()?.forEach { source ->
            if (!source.isFile) return@forEach
            val dest = uniqueDestFile(externalSongs, source.name)
            if (!dest.exists()) {
                source.copyTo(dest)
            }
            pathMap[source.absolutePath] = dest.absolutePath
        }
        return pathMap
    }

    fun remapSongPaths(
        db: SQLiteDatabase,
        pathMap: Map<String, String>,
        internalSongsRoot: String,
        externalSongsRoot: String,
    ) {
        pathMap.forEach { (oldPath, newPath) ->
            db.execSQL(
                "UPDATE songs SET filePath = ? WHERE filePath = ?",
                arrayOf(newPath, oldPath),
            )
        }
        db.rawQuery("SELECT id, filePath FROM songs", null).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val pathIdx = cursor.getColumnIndexOrThrow("filePath")
            while (cursor.moveToNext()) {
                val oldPath = cursor.getString(pathIdx).orEmpty()
                val newPath = remapPath(oldPath, internalSongsRoot, externalSongsRoot)
                if (newPath != null && newPath != oldPath) {
                    db.execSQL(
                        "UPDATE songs SET filePath = ? WHERE id = ?",
                        arrayOf(newPath, cursor.getLong(idIdx)),
                    )
                }
            }
        }
    }

    fun remapPath(oldPath: String, internalSongsRoot: String, externalSongsRoot: String): String? {
        if (oldPath.startsWith(externalSongsRoot)) return null
        if (oldPath.startsWith(internalSongsRoot)) {
            val relative = oldPath.removePrefix(internalSongsRoot).trimStart('/')
            return File(externalSongsRoot, relative).absolutePath
        }
        val name = File(oldPath).name
        if (name.isBlank()) return null
        val candidate = File(externalSongsRoot, name)
        return if (candidate.isFile) candidate.absolutePath else null
    }

    private fun migrateDatabase(
        internalDb: File,
        externalDb: File,
        pathMap: Map<String, String>,
        internalSongs: File,
        externalSongs: File,
    ) {
        when {
            internalDb.isFile && !externalDb.isFile -> internalDb.copyTo(externalDb, overwrite = false)
            internalDb.isFile && externalDb.isFile && internalDb.lastModified() > externalDb.lastModified() -> {
                internalDb.copyTo(externalDb, overwrite = true)
            }
        }
        if (!externalDb.isFile) return

        val db = SQLiteDatabase.openDatabase(
            externalDb.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            remapSongPaths(
                db,
                pathMap,
                internalSongs.absolutePath,
                externalSongs.absolutePath,
            )
        } finally {
            db.close()
        }
    }

    private fun uniqueDestFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index$ext")
            index++
        }
        return candidate
    }
}
