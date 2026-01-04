package dev.matsyshyn.lab4

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
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import kotlin.math.sqrt

class ServerActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "ServerActivity"
    }

    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    // Сенсор: ГІРОСКОП
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var stabilityScore: Float = 0f
    private var rawX: Float = 0f
    private var rawY: Float = 0f
    private var rawZ: Float = 0f

    private lateinit var tvStatus: TextView
    private lateinit var tvGyroData: TextView
    private lateinit var tvRawData: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        tvStatus = findViewById(R.id.tvServerStatus)
        tvGyroData = findViewById(R.id.tvGyroData)
        tvRawData = findViewById(R.id.tvRawData)
        
        findViewById<android.widget.Button>(R.id.btnTestSend).setOnClickListener {
            // Тестова відправка з не-нульовими значеннями
            rawX = 1.5f
            rawY = 2.3f
            rawZ = 0.8f
            stabilityScore = sqrt(rawX*rawX + rawY*rawY + rawZ*rawZ)
            updateAndNotify()
        }

        // 1. Ініціалізація Гіроскопа
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (gyroscope == null) {
            tvStatus.text = "Error: No Gyroscope!"
            return
        }
        
        // Ініціалізуємо початкові значення (для тесту)
        rawX = 0.01f
        rawY = 0.01f
        rawZ = 0.01f
        stabilityScore = sqrt(rawX*rawX + rawY*rawY + rawZ*rawZ)

        // 2. Старт Сервера (якщо є дозволи)
        if (hasPermissions()) {
            startServer()
        } else {
            requestPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager?.adapter?.isEnabled == false) {
            tvStatus.text = "Bluetooth Disabled!"
            return
        }

        // Створюємо GATT Server
        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        if (gattServer == null) {
            tvStatus.text = "Error: Cannot create GATT Server"
            Log.e(TAG, "Failed to create GATT server")
            return
        }

        // Створюємо Service та Characteristic
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Додаємо CCCD descriptor для нотифікацій
        val descriptor = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        Log.d(TAG, "GATT Server created and service added")

        // Стартуємо рекламування
        advertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser
        startAdvertising()

        // Реєструємо слухач сенсора
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)

        // Запускаємо періодичну відправку даних
        startPeriodicUpdates()
    }

    private fun startPeriodicUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                if (gattServer != null) {
                    updateAndNotify()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    @SuppressLint("MissingPermission")
    private fun updateAndNotify() {
        // Формуємо JSON з stability та координатами x, y, z
        // s - stability, x, y, z - координати гіроскопа
        val json = String.format(Locale.US, "{\"s\":%.2f,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f}", 
            stabilityScore, rawX, rawY, rawZ)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        Log.d(TAG, "📤 Preparing to send JSON: '$json' (${jsonBytes.size} bytes)")

        // Оновлюємо UI сервера
        tvGyroData.text = "Stability: %.2f".format(stabilityScore)
        tvRawData.text = "X:%.1f Y:%.1f Z:%.1f".format(rawX, rawY, rawZ)

        if (gattServer == null) {
            Log.w(TAG, "GATT server is null, cannot send data")
            return
        }

        val service = gattServer?.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service not found!")
            return
        }

        val characteristic = service.getCharacteristic(CHAR_UUID)
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found!")
            return
        }

        if (registeredDevices.isEmpty()) {
            Log.d(TAG, "No registered devices (${registeredDevices.size}), skipping notification")
            // Оновлюємо значення characteristic навіть якщо немає підписаних клієнтів
            characteristic.value = jsonBytes
            return
        }

        Log.d(TAG, "📤 Sending data: '$json' (${jsonBytes.size} bytes) to ${registeredDevices.size} device(s)")
        
        // Встановлюємо значення characteristic
        characteristic.value = jsonBytes
        
        // Відправляємо нотифікації всім підписаним клієнтам
        var successCount = 0
        var failCount = 0
        for (device in registeredDevices) {
            try {
                val success = gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
                if (success) {
                    successCount++
                    Log.d(TAG, "✅ Notification sent successfully to ${device.address}")
                } else {
                    failCount++
                    Log.e(TAG, "❌ Failed to send notification to ${device.address}")
                }
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "❌ Error sending notification to ${device.address}: ${e.message}", e)
            }
        }
        
        if (registeredDevices.isNotEmpty()) {
            Log.d(TAG, "📤 Sent to $successCount/${registeredDevices.size} clients (failed: $failCount)")
            if (failCount > 0) {
                runOnUiThread {
                    Toast.makeText(this@ServerActivity, "Warning: Failed to send to $failCount client(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Слухач сенсора (Гіроскоп)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            rawX = event.values[0]
            rawY = event.values[1]
            rawZ = event.values[2]
            stabilityScore = sqrt(rawX*rawX + rawY*rawY + rawZ*rawZ)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не використовується
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "Connection state changed: device=${device.address}, status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread {
                        tvStatus.text = "Client Connected (${registeredDevices.size + 1})"
                    }
                    Log.d(TAG, "Client connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    registeredDevices.remove(device)
                    runOnUiThread {
                        tvStatus.text = "Client Disconnected (${registeredDevices.size} client(s))"
                    }
                    Log.d(TAG, "Client disconnected: ${device.address}, remaining: ${registeredDevices.size}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "Descriptor write request from ${device.address}, value: ${value.contentToString()}")
            
            if (descriptor.uuid == CCCD_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    registeredDevices.add(device)
                    Log.d(TAG, "Device ${device.address} subscribed. Total: ${registeredDevices.size}")
                    runOnUiThread { 
                        tvStatus.text = "Client Subscribed (${registeredDevices.size} client(s))"
                    }
                    // Відправляємо тестові дані після підписки
                    handler.postDelayed({
                        updateAndNotify()
                        Log.d(TAG, "Sent test data after subscription")
                    }, 500)
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    registeredDevices.remove(device)
                    Log.d(TAG, "Device ${device.address} unsubscribed. Remaining: ${registeredDevices.size}")
                    runOnUiThread { 
                        tvStatus.text = "Client Unsubscribed (${registeredDevices.size} client(s))"
                    }
                } else {
                    Log.w(TAG, "Unknown descriptor value: ${value.contentToString()}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                val json = String.format(Locale.US, "{\"s\":%.2f,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f}", 
                    stabilityScore, rawX, rawY, rawZ)
                characteristic.value = json.toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
                Log.d(TAG, "Sent characteristic value: $json")
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE Advertising started successfully")
            runOnUiThread { 
                tvStatus.text = "Server Broadcasting..."
                Toast.makeText(this@ServerActivity, "BLE Server is broadcasting", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising Failed: $errorCode")
            runOnUiThread {
                tvStatus.text = "Advertising Failed: $errorCode"
                Toast.makeText(this@ServerActivity, "BLE Advertising failed: $errorCode", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.d(TAG, "Started BLE advertising")
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                1
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startServer()
        } else {
            tvStatus.text = "Permissions Denied!"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        @SuppressLint("MissingPermission")
        advertiser?.stopAdvertising(advertiseCallback)
        sensorManager.unregisterListener(this)
        gattServer?.close()
    }
}




