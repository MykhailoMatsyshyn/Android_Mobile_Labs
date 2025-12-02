package dev.matsyshyn

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import kotlin.math.sqrt

class ServerActivity : AppCompatActivity(), SensorEventListener {


    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    // Characteristic UUID: ...f1
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    // UUID для дескриптора (стандартний для сповіщень)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    // Сенсори
    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null
    private var currentMagneticValue: Float = 0f

    // UI та таймер
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvClients: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        tvStatus = findViewById(R.id.tvServerStatus)
        tvData = findViewById(R.id.tvMagneticData)
        tvDirection = findViewById(R.id.tvDirection)
        tvClients = findViewById(R.id.tvClientCount)

        // Ініціалізація магнітометра
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        magneticSensor?.let { sensor ->
            Log.d("BLE_SERVER", "Магнітометр знайдено: ${sensor.name}")
        } ?: run {
            tvStatus.text = "Магнітометр не знайдено!"
            Log.e("BLE_SERVER", "Магнітометр не доступний на цьому пристрої")
        }

        // Перевірка дозволів перед запуском BLE
        if (hasPermissions()) {
            startServer()
        } else {
            requestPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager?.adapter

        // Перевірка чи включений Bluetooth
        if (adapter == null || !adapter.isEnabled) {
            tvStatus.text = "Bluetooth вимкнено!"
            return
        }

        // 1. Налаштування GATT Сервера (внутрішня логіка)
        setupGattServer()

        // 2. Налаштування Реклами (щоб інші бачили цей телефон)
        startAdvertising()

        // 3. Запуск циклу відправки даних (кожні 1000 мс)
        startSendingLoop()
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            // Дозволяємо читати і підписуватися на оновлення (Notify)
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Додаємо дескриптор для клієнтських налаштувань (Notify)
        val configDescriptor = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(configDescriptor)
        service.addCharacteristic(characteristic)

        // Відкриваємо сервер
        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        advertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            tvStatus.text = "Помилка Advertise (не підтримується)"
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            // Не включаємо ім'я пристрою, щоб не перевищити ліміт 31 байт
            // UUID сервісу важливіший для фільтрації при скануванні
            .addServiceUuid(ParcelUuid(SERVICE_UUID)) // Вказуємо наш сервіс
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    // Таймер відправки даних
    private fun startSendingLoop() {
        val runnable = object : Runnable {
            override fun run() {
                updateAndNotifyClients()
                handler.postDelayed(this, 1000) // Повторити через 1 сек
            }
        }
        // Затримка перед першою відправкою, щоб MTU negotiation встиг завершитися
        handler.postDelayed(runnable, 500)
    }

    @SuppressLint("MissingPermission")
    private fun updateAndNotifyClients() {
        // Формат Варіанту 12: JSON {"magnetic": value, "direction": "N/S"}
        val direction = if (currentMagneticValue >= 40) "N" else "S" // Умовний напрямок
        val jsonString = String.format(Locale.US, "{\"magnetic\":%.1f,\"direction\":\"%s\"}", currentMagneticValue, direction)

        // Оновлюємо UI
        tvData.text = "%.1f мкТл".format(currentMagneticValue)
        tvDirection.text = "Напрямок: $direction"

        if (registeredDevices.isEmpty()) {
            Log.d("BLE_SERVER", "Немає підключених клієнтів")
            return
        }

        val service = gattServer?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHAR_UUID)
        if (characteristic == null) {
            Log.e("BLE_SERVER", "Characteristic не знайдено!")
            return
        }

        // Записуємо дані в характеристику
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
        
        // Перевірка розміру (BLE має обмеження ~20-23 байти, але з MTU negotiation може бути більше)
        if (jsonBytes.size > 512) {
            Log.e("BLE_SERVER", "Дані занадто великі: ${jsonBytes.size} байт")
            return
        }
        
        // ВАЖЛИВО: Встановлюємо значення ПЕРЕД викликом notifyCharacteristicChanged
        characteristic.value = jsonBytes
        
        Log.d("BLE_SERVER", "Відправляю дані: '$jsonString' (${jsonBytes.size} байт)")
        Log.d("BLE_SERVER", "Підключено клієнтів: ${registeredDevices.size}")

        // Відправляємо всім підключеним клієнтам
        for (device in registeredDevices) {
            // Використовуємо notifyCharacteristicChanged - він автоматично відправляє characteristic.value
            val success = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
            Log.d("BLE_SERVER", "Відправка до ${device.address}: ${if (success) "OK" else "FAILED"}, розмір: ${jsonBytes.size} байт")
        }
    }

    // Callbacks (Зворотній зв'язок від Bluetooth)
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("BLE_SERVER", "Зміна стану підключення: ${device.address}, newState=$newState, status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // НЕ додаємо в registeredDevices тут - тільки після підписки на notifications
                runOnUiThread {
                    tvStatus.text = "Клієнт підключився, очікування підписки..."
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
                runOnUiThread { 
                    tvClients.text = "${registeredDevices.size}"
                    tvStatus.text = "Клієнт відключився"
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            super.onMtuChanged(device, mtu)
            Log.d("BLE_SERVER", "MTU змінено для ${device.address}: $mtu байт")
        }

        // Дозвіл на включення Notify
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (CCCD_UUID == descriptor.uuid) {
                val isNotificationEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                                           value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                
                Log.d("BLE_SERVER", "Запит на підписку від ${device.address}: ${if (isNotificationEnabled) "ENABLED" else "DISABLED"}")
                Log.d("BLE_SERVER", "Значення дескриптора: ${value.contentToString()}")
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    Log.d("BLE_SERVER", "Відправлено успішну відповідь на підписку")
                }
                
                if (isNotificationEnabled) {
                    registeredDevices.add(device)
                    runOnUiThread {
                        tvClients.text = "${registeredDevices.size}"
                        tvStatus.text = "Клієнт підписався на дані!"
                    }
                } else {
                    registeredDevices.remove(device)
                    runOnUiThread {
                        tvClients.text = "${registeredDevices.size}"
                    }
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            runOnUiThread { tvStatus.text = "Трансляція активна (Advertising)" }
        }
        override fun onStartFailure(errorCode: Int) {
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Дані занадто великі (ліміт 31 байт)"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Занадто багато активних advertising"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising вже запущено"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Внутрішня помилка"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Функція не підтримується"
                else -> "Невідома помилка: $errorCode"
            }
            runOnUiThread { 
                tvStatus.text = "Помилка трансляції: $errorMessage"
                Log.e("BLE_SERVER", "Advertising failed: $errorMessage (code: $errorCode)")
            }
        }
    }

    // Сенсор (Акселерометр/Магнітометр)
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                // Обчислення магнітуди вектора
                currentMagneticValue = sqrt(x*x + y*y + z*z)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        magneticSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Не зупиняємо Advertising тут, щоб зв'язок не рвався при згортанні
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Старіші версії Android - дозволи надаються автоматично
            return true
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 2)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Дозволи надано", Toast.LENGTH_SHORT).show()
                startServer()
            } else {
                Toast.makeText(this, "Потрібні дозволи для роботи BLE сервера", Toast.LENGTH_LONG).show()
                tvStatus.text = "Потрібні дозволи"
            }
        }
    }
}