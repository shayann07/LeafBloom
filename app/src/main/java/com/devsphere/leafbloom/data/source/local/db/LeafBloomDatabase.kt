package com.devsphere.leafbloom.data.source.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WeatherEntity::class, ScanHistoryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class LeafBloomDatabase : RoomDatabase() {

    abstract fun weatherDao(): WeatherDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        private const val DB_NAME = "leafbloom.db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_history ADD COLUMN identifyResponseJson TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: LeafBloomDatabase? = null

        fun getInstance(context: Context): LeafBloomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LeafBloomDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}

