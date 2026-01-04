package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import java.io.Serializable

@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "timestamp")
    @get:PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    // Датчики наявності авто на місцях: масив з 100 елементів (0 = вільне, 1 = зайняте)
    // Зберігається як JSON string в БД
    @ColumnInfo(name = "parking_sensors")
    @get:PropertyName("parking_sensors")
    val parkingSensors: List<Int> = List(100) { 0 }, // 100 датчиків: [0,1,0,1,1,...]
    
    // Обчислюване поле: середній рівень зайнятості (0.0-1.0) для зручності
    @ColumnInfo(name = "parking_occupied")
    @get:PropertyName("parking_occupied")
    val parkingOccupied: Float = 0f, // Обчислюється: sum(parkingSensors) / 100.0
    
    // Кількість вільних місць (0-100) - обчислюється з parkingSensors
    @ColumnInfo(name = "free_spots")
    @get:PropertyName("free_spots")
    val freeSpots: Int = 0, // Обчислюється: parkingSensors.count { it == 0 }
    
    // Якість повітря CO: 0-500 ppm
    @ColumnInfo(name = "co_level")
    @get:PropertyName("co_level")
    val coLevel: Float = 0f,
    
    // Якість повітря NOx: 0-500 ppm
    @ColumnInfo(name = "nox_level")
    @get:PropertyName("nox_level")
    val noxLevel: Float = 0f,
    
    // Температура: -10 до 40°C
    @ColumnInfo(name = "temperature")
    @get:PropertyName("temperature")
    val temperature: Float = 0f,
    
    // Синхронізовано з хмарою
    @ColumnInfo(name = "synced")
    @get:PropertyName("synced")
    @set:PropertyName("synced")
    var synced: Boolean = false
) : Serializable {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "timestamp" to timestamp,
            "parking_sensors" to parkingSensors,
            "parking_occupied" to parkingOccupied,
            "free_spots" to freeSpots,
            "co_level" to coLevel,
            "nox_level" to noxLevel,
            "temperature" to temperature,
            "synced" to synced
        )
    }
    
    /**
     * Створює копію з ArrayList<Int> для parkingSensors (для Serializable)
     */
    fun toSerializable(): SensorData {
        return this.copy(parkingSensors = ArrayList(this.parkingSensors))
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): SensorData {
            // Парсимо масив датчиків
            val parkingSensors = when (val sensors = map["parking_sensors"]) {
                is List<*> -> sensors.mapNotNull { (it as? Number)?.toInt() }
                else -> List(100) { 0 }
            }
            
            // Обчислюємо похідні значення
            val occupiedCount = parkingSensors.sum()
            val parkingOccupied = (occupiedCount / 100f).coerceIn(0f, 1f)
            val freeSpots = 100 - occupiedCount
            
            return SensorData(
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                parkingSensors = parkingSensors,
                parkingOccupied = parkingOccupied,
                freeSpots = freeSpots,
                coLevel = (map["co_level"] as? Number)?.toFloat() ?: 0f,
                noxLevel = (map["nox_level"] as? Number)?.toFloat() ?: 0f,
                temperature = (map["temperature"] as? Number)?.toFloat() ?: 0f,
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
    }
}

