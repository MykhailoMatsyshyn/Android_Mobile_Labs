package dev.matsyshyn.smartparkingsystem.data.api

import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import retrofit2.http.*

/**
 * REST API інтерфейс для керування пристроями (Компонент 3)
 */
interface DeviceApiService {
    /**
     * Отримати стан всіх пристроїв
     */
    @GET("api/devices")
    suspend fun getAllDevices(): DevicesResponse
    
    /**
     * Отримати стан конкретного пристрою
     */
    @GET("api/devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): DeviceResponse
    
    /**
     * Оновити стан пристрою
     */
    @PUT("api/devices/{deviceId}")
    suspend fun updateDevice(
        @Path("deviceId") deviceId: String,
        @Body request: DeviceUpdateRequest
    ): DeviceResponse
}

data class DevicesResponse(
    val devices: List<DeviceResponse>
)

data class DeviceResponse(
    val device_id: String,
    val device_type: String,
    val enabled: Boolean,
    val brightness: Int? = null,
    val fan_speed: Int? = null,
    val heating_power: Int? = null,
    val last_updated: Long,
    val status: String? = null
) {
    fun toDeviceState(): DeviceState {
        return DeviceState(
            deviceId = device_id,
            deviceType = dev.matsyshyn.smartparkingsystem.data.model.DeviceType.valueOf(device_type),
            enabled = enabled,
            brightness = brightness ?: 50,
            fanSpeed = fan_speed ?: 1,
            heatingPower = heating_power ?: 1,
            lastUpdated = last_updated,
            synced = true
        )
    }
}

data class DeviceUpdateRequest(
    val enabled: Boolean? = null,
    val brightness: Int? = null,
    val fan_speed: Int? = null,
    val heating_power: Int? = null
)





