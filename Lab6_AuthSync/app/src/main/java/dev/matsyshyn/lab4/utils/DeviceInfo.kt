package dev.matsyshyn.lab4.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Утиліта для отримання інформації про пристрій
 */
object DeviceInfo {
    
    /**
     * Отримати унікальний ID пристрою (Android ID)
     */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }
    
    /**
     * Отримати назву пристрою
     */
    fun getDeviceName(): String {
        return Build.MODEL ?: "Unknown Device"
    }
    
    /**
     * Отримати модель пристрою
     */
    fun getDeviceModel(): String {
        return Build.MODEL ?: "Unknown"
    }
    
    /**
     * Отримати виробника
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER ?: "Unknown"
    }
    
    /**
     * Отримати версію Android
     */
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE ?: "Unknown"
    }
    
    /**
     * Отримати SDK версію
     */
    fun getOsSdkInt(): Int {
        return Build.VERSION.SDK_INT
    }
    
    /**
     * Отримати повну інформацію про пристрій
     */
    fun getDeviceInfo(context: Context): DeviceInfoData {
        return DeviceInfoData(
            deviceId = getDeviceId(context),
            deviceName = "${getManufacturer()} ${getDeviceName()}",
            deviceModel = getDeviceModel(),
            manufacturer = getManufacturer(),
            osVersion = getOsVersion(),
            osSdkInt = getOsSdkInt()
        )
    }
    
    data class DeviceInfoData(
        val deviceId: String,
        val deviceName: String,
        val deviceModel: String,
        val manufacturer: String,
        val osVersion: String,
        val osSdkInt: Int
    )
}



