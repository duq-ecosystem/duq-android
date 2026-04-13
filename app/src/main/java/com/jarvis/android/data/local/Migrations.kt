package com.jarvis.android.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations.
 *
 * Each migration handles schema changes between consecutive versions.
 * Add new migrations here when the database schema changes.
 *
 * Example:
 * ```
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE messages ADD COLUMN audio_url TEXT")
 *     }
 * }
 * ```
 */
object Migrations {

    /**
     * All migrations to apply when upgrading the database.
     * Add new migrations to this list in order.
     */
    val ALL: Array<Migration> = arrayOf(
        // Add migrations here as needed:
        // MIGRATION_1_2,
        // MIGRATION_2_3,
    )

    // Example migration from version 1 to 2 (commented out until needed):
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(database: SupportSQLiteDatabase) {
    //         // Example: Add a new column to messages table
    //         database.execSQL("ALTER TABLE messages ADD COLUMN audio_url TEXT")
    //     }
    // }
}
