package dev.matsyshyn.smartparkingsystem.data.device

import dev.matsyshyn.smartparkingsystem.data.model.DeviceState
import dev.matsyshyn.smartparkingsystem.data.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Емулятор керування пристроями
 */
class DeviceController {
    private val _deviceStates = MutableStateFlow<List<DeviceState>>(emptyList())
    val deviceStates: StateFlow<List<DeviceState>> = _deviceStates.asStateFlow()
    
    init {
        // Ініціалізуємо пристрої за замовчуванням
        initializeDevices()
    }
    
    private fun initializeDevices() {
        val devices = listOf(
            DeviceState(
                deviceId = "direction_panels_1",
                deviceType = DeviceType.DIRECTION_PANELS,
                enabled = false,
                brightness = 50
            ),
            DeviceState(
                deviceId = "ventilation_1",
                deviceType = DeviceType.VENTILATION,
                enabled = false,
                fanSpeed = 1
            ),
            DeviceState(
                deviceId = "heating_1",
                deviceType = DeviceType.HEATING,
                enabled = false,
                heatingPower = 1
            )
        )
        _deviceStates.value = devices
    }
    
    /**
     * Отримує стан пристрою за ID
     */
    fun getDeviceState(deviceId: String): DeviceState? {
        return _deviceStates.value.find { it.deviceId == deviceId }
    }
    
    /**
     * Отримує стан пристрою за типом
     */
    fun getDeviceStateByType(deviceType: DeviceType): DeviceState? {
        return _deviceStates.value.find { it.deviceType == deviceType }
    }
    
    /**
     * Оновлює стан пристрою
     */
    fun updateDeviceState(deviceId: String, update: (DeviceState) -> DeviceState) {
        val currentStates = _deviceStates.value.toMutableList()
        val index = currentStates.indexOfFirst { it.deviceId == deviceId }
        if (index >= 0) {
            currentStates[index] = update(currentStates[index]).copy(
                lastUpdated = System.currentTimeMillis(),
                synced = false
            )
            _deviceStates.value = currentStates
        }
    }
    
    /**
     * Вмикає/вимикає панелі з напрямками
     */
    fun setDirectionPanelsEnabled(enabled: Boolean, brightness: Int = 50) {
        val device = getDeviceStateByType(DeviceType.DIRECTION_PANELS)
        device?.let {
            updateDeviceState(it.deviceId) { state ->
                state.copy(
                    enabled = enabled,
                    brightness = brightness.coerceIn(0, 100)
                )
            }
        }
    }
    
    /**
     * Встановлює швидкість вентиляції
     */
    fun setVentilationSpeed(speed: Int, enabled: Boolean = true) {
        val device = getDeviceStateByType(DeviceType.VENTILATION)
        device?.let {
            updateDeviceState(it.deviceId) { state ->
                state.copy(
                    enabled = enabled,
                    fanSpeed = speed.coerceIn(1, 3)
                )
            }
        }
    }
    
    /**
     * Вмикає/вимикає систему обігріву
     */
    fun setHeatingEnabled(enabled: Boolean, power: Int = 1) {
        val device = getDeviceStateByType(DeviceType.HEATING)
        device?.let {
            updateDeviceState(it.deviceId) { state ->
                state.copy(
                    enabled = enabled,
                    heatingPower = power.coerceIn(1, 2)
                )
            }
        }
    }
    
    /**
     * Отримує всі стани пристроїв
     */
    fun getAllDeviceStates(): List<DeviceState> {
        return _deviceStates.value
    }
}





