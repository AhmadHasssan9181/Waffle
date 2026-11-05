package com.noobdev.waffle.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlertEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 added the free-form `details` map. Older installs can have a v1 table, so add
         * the column in place instead of destroying the cached alert history. The migration is
         * defensive because some v1/v2 builds were shipped from the same database version.
         */
        private val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        val columns = mutableSetOf<String>()
                        db.query("PRAGMA table_info(`disaster_alerts`)").use { cursor ->
                            val nameIndex = cursor.getColumnIndex("name")
                            if (nameIndex >= 0) {
                                while (cursor.moveToNext()) {
                                    columns += cursor.getString(nameIndex)
                                }
                            }
                        }
                        if (columns.isEmpty()) {
                            db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `disaster_alerts` (
                                        `id` TEXT NOT NULL,
                                        `title` TEXT,
                                        `location_name` TEXT,
                                        `type` TEXT,
                                        `status` TEXT,
                                        `timestamp` TEXT,
                                        `latitude` REAL,
                                        `longitude` REAL,
                                        `details` TEXT,
                                        PRIMARY KEY(`id`)
                                    )
                                    """.trimIndent()
                            )
                        } else if ("details" !in columns) {
                            db.execSQL(
                                    "ALTER TABLE `disaster_alerts` ADD COLUMN `details` TEXT"
                            )
                        }
                    }
                }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "waffle_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

