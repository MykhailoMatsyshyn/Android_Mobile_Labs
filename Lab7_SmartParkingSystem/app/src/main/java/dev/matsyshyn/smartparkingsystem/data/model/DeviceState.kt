package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "device_state")
data class DeviceState(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "device_type")
    @get:PropertyName("device_type")
    val deviceType: DeviceType,
    
    // Для панелей з напрямками
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    @set:PropertyName("enabled")
    var enabled: Boolean = false,
    
    @ColumnInfo(name = "brightness")
    @get:PropertyName("brightness")
    @set:PropertyName("brightness")
    var brightness: Int = 50, // 0-100
    
    // Для вентилятора
    @ColumnInfo(name = "fan_speed")
    @get:PropertyName("fan_speed")
    @set:PropertyName("fan_speed")
    var fanSpeed: Int = 1, // 1-3
    
    // Для системи обігріву
    @ColumnInfo(name = "heating_power")
    @get:PropertyName("heating_power")
    @set:PropertyName("heating_power")
    var heatingPower: Int = 1, // 1-2
    
    @ColumnInfo(name = "last_updated")
    @get:PropertyName("last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "synced")
    @get:PropertyName("synced")
    @set:PropertyName("synced")
    var synced: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> {
        // Базові поля для всіх пристроїв
        val data = mutableMapOf<String, Any>(
            "device_id" to deviceId,
            "device_type" to deviceType.name,
            "enabled" to enabled,
            "last_updated" to lastUpdated,
            "synced" to synced
        )
        
        // Додаємо тільки релевантні поля залежно від типу пристрою
        when (deviceType) {
            DeviceType.DIRECTION_PANELS -> {
                data["brightness"] = brightness
                // НЕ додаємо fan_speed та heating_power для панелей!
            }
            DeviceType.VENTILATION -> {
                data["fan_speed"] = fanSpeed
                // НЕ додаємо brightness та heating_power для вентиляції!
            }
            DeviceType.HEATING -> {
                data["heating_power"] = heatingPower
                // НЕ додаємо brightness та fan_speed для обігріву!
            }
        }
        
        return data
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): DeviceState {
            return DeviceState(
                deviceId = (map["device_id"] as? String) ?: "",
                deviceType = DeviceType.valueOf((map["device_type"] as? String) ?: "DIRECTION_PANELS"),
                enabled = (map["enabled"] as? Boolean) ?: false,
                brightness = (map["brightness"] as? Number)?.toInt() ?: 50,
                fanSpeed = (map["fan_speed"] as? Number)?.toInt() ?: 1,
                heatingPower = (map["heating_power"] as? Number)?.toInt() ?: 1,
                lastUpdated = (map["last_updated"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
    }
}

enum class DeviceType {
    DIRECTION_PANELS,  // Панелі з напрямками
    VENTILATION,       // Вентиляція
    HEATING            // Обігрів
}

