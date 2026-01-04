package dev.matsyshyn.lab4.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StabilityRecord::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stabilityDao(): StabilityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Міграція з версії 2 до 3: додавання нових полів для метаданих та синхронізації
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Додаємо нові колонки для метаданих пристрою
                database.execSQL("ALTER TABLE stability_records ADD COLUMN deviceId TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN deviceName TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN deviceModel TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN manufacturer TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN osVersion TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN osSdkInt INTEGER")
                
                // Додаємо колонки для синхронізації
                database.execSQL("ALTER TABLE stability_records ADD COLUMN syncedToCloud INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN cloudId TEXT")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN syncedAt INTEGER")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN userId TEXT")
                
                // Додаємо колонки для обробки дублікатів
                database.execSQL("ALTER TABLE stability_records ADD COLUMN isDuplicate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE stability_records ADD COLUMN duplicateGroupId TEXT")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stability_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}



