package dev.matsyshyn.smartparkingsystem.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.matsyshyn.smartparkingsystem.data.model.AutomationRule
import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.DirectionPanelsState
import dev.matsyshyn.smartparkingsystem.data.model.HeatingState
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import dev.matsyshyn.smartparkingsystem.data.model.VentilationState

@Database(
    entities = [
        SensorData::class,
        DeviceState::class,
        AutomationRule::class,
        DirectionPanelsState::class,
        VentilationState::class,
        HeatingState::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorDataDao(): SensorDataDao
    abstract fun deviceStateDao(): DeviceStateDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun directionPanelsDao(): DirectionPanelsDao
    abstract fun ventilationDao(): VentilationDao
    abstract fun heatingDao(): HeatingDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_parking_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

