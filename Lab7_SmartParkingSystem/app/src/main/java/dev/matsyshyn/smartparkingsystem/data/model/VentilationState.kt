package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

/**
 * Історія змін стану вентиляції
 */
@Entity(tableName = "ventilation_history")
data class VentilationState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "device_id")
    @get:PropertyName("device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    val enabled: Boolean,
    
    @ColumnInfo(name = "fan_speed")
    @get:PropertyName("fan_speed")
    val fanSpeed: Int, // 1-3
    
    @ColumnInfo(name = "timestamp")
    @get:PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "synced")
    @get:PropertyName("synced")
    @set:PropertyName("synced")
    var synced: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "device_id" to deviceId,
            "device_type" to "VENTILATION",
            "enabled" to enabled,
            "fan_speed" to fanSpeed,
            "timestamp" to timestamp,
            "synced" to synced
        )
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): VentilationState {
            return VentilationState(
                deviceId = (map["device_id"] as? String) ?: "",
                enabled = (map["enabled"] as? Boolean) ?: false,
                fanSpeed = (map["fan_speed"] as? Number)?.toInt() ?: 1,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
        
        fun fromDeviceState(deviceState: DeviceState): VentilationState {
            require(deviceState.deviceType == DeviceType.VENTILATION) {
                "DeviceState must be VENTILATION type"
            }
            return VentilationState(
                deviceId = deviceState.deviceId,
                enabled = deviceState.enabled,
                fanSpeed = deviceState.fanSpeed,
                timestamp = deviceState.lastUpdated,
                synced = deviceState.synced
            )
        }
    }
}





