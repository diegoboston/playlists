package com.playlists.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.playlists.app.util.SongTitleMigration
import com.playlists.app.util.StageManagerStorage

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN deletedAt INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playlists ADD COLUMN colorArgb INTEGER")
                db.execSQL(
                    """
                    UPDATE playlists SET sortOrder = (
                        SELECT COUNT(*) FROM playlists AS p2
                        WHERE p2.createdAt > playlists.createdAt
                           OR (p2.createdAt = playlists.createdAt AND p2.id > playlists.id)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE songs SET sortOrder = (
                        SELECT COUNT(*) FROM songs AS s2
                        WHERE s2.createdAt > songs.createdAt
                           OR (s2.createdAt = songs.createdAt AND s2.id > songs.id)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lastViewedAt INTEGER")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE songs ADD COLUMN isPlaceholder INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("SELECT id, title, keySignature, notes FROM songs").use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow("id")
                    val titleIdx = cursor.getColumnIndexOrThrow("title")
                    val keyIdx = cursor.getColumnIndexOrThrow("keySignature")
                    val notesIdx = cursor.getColumnIndexOrThrow("notes")
                    while (cursor.moveToNext()) {
                        val parsed = SongTitleMigration.parse(
                            title = cursor.getString(titleIdx).orEmpty(),
                            existingKey = cursor.getString(keyIdx).orEmpty(),
                            existingNotes = cursor.getString(notesIdx).orEmpty(),
                        )
                        db.execSQL(
                            "UPDATE songs SET title = ?, keySignature = ?, notes = ? WHERE id = ?",
                            arrayOf(
                                parsed.title,
                                parsed.keySignature,
                                parsed.notes,
                                cursor.getLong(idIdx),
                            ),
                        )
                    }
                }
            }
        }

        fun databasePath(context: Context): String {
            return if (StageManagerStorage.hasAccess(context)) {
                StageManagerStorage.dbFile().absolutePath
            } else {
                "playlists.db"
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    databasePath(context),
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                    )
                    .build().also { instance = it }
            }
        }
    }
}
