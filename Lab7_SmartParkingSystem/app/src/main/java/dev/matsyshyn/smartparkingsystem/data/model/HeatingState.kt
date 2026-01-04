package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

/**
 * Історія змін стану системи обігріву
 */
@Entity(tableName = "heating_history")
data class HeatingState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "device_id")
    @get:PropertyName("device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    val enabled: Boolean,
    
    @ColumnInfo(name = "heating_power")
    @get:PropertyName("heating_power")
    val heatingPower: Int, // 1-2
    
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
            "device_type" to "HEATING",
            "enabled" to enabled,
            "heating_power" to heatingPower,
            "timestamp" to timestamp,
            "synced" to synced
        )
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): HeatingState {
            return HeatingState(
                deviceId = (map["device_id"] as? String) ?: "",
                enabled = (map["enabled"] as? Boolean) ?: false,
                heatingPower = (map["heating_power"] as? Number)?.toInt() ?: 1,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
        
        fun fromDeviceState(deviceState: DeviceState): HeatingState {
            require(deviceState.deviceType == DeviceType.HEATING) {
                "DeviceState must be HEATING type"
            }
            return HeatingState(
                deviceId = deviceState.deviceId,
                enabled = deviceState.enabled,
                heatingPower = deviceState.heatingPower,
                timestamp = deviceState.lastUpdated,
                synced = deviceState.synced
            )
        }
    }
}





