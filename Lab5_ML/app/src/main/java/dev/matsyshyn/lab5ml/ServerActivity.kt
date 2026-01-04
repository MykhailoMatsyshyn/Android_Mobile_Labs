package dev.matsyshyn.lab5ml

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class ServerActivity : AppCompatActivity(), SensorEventListener {

    // UUID 
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    
    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    // Сенсори
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    // Дані (6 каналів)
    private var ax = 0f; private var ay = 0f; private var az = 0f
    private var gx = 0f; private var gy = 0f; private var gz = 0f

    private lateinit var tvInfo: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        tvInfo = findViewById(R.id.tvInfo)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (checkPermissions()) startServer() else requestPermissions()
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager?.adapter
        
        // Перевірка чи Bluetooth увімкнено
        if (adapter == null) {
            tvInfo.text = "❌ Bluetooth не підтримується на цьому пристрої"
            return
        }
        
        if (!adapter.isEnabled) {
            tvInfo.text = "❌ Bluetooth вимкнено!\n\nБудь ласка, увімкніть Bluetooth в налаштуваннях."
            return
        }
        
        if (!adapter.isMultipleAdvertisementSupported) {
            tvInfo.text = "⚠️ BLE Advertising не підтримується\n\nПотрібен Android 5.0+ з підтримкою BLE"
            return
        }
        
        // Налаштування GATT
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // Додаємо descriptor для notifications (стандартний UUID)
        val configDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(configDescriptor)
        service.addCharacteristic(characteristic)

        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        gattServer?.addService(service)

        // Реклама (Advertising)
        advertiser = adapter?.bluetoothLeAdvertiser
        
        if (advertiser == null) {
            tvInfo.text = "❌ BLE Advertiser недоступний\n\nПеревірте, чи Bluetooth увімкнено"
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        
        advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                android.util.Log.d("Lab5", "BLE Advertising started successfully")
                runOnUiThread {
                    tvInfo.text = "📡 Bluetooth LE Advertising started!\n\nWaiting for connection...\n\nMake sure Collector Mode is scanning."
                }
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                runOnUiThread {
                    val errorMsg = when (errorCode) {
                        ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising вже запущено"
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "Дані занадто великі"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE Advertising не підтримується"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "Внутрішня помилка"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Занадто багато advertisers"
                        else -> "Помилка: $errorCode"
                    }
                    tvInfo.text = "❌ Failed to start advertising\n\n$errorMsg\n\nПеревірте:\n1. Bluetooth увімкнено\n2. Дозволи надано\n3. BLE підтримується"
                }
            }
        })

        // Запуск циклу відправки даних (кожні 100 мс = 10 Гц, для ML це мінімум)
        startSendingLoop()
    }

    private fun startSendingLoop() {
        val runnable = object : Runnable {
            override fun run() {
                sendData()
                handler.postDelayed(this, 100) // 10 разів на секунду
            }
        }
        // ВАЖЛИВО: Затримка перед першою відправкою, щоб MTU negotiation встиг завершитися
        handler.postDelayed(runnable, 500)
    }

    @SuppressLint("MissingPermission")
    private fun sendData() {
        // Формат: "ax,ay,az,gx,gy,gz"
        val dataString = "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f".format(Locale.US, ax, ay, az, gx, gy, gz)
        
        val connectedCount = registeredDevices.size
        val statusText = if (connectedCount > 0) {
            "📡 Broadcasting to $connectedCount device(s)\n\n$dataString"
        } else {
            "⏳ Waiting for connection...\n\n$dataString"
        }
        
        runOnUiThread { tvInfo.text = statusText }

        if (registeredDevices.isEmpty()) {
            android.util.Log.d("Lab5", "No subscribed devices, skipping send")
            return
        }

        val service = gattServer?.getService(SERVICE_UUID)
        val charac = service?.getCharacteristic(CHAR_UUID)
        
        if (charac == null) {
            android.util.Log.e("Lab5", "Characteristic not found!")
            return
        }

        // Перевірка розміру даних
        val dataBytes = dataString.toByteArray(Charsets.UTF_8)
        if (dataBytes.size > 512) {
            android.util.Log.e("Lab5", "Data too large: ${dataBytes.size} bytes")
            return
        }
        
        // Встановлюємо значення ПЕРЕД викликом notifyCharacteristicChanged
        charac.value = dataBytes
        
        android.util.Log.d("Lab5", "Sending data: '$dataString' (${dataBytes.size} bytes) to ${registeredDevices.size} device(s)")
        
        // Відправляємо всім підписаним пристроям
        for (device in registeredDevices) {
            val success = gattServer?.notifyCharacteristicChanged(device, charac, false) ?: false
            android.util.Log.d("Lab5", "Notify to ${device.address}: ${if (success) "OK" else "FAILED"}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            ax = event.values[0]; ay = event.values[1]; az = event.values[2]
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gx = event.values[0]; gy = event.values[1]; gz = event.values[2]
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // Callback для підключень
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            android.util.Log.d("Lab5", "Connection state change: ${device.address}, newState=$newState, status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // НЕ додаємо в registeredDevices тут - тільки після підписки на notifications
                runOnUiThread {
                    tvInfo.text = "✅ Device connected: ${device.name ?: device.address}\n\nWaiting for subscription..."
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
                runOnUiThread {
                    tvInfo.text = "❌ Device disconnected\n\nWaiting for connection..."
                }
                android.util.Log.d("Lab5", "Device disconnected: ${device.address}")
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            super.onMtuChanged(device, mtu)
            android.util.Log.d("Lab5", "MTU changed for ${device.address}: $mtu bytes")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) {
                val isNotificationEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                                          value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                
                android.util.Log.d("Lab5", "Descriptor write request from ${device.address}: ${if (isNotificationEnabled) "ENABLED" else "DISABLED"}")
                android.util.Log.d("Lab5", "Descriptor value: ${value.contentToString()}")
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    android.util.Log.d("Lab5", "Sent success response for descriptor write")
                }
                
                if (isNotificationEnabled) {
                    registeredDevices.add(device)
                    runOnUiThread {
                        tvInfo.text = "✅ Device subscribed!\n📡 Sending data to: ${device.name ?: device.address}"
                    }
                    android.util.Log.d("Lab5", "Device ${device.address} subscribed to notifications")
                } else {
                    registeredDevices.remove(device)
                    android.util.Log.d("Lab5", "Device ${device.address} unsubscribed from notifications")
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Зупиняємо advertising
        @SuppressLint("MissingPermission")
        advertiser?.stopAdvertising(null)
        
        // Закриваємо GATT Server
        @SuppressLint("MissingPermission")
        gattServer?.close()
        
        // Зупиняємо цикл відправки
        handler.removeCallbacksAndMessages(null)
    }

    // Дозволи
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startServer()
        } else {
            tvInfo.text = "Permissions denied. Please grant Bluetooth permissions."
        }
    }
}

