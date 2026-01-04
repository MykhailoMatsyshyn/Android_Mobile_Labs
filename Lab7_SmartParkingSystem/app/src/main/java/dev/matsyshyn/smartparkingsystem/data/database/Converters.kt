package dev.matsyshyn.smartparkingsystem.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.matsyshyn.smartparkingsystem.data.model.ComparisonOperator
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import dev.matsyshyn.smartparkingsystem.data.model.SensorType

class Converters {
    @TypeConverter
    fun fromDeviceType(value: DeviceType): String {
        return value.name
    }

    @TypeConverter
    fun toDeviceType(value: String): DeviceType {
        return DeviceType.valueOf(value)
    }

    @TypeConverter
    fun fromSensorType(value: SensorType): String {
        return value.name
    }

    @TypeConverter
    fun toSensorType(value: String): SensorType {
        return SensorType.valueOf(value)
    }

    @TypeConverter
    fun fromComparisonOperator(value: ComparisonOperator): String {
        return value.name
    }

    @TypeConverter
    fun toComparisonOperator(value: String): ComparisonOperator {
        return ComparisonOperator.valueOf(value)
    }
    
    // Конвертер для масиву датчиків парковки (List<Int>)
    @TypeConverter
    fun fromParkingSensorsList(value: List<Int>): String {
        val gson = Gson()
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toParkingSensorsList(value: String): List<Int> {
        val gson = Gson()
        val listType = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson<List<Int>>(value, listType) ?: List(100) { 0 }
    }
}

