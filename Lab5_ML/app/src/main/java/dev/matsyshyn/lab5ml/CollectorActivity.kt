package dev.matsyshyn.lab5ml

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.util.*

class CollectorActivity : AppCompatActivity() {

    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private val scanHandler = Handler(Looper.getMainLooper())

    // Збір даних
    private var isRecording = false
    private val dataBuffer = StringBuilder()
    private var recordCount = 0
    private var currentClass = 4 // За замовчуванням "Спокій"

    // Назви класів
    private val classNames = mapOf(
        0 to "Згинання рук",
        1 to "Підняття рук",
        2 to "Розведення рук",
        3 to "Обертання рук",
        4 to "Спокій"
    )

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvGyro: TextView
    private lateinit var tvSelectedClass: TextView
    private lateinit var tvRecordCount: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnSave: Button
    private lateinit var btnClass0: Button
    private lateinit var btnClass1: Button
    private lateinit var btnClass2: Button
    private lateinit var btnClass3: Button
    private lateinit var btnClass4: Button
    private lateinit var btnRescan: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector)

        tvStatus = findViewById(R.id.tvStatus)
        tvData = findViewById(R.id.tvData)
        tvAccel = findViewById(R.id.tvAccel)
        tvGyro = findViewById(R.id.tvGyro)
        tvSelectedClass = findViewById(R.id.tvSelectedClass)
        tvRecordCount = findViewById(R.id.tvRecordCount)
        btnRecord = findViewById(R.id.btnRecord)
        btnSave = findViewById(R.id.btnSave)
        btnClass0 = findViewById(R.id.btnClass0)
        btnClass1 = findViewById(R.id.btnClass1)
        btnClass2 = findViewById(R.id.btnClass2)
        btnClass3 = findViewById(R.id.btnClass3)
        btnClass4 = findViewById(R.id.btnClass4)
        btnRescan = findViewById(R.id.btnRescan)

        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        // Перевірка Bluetooth
        if (bluetoothAdapter == null) {
            tvStatus.text = "❌ Bluetooth не підтримується"
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "❌ Bluetooth вимкнено!\n\nУвімкніть Bluetooth в налаштуваннях"
            btnRescan.setOnClickListener {
                val intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(intent, 2)
            }
            return
        }

        // Автоматично починаємо скан
        btnRescan.setOnClickListener {
            if (checkPermissions()) {
                startScan()
            } else {
                requestPermissions()
            }
        }

        if (checkPermissions()) startScan() else requestPermissions()

        // Налаштування кнопок вибору класу
        btnClass0.setOnClickListener { selectClass(0) }
        btnClass1.setOnClickListener { selectClass(1) }
        btnClass2.setOnClickListener { selectClass(2) }
        btnClass3.setOnClickListener { selectClass(3) }
        btnClass4.setOnClickListener { selectClass(4) }

        // Оновлюємо відображення поточного класу
        updateClassDisplay()

        btnRecord.setOnClickListener {
            isRecording = !isRecording
            if (isRecording) {
                btnRecord.text = "⏸ STOP Recording"
                btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_dark))
                Toast.makeText(this, "Started recording: ${classNames[currentClass]}", Toast.LENGTH_SHORT).show()
            } else {
                btnRecord.text = "▶ START Recording"
                btnRecord.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            }
        }

        btnSave.setOnClickListener {
            saveToCsv()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "❌ Bluetooth вимкнено"
            return
        }

        tvStatus.text = "🔍 Scanning for Server..."
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            tvStatus.text = "❌ BLE Scanner недоступний"
            return
        }
        
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                Log.d("Lab5", "Device found: ${device.name ?: device.address}, RSSI: ${result.rssi}")
                
                // Зупиняємо сканування
                scanner.stopScan(this)
                scanCallback = null
                
                runOnUiThread {
                    tvStatus.text = "🔗 Connecting to ${device.name ?: device.address}..."
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_blue_dark))
                }
                
                // Підключаємося
                @SuppressLint("MissingPermission")
                bluetoothGatt = device.connectGatt(this@CollectorActivity, false, gattCallback)
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                scanCallback = null
                runOnUiThread {
                    val errorMsg = when (errorCode) {
                        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Скан вже запущено"
                        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Помилка реєстрації"
                        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE не підтримується"
                        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Внутрішня помилка"
                        else -> "Помилка: $errorCode"
                    }
                    tvStatus.text = "❌ Scan failed: $errorMsg\n\nНатисни 🔄 щоб спробувати знову"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                    Log.e("Lab5", "Scan failed: $errorMsg")
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        scanner.startScan(listOf(filter), settings, scanCallback)
        
        // Timeout сканування (15 секунд)
        scanHandler.postDelayed({
            @SuppressLint("MissingPermission")
            if (scanCallback != null) {
                scanner.stopScan(scanCallback)
                scanCallback = null
                runOnUiThread {
                    tvStatus.text = "⏱️ Scan timeout\n\nПристрій не знайдено.\nПеревірте:\n1. Server Mode запущено\n2. Телефони поруч\n3. Bluetooth увімкнено\n\nНатисни 🔄 щоб спробувати знову"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_orange_dark))
                    Log.w("Lab5", "Scan timeout - device not found")
                }
            }
        }, 15000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("Lab5", "Connection state change: newState=$newState, status=$status")
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    runOnUiThread { 
                        tvStatus.text = "🔗 Connected! Discovering services..."
                        tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_green_dark))
                    }
                    gatt.discoverServices()
                } else {
                    runOnUiThread {
                        tvStatus.text = "❌ Connection failed: $status\n\nНатисни 🔄 щоб спробувати знову"
                        tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                    }
                    Log.e("Lab5", "Connection failed with status: $status")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvStatus.text = "❌ Disconnected\n\nНатисни 🔄 щоб пересканувати"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                }
                Log.d("Lab5", "Device disconnected")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    tvStatus.text = "❌ Service discovery failed: $status"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                }
                return
            }
            
            val charac = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (charac != null) {
                // ВАЖЛИВО: Спочатку запитуємо збільшення MTU для більших пакетів
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestMtu(512)
                    runOnUiThread {
                        tvStatus.text = "🔗 Requesting MTU..."
                        tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_blue_dark))
                    }
                } else {
                    // Для старих версій одразу підписуємося
                    subscribeToNotifications(gatt, charac)
                }
            } else {
                runOnUiThread {
                    tvStatus.text = "❌ Service not found\n\nПеревірте UUID на Server"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d("Lab5", "MTU змінено: $mtu байт, статус: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val charac = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (charac != null) {
                    subscribeToNotifications(gatt, charac)
                }
            } else {
                runOnUiThread {
                    tvStatus.text = "⚠️ MTU negotiation failed, trying anyway..."
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_orange_dark))
                }
                // Спробуємо підписатися навіть якщо MTU не вдалося збільшити
                val charac = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (charac != null) {
                    subscribeToNotifications(gatt, charac)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        private fun subscribeToNotifications(gatt: BluetoothGatt, charac: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(charac, true)
            val desc = charac.getDescriptor(CCCD_UUID)
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
            runOnUiThread { 
                tvStatus.text = "✅ Subscribed! Waiting for data..."
                tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_green_dark))
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    tvStatus.text = "✅ Receiving Data!"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_green_dark))
                }
                Log.d("Lab5", "Descriptor write successful - notifications enabled")
            } else {
                runOnUiThread {
                    tvStatus.text = "❌ Failed to enable notifications: $status"
                    tvStatus.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                }
                Log.e("Lab5", "Descriptor write failed: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val rawString = String(characteristic.value, Charsets.UTF_8) // Приходить "ax,ay,az,gx,gy,gz"
            
            runOnUiThread {
                // Парсимо дані для відображення
                try {
                    val values = rawString.split(",")
                    if (values.size == 6) {
                        val ax = values[0].toFloatOrNull() ?: 0f
                        val ay = values[1].toFloatOrNull() ?: 0f
                        val az = values[2].toFloatOrNull() ?: 0f
                        val gx = values[3].toFloatOrNull() ?: 0f
                        val gy = values[4].toFloatOrNull() ?: 0f
                        val gz = values[5].toFloatOrNull() ?: 0f
                        
                        // Оновлюємо відображення
                        tvAccel.text = String.format(Locale.US, "ax: %.2f\nay: %.2f\naz: %.2f", ax, ay, az)
                        tvGyro.text = String.format(Locale.US, "gx: %.2f\ngy: %.2f\ngz: %.2f", gx, gy, gz)
                        tvData.text = "📡 Receiving data..."
                        
                        // Якщо записуємо - додаємо рядок у CSV форматі: ax,ay,az,gx,gy,gz,label
                        if (isRecording) {
                            dataBuffer.append("$rawString,$currentClass\n")
                            recordCount++
                            tvRecordCount.text = "Samples: $recordCount"
                            btnRecord.text = "⏸ STOP ($recordCount)"
                            
                            // Візуальний фідбек - підсвічуємо, що записується
                            tvRecordCount.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.holo_red_dark))
                        } else {
                            tvRecordCount.setTextColor(ContextCompat.getColor(this@CollectorActivity, android.R.color.darker_gray))
                        }
                    }
                } catch (e: Exception) {
                    tvData.text = "Error parsing: $rawString"
                    Log.e("Lab5", "Parse error", e)
                }
            }
        }
    }

    private fun saveToCsv() {
        try {
            val fileName = "training_data_${System.currentTimeMillis()}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            // Заголовок CSV
            val header = "ax,ay,az,gx,gy,gz,label\n"
            
            FileWriter(file).use { 
                it.write(header + dataBuffer.toString()) 
            }
            
            Toast.makeText(this, "✅ Saved $fileName\n(${recordCount} rows)", Toast.LENGTH_LONG).show()
            dataBuffer.clear()
            recordCount = 0
            tvRecordCount.text = "Samples: 0"
            tvRecordCount.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            btnRecord.text = "▶ START Recording"
            isRecording = false
        } catch (e: Exception) {
            Log.e("Lab5", "Error saving", e)
        }
    }

    // Дозволи
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Старі версії Android потребують дозвіл на локацію для сканування
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 1)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (bluetoothAdapter?.isEnabled == true) {
                startScan()
            } else {
                tvStatus.text = "❌ Bluetooth вимкнено. Увімкніть Bluetooth."
            }
        } else {
            tvStatus.text = "❌ Permissions denied. Please grant Bluetooth permissions."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) {
            if (bluetoothAdapter?.isEnabled == true) {
                if (checkPermissions()) {
                    startScan()
                } else {
                    requestPermissions()
                }
            } else {
                tvStatus.text = "❌ Bluetooth все ще вимкнено"
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Зупиняємо сканування
        scanCallback?.let {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        scanHandler.removeCallbacksAndMessages(null)
        
        // Закриваємо GATT з'єднання
        @SuppressLint("MissingPermission")
        bluetoothGatt?.close()
    }

    private fun selectClass(classNum: Int) {
        currentClass = classNum
        updateClassDisplay()
        // Візуальний фідбек
        Toast.makeText(this, "Selected: ${classNames[classNum]}", Toast.LENGTH_SHORT).show()
    }

    private fun updateClassDisplay() {
        tvSelectedClass.text = "${classNames[currentClass]} ($currentClass)"
        
        // Скидаємо виділення всіх кнопок
        val buttons = listOf(btnClass0, btnClass1, btnClass2, btnClass3, btnClass4)
        buttons.forEach { it.alpha = 0.6f }
        
        // Виділяємо поточну кнопку
        when (currentClass) {
            0 -> btnClass0.alpha = 1.0f
            1 -> btnClass1.alpha = 1.0f
            2 -> btnClass2.alpha = 1.0f
            3 -> btnClass3.alpha = 1.0f
            4 -> btnClass4.alpha = 1.0f
        }
    }
}

