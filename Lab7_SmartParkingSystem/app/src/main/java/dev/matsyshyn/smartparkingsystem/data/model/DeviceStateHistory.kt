package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

/**
 * Історія змін стану пристроїв
 * Зберігає кожну зміну стану пристрою з часовою позначкою
 */
@Entity(tableName = "device_state_history")
data class DeviceStateHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "device_id")
    @get:PropertyName("device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "device_type")
    @get:PropertyName("device_type")
    val deviceType: DeviceType,
    
    // Загальні поля для всіх пристроїв
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    val enabled: Boolean,
    
    @ColumnInfo(name = "timestamp")
    @get:PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "synced")
    @get:PropertyName("synced")
    @set:PropertyName("synced")
    var synced: Boolean = false,
    
    // Специфічні поля (nullable - заповнюються тільки для відповідного типу)
    // Для панелей з напрямками
    @ColumnInfo(name = "brightness")
    @get:PropertyName("brightness")
    val brightness: Int? = null, // 0-100, тільки для DIRECTION_PANELS
    
    // Для вентилятора
    @ColumnInfo(name = "fan_speed")
    @get:PropertyName("fan_speed")
    val fanSpeed: Int? = null, // 1-3, тільки для VENTILATION
    
    // Для системи обігріву
    @ColumnInfo(name = "heating_power")
    @get:PropertyName("heating_power")
    val heatingPower: Int? = null // 1-2, тільки для HEATING
) {
    fun toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "device_id" to deviceId,
            "device_type" to deviceType.name,
            "enabled" to enabled,
            "timestamp" to timestamp,
            "synced" to synced
        )
        
        // Додаємо специфічні поля тільки якщо вони не null
        brightness?.let { map["brightness"] = it }
        fanSpeed?.let { map["fan_speed"] = it }
        heatingPower?.let { map["heating_power"] = it }
        
        return map
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): DeviceStateHistory {
            return DeviceStateHistory(
                deviceId = (map["device_id"] as? String) ?: "",
                deviceType = DeviceType.valueOf((map["device_type"] as? String) ?: "DIRECTION_PANELS"),
                enabled = (map["enabled"] as? Boolean) ?: false,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                synced = (map["synced"] as? Boolean) ?: true,
                brightness = (map["brightness"] as? Number)?.toInt(),
                fanSpeed = (map["fan_speed"] as? Number)?.toInt(),
                heatingPower = (map["heating_power"] as? Number)?.toInt()
            )
        }
        
        /**
         * Створює DeviceStateHistory з DeviceState
         */
        fun fromDeviceState(deviceState: DeviceState): DeviceStateHistory {
            return DeviceStateHistory(
                deviceId = deviceState.deviceId,
                deviceType = deviceState.deviceType,
                enabled = deviceState.enabled,
                timestamp = deviceState.lastUpdated,
                synced = deviceState.synced,
                brightness = if (deviceState.deviceType == DeviceType.DIRECTION_PANELS) {
                    deviceState.brightness
                } else null,
                fanSpeed = if (deviceState.deviceType == DeviceType.VENTILATION) {
                    deviceState.fanSpeed
                } else null,
                heatingPower = if (deviceState.deviceType == DeviceType.HEATING) {
                    deviceState.heatingPower
                } else null
            )
        }
        
        /**
         * Конвертує DeviceStateHistory в DeviceState (поточний стан)
         */
        fun toDeviceState(history: DeviceStateHistory): DeviceState {
            return DeviceState(
                deviceId = history.deviceId,
                deviceType = history.deviceType,
                enabled = history.enabled,
                brightness = history.brightness ?: 50,
                fanSpeed = history.fanSpeed ?: 1,
                heatingPower = history.heatingPower ?: 1,
                lastUpdated = history.timestamp,
                synced = history.synced
            )
        }
    }
}





