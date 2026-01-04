package dev.matsyshyn.smartparkingsystem.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey
    @ColumnInfo(name = "rule_id")
    val ruleId: String,
    
    @ColumnInfo(name = "rule_name")
    @get:PropertyName("rule_name")
    val ruleName: String,
    
    @ColumnInfo(name = "enabled")
    @get:PropertyName("enabled")
    var enabled: Boolean = true,
    
    // Умова: тип сенсора
    @ColumnInfo(name = "sensor_type")
    @get:PropertyName("sensor_type")
    val sensorType: SensorType,
    
    // Умова: оператор порівняння
    @ColumnInfo(name = "operator")
    @get:PropertyName("operator")
    val operator: ComparisonOperator,
    
    // Умова: порогове значення
    @ColumnInfo(name = "threshold")
    @get:PropertyName("threshold")
    var threshold: Float,
    
    // Дія: тип пристрою
    @ColumnInfo(name = "device_type")
    @get:PropertyName("device_type")
    val deviceType: DeviceType,
    
    // Дія: параметри пристрою
    @ColumnInfo(name = "action_enabled")
    @get:PropertyName("action_enabled")
    val actionEnabled: Boolean = true,
    
    @ColumnInfo(name = "action_brightness")
    @get:PropertyName("action_brightness")
    val actionBrightness: Int = 50,
    
    @ColumnInfo(name = "action_fan_speed")
    @get:PropertyName("action_fan_speed")
    val actionFanSpeed: Int = 3,
    
    @ColumnInfo(name = "action_heating_power")
    @get:PropertyName("action_heating_power")
    val actionHeatingPower: Int = 2,
    
    @ColumnInfo(name = "last_triggered")
    @get:PropertyName("last_triggered")
    var lastTriggered: Long = 0,
    
    @ColumnInfo(name = "synced")
    @get:PropertyName("synced")
    @set:PropertyName("synced")
    var synced: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "rule_id" to ruleId,
            "rule_name" to ruleName,
            "enabled" to enabled,
            "sensor_type" to sensorType.name,
            "operator" to operator.name,
            "threshold" to threshold,
            "device_type" to deviceType.name,
            "action_enabled" to actionEnabled,
            "action_brightness" to actionBrightness,
            "action_fan_speed" to actionFanSpeed,
            "action_heating_power" to actionHeatingPower,
            "last_triggered" to lastTriggered,
            "synced" to synced
        )
    }
    
    companion object {
        fun fromFirestoreMap(map: Map<String, Any>): AutomationRule {
            return AutomationRule(
                ruleId = (map["rule_id"] as? String) ?: "",
                ruleName = (map["rule_name"] as? String) ?: "",
                enabled = (map["enabled"] as? Boolean) ?: true,
                sensorType = SensorType.valueOf((map["sensor_type"] as? String) ?: "FREE_SPOTS"),
                operator = ComparisonOperator.valueOf((map["operator"] as? String) ?: "LESS_THAN"),
                threshold = (map["threshold"] as? Number)?.toFloat() ?: 0f,
                deviceType = DeviceType.valueOf((map["device_type"] as? String) ?: "DIRECTION_PANELS"),
                actionEnabled = (map["action_enabled"] as? Boolean) ?: true,
                actionBrightness = (map["action_brightness"] as? Number)?.toInt() ?: 50,
                actionFanSpeed = (map["action_fan_speed"] as? Number)?.toInt() ?: 3,
                actionHeatingPower = (map["action_heating_power"] as? Number)?.toInt() ?: 2,
                lastTriggered = (map["last_triggered"] as? Number)?.toLong() ?: 0,
                synced = (map["synced"] as? Boolean) ?: true
            )
        }
    }
}

enum class SensorType {
    FREE_SPOTS,    // Вільні місця
    CO_LEVEL,      // Рівень CO
    NOX_LEVEL,     // Рівень NOx
    TEMPERATURE    // Температура
}

enum class ComparisonOperator {
    LESS_THAN,        // <
    LESS_OR_EQUAL,    // <=
    GREATER_THAN,     // >
    GREATER_OR_EQUAL  // >=
}

