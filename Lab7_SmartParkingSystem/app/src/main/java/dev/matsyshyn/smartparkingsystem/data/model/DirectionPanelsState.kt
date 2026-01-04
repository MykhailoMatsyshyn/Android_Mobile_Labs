package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

/**
 * Історія змін стану панелей з напрямками
 */
@Entity(tableName = "direction_panels_history")
data class DirectionPanelsState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "device_id")
    @get:PropertyName("device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    val enabled: Boolean,
    
    @ColumnInfo(name = "brightness")
    @get:PropertyName("brightness")
    val brightness: Int, // 0-100
    
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
            "device_type" to "DIRECTION_PANELS",
            "enabled" to enabled,
            "brightness" to brightness,
            "timestamp" to timestamp,
            "synced" to synced
        )
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): DirectionPanelsState {
            return DirectionPanelsState(
                deviceId = (map["device_id"] as? String) ?: "",
                enabled = (map["enabled"] as? Boolean) ?: false,
                brightness = (map["brightness"] as? Number)?.toInt() ?: 50,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
        
        fun fromDeviceState(deviceState: DeviceState): DirectionPanelsState {
            require(deviceState.deviceType == DeviceType.DIRECTION_PANELS) {
                "DeviceState must be DIRECTION_PANELS type"
            }
            return DirectionPanelsState(
                deviceId = deviceState.deviceId,
                enabled = deviceState.enabled,
                brightness = deviceState.brightness,
                timestamp = deviceState.lastUpdated,
                synced = deviceState.synced
            )
        }
    }
}

