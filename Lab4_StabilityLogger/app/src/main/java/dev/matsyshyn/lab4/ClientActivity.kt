package dev.matsyshyn.lab4

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import dev.matsyshyn.lab4.db.AppDatabase
import dev.matsyshyn.lab4.db.StabilityRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ClientActivity : AppCompatActivity() {

    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    
    // БД
    private lateinit var database: AppDatabase

    private lateinit var tvStatus: TextView
    private lateinit var tvReceivedScore: TextView
    private lateinit var tvPacketCount: TextView
    private lateinit var tvDbStatus: TextView
    private lateinit var tvRecordingStatus: TextView
    private lateinit var tvConnectionInfo: TextView
    private lateinit var btnScan: Button
    private lateinit var btnStartRecording: Button
    private lateinit var btnStopRecording: Button
    private lateinit var btnSetInterval: Button
    private lateinit var btnDeleteOld: Button
    private lateinit var btnViewGraph: Button
    private lateinit var btnSetThreshold: Button
    private lateinit var etRecordInterval: EditText
    private lateinit var etCriticalThreshold: EditText
    private lateinit var spinnerDeleteTime: Spinner
    private lateinit var tvThresholdStatus: TextView
    private lateinit var listDevices: ListView
    
    // Список знайдених пристроїв
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<BluetoothDevice>
    
    private var packetCount = 0
    private val dataBuffer = StringBuilder() // Буфер для збору часткових даних
    private var isSubscribed = false
    
    // Керування записом
    private var isRecording = false
    private var recordIntervalSeconds = 1 // За замовчуванням 1 секунда
    private var lastRecordTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var recordingRunnable: Runnable? = null
    
    // Критичне значення та сповіщення
    private var criticalThreshold: Float = 10.0f
    private var lastNotificationTime = 0L
    private val notificationCooldown = 5000L // 5 секунд між сповіщеннями
    private val CHANNEL_ID = "stability_alerts"
    
    // Останні отримані дані
    private var lastStability: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    
    companion object {
        private const val TAG = "ClientActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        tvStatus = findViewById(R.id.tvClientStatus)
        tvReceivedScore = findViewById(R.id.tvReceivedScore)
        tvPacketCount = findViewById(R.id.tvPacketCount)
        tvDbStatus = findViewById(R.id.tvDbStatus)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        tvConnectionInfo = findViewById(R.id.tvConnectionInfo)
        btnScan = findViewById(R.id.btnScan)
        btnStartRecording = findViewById(R.id.btnStartRecording)
        btnStopRecording = findViewById(R.id.btnStopRecording)
        btnSetInterval = findViewById(R.id.btnSetInterval)
        btnDeleteOld = findViewById(R.id.btnDeleteOld)
        btnViewGraph = findViewById(R.id.btnViewGraph)
        btnSetThreshold = findViewById(R.id.btnSetThreshold)
        etRecordInterval = findViewById(R.id.etRecordInterval)
        etCriticalThreshold = findViewById(R.id.etCriticalThreshold)
        spinnerDeleteTime = findViewById(R.id.spinnerDeleteTime)
        tvThresholdStatus = findViewById(R.id.tvThresholdStatus)
        listDevices = findViewById(R.id.listDevices)
        
        // Налаштування списку пристроїв
        deviceAdapter = object : ArrayAdapter<BluetoothDevice>(this, R.layout.list_item_device, foundDevices) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.list_item_device, parent, false)
                val device = getItem(position)
                if (device != null) {
                    val tvDeviceName = view.findViewById<TextView>(R.id.tvDeviceName)
                    val tvDeviceAddress = view.findViewById<TextView>(R.id.tvDeviceAddress)
                    tvDeviceName.text = device.name ?: "Unknown Device"
                    tvDeviceAddress.text = device.address
                }
                return view
            }
        }
        listDevices.adapter = deviceAdapter
        listDevices.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position < foundDevices.size) {
                connectToDevice(foundDevices[position])
            }
        }

        // Ініціалізація БД
        database = AppDatabase.getDatabase(this)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Налаштування Spinner для вибору часу видалення
        val deleteTimeOptions = arrayOf("1 hour", "6 hours", "12 hours", "24 hours", "48 hours", "1 week", "All")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, deleteTimeOptions.toList()) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view as TextView
                textView.setTextColor(getColor(android.R.color.black))
                textView.setBackgroundColor(getColor(android.R.color.white))
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView
                textView.setTextColor(getColor(android.R.color.black))
                textView.setBackgroundColor(getColor(android.R.color.white))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDeleteTime.adapter = adapter

        // Обробники подій
        btnScan.setOnClickListener { startScan() }
        btnStartRecording.setOnClickListener { startRecording() }
        btnStopRecording.setOnClickListener { stopRecording() }
        btnSetInterval.setOnClickListener {
            try {
                val interval = etRecordInterval.text.toString().toInt()
                if (interval > 0) {
                    recordIntervalSeconds = interval
                    Toast.makeText(this, "Recording interval set to $interval seconds", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Interval must be greater than 0", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid interval value", Toast.LENGTH_SHORT).show()
            }
        }
        btnDeleteOld.setOnClickListener { deleteOldRecords() }
        btnViewGraph.setOnClickListener {
            startActivity(android.content.Intent(this, GraphActivity::class.java))
        }

        btnSetThreshold.setOnClickListener {
            try {
                val threshold = etCriticalThreshold.text.toString().toFloat()
                if (threshold > 0) {
                    criticalThreshold = threshold
                    tvThresholdStatus.text = "Threshold: $threshold"
                    Toast.makeText(this, "Critical threshold set to $threshold", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Threshold must be greater than 0", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid threshold value", Toast.LENGTH_SHORT).show()
            }
        }

        // Створити канал сповіщень
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        tvStatus.text = "Scanning for Gyro Server..."
        btnScan.isEnabled = false
        foundDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        // Фільтр по UUID сервісу
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        Log.d(TAG, "Starting scan for service: $SERVICE_UUID")
        scanner?.startScan(listOf(filter), settings, scanCallback)
        
        // Зупиняємо сканування через 10 секунд
        handler.postDelayed({
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            runOnUiThread {
                btnScan.isEnabled = true
                if (foundDevices.isEmpty()) {
                    tvStatus.text = "No devices found. Try again."
                } else {
                    tvStatus.text = "Found ${foundDevices.size} device(s). Tap to connect."
                }
            }
        }, 10000)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            var hasCorrectService = false
            
            // Перевіряємо serviceUuids
            scanRecord?.serviceUuids?.forEach { uuid ->
                val uuidStr = uuid.toString()
                val targetUuidStr = SERVICE_UUID.toString()
                Log.d(TAG, "  Checking UUID: $uuidStr vs $targetUuidStr")
                if (uuidStr.equals(targetUuidStr, ignoreCase = true)) {
                    hasCorrectService = true
                    Log.d(TAG, "✅ Found device with correct service: ${device.name ?: device.address}")
                }
            }
            
            // Якщо serviceUuids порожній або null, перевіряємо serviceData
            if (!hasCorrectService && scanRecord?.serviceData != null) {
                scanRecord.serviceData.forEach { (uuid, _) ->
                    if (uuid.toString().equals(SERVICE_UUID.toString(), ignoreCase = true)) {
                        hasCorrectService = true
                        Log.d(TAG, "✅ Found device with correct service in serviceData: ${device.name ?: device.address}")
                    }
                }
            }
            
            // Додаємо пристрій до списку тільки якщо він має правильний UUID
            if (hasCorrectService && !foundDevices.any { it.address == device.address }) {
                foundDevices.add(device)
                runOnUiThread {
                    deviceAdapter.notifyDataSetChanged()
                    tvStatus.text = "Found ${foundDevices.size} Gyro Server(s). Tap to connect."
                    Log.d(TAG, "✅ Added device: ${device.name ?: device.address}")
                }
            } else if (!hasCorrectService) {
                Log.d(TAG, "❌ Device ${device.name ?: device.address} does not have correct service UUID - IGNORED")
            } else {
                Log.d(TAG, "Device ${device.name ?: device.address} already in list")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                btnScan.isEnabled = true
                tvStatus.text = "Scan Failed: $errorCode"
                Log.e(TAG, "Scan failed with error code: $errorCode")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to device: ${device.address}")
        tvStatus.text = "Connecting to ${device.name ?: device.address}..."
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "Connection state changed: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "✅ Connected to GATT server")
                    runOnUiThread {
                        tvStatus.text = "Connected! Requesting MTU..."
                        tvConnectionInfo.text = "✅ Connected\n📡 Requesting MTU..."
                        tvConnectionInfo.setTextColor(getColor(android.R.color.holo_blue_dark))
                    }
                    // Запитуємо збільшення MTU до 512 байтів (щоб JSON не розбивався)
                    val mtuResult = gatt.requestMtu(512)
                    Log.d(TAG, "📏 Requested MTU 512: $mtuResult")
                    // Також запускаємо discovery services
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "❌ Disconnected from GATT server")
                    isSubscribed = false
                    runOnUiThread {
                        tvStatus.text = "Disconnected"
                        tvConnectionInfo.text = "❌ Disconnected"
                        tvConnectionInfo.setTextColor(getColor(android.R.color.holo_red_dark))
                        btnStartRecording.isEnabled = false
                        if (isRecording) {
                            stopRecording()
                        }
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Toast.makeText(this@ClientActivity, "Connection lost: $status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "Services discovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "✅ Found service: $SERVICE_UUID")
                    val characteristic = service.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        Log.d(TAG, "✅ Found characteristic: $CHAR_UUID")
                        
                        // Увімкнути нотифікації
                        val notificationResult = gatt.setCharacteristicNotification(characteristic, true)
                        Log.d(TAG, "🔔 setCharacteristicNotification result: $notificationResult")
                        
                        if (!notificationResult) {
                            Log.e(TAG, "❌ Failed to enable notifications!")
                            runOnUiThread {
                                Toast.makeText(this@ClientActivity, "Failed to enable notifications", Toast.LENGTH_LONG).show()
                            }
                        }
                        
                        // Налаштувати descriptor для підписки
                        val descriptor = characteristic.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            Log.d(TAG, "✅ Descriptor found: ${descriptor.uuid}")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            val writeResult = gatt.writeDescriptor(descriptor)
                            Log.d(TAG, "📝 writeDescriptor result: $writeResult")
                            
                            if (!writeResult) {
                                Log.e(TAG, "❌ Failed to write descriptor!")
                            }
                        } else {
                            Log.e(TAG, "❌ Descriptor is null! Available descriptors:")
                            characteristic.descriptors.forEach { d ->
                                Log.e(TAG, "  - ${d.uuid}")
                            }
                            runOnUiThread { 
                                tvStatus.text = "Error: Descriptor not found!"
                                Toast.makeText(this@ClientActivity, "Descriptor not found", Toast.LENGTH_LONG).show()
                            }
                        }
                        
                        // Також прочитати початкове значення
                        val readResult = gatt.readCharacteristic(characteristic)
                        Log.d(TAG, "readCharacteristic result: $readResult")
                    } else {
                        Log.e(TAG, "Characteristic not found in service! Available characteristics:")
                        service.characteristics.forEach { c ->
                            Log.e(TAG, "  - ${c.uuid}")
                        }
                        runOnUiThread { 
                            tvStatus.text = "Error: Characteristic not found!"
                            Toast.makeText(this@ClientActivity, "Gyro Server characteristic not found", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                    runOnUiThread { 
                        tvStatus.text = "Service discovery failed: $status"
                        Toast.makeText(this@ClientActivity, "Failed to discover services", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "🔔 onDescriptorWrite called: status=$status, descriptor=${descriptor.uuid}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isSubscribed = true
                Log.d(TAG, "✅ Successfully subscribed to notifications! isSubscribed=$isSubscribed")
                runOnUiThread { 
                    tvStatus.text = "✅ Subscribed! Waiting for data..."
                    tvConnectionInfo.text = "✅ Connected & Subscribed\n📡 Ready to receive data"
                    tvConnectionInfo.setTextColor(getColor(android.R.color.holo_green_dark))
                    // Активуємо кнопки запису після підключення
                    btnStartRecording.isEnabled = true
                    Toast.makeText(this@ClientActivity, "Subscribed successfully! Waiting for data...", Toast.LENGTH_SHORT).show()
                }
                // Прочитати початкове значення після підписки
                val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (characteristic != null) {
                    val readResult = gatt.readCharacteristic(characteristic)
                    Log.d(TAG, "📖 Read characteristic after subscription: $readResult")
                } else {
                    Log.e(TAG, "❌ Characteristic is null after subscription!")
                }
            } else {
                Log.e(TAG, "❌ Subscription failed: $status")
                isSubscribed = false
                runOnUiThread { 
                    tvStatus.text = "❌ Subscription failed: $status"
                    tvConnectionInfo.text = "❌ Subscription failed\nStatus: $status"
                    tvConnectionInfo.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this@ClientActivity, "Failed to subscribe: $status", Toast.LENGTH_LONG).show()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "📏 MTU changed: $mtu bytes (status: $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "✅ MTU successfully set to $mtu bytes")
                runOnUiThread {
                    tvStatus.text = "MTU: $mtu bytes"
                }
            } else {
                Log.e(TAG, "❌ Failed to change MTU: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicRead: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.value != null) {
                val data = String(characteristic.value, Charsets.UTF_8)
                Log.d(TAG, "Characteristic read: $data (length: ${data.length})")
                runOnUiThread {
                    tvStatus.text = "Read data: ${data.take(20)}..."
                }
                processData(data)
            } else {
                Log.e(TAG, "Read failed or null value: status=$status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "🔥 onCharacteristicChanged CALLED!")
            val value = characteristic.value
            if (value == null || value.isEmpty()) {
                Log.w(TAG, "⚠️ Received empty data")
                return
            }
            
            val chunk = String(value, Charsets.UTF_8)
            Log.d(TAG, "📥 Received chunk: '$chunk' (${chunk.length} chars, ${value.size} bytes)")
            processData(chunk)
        }
    }

    private fun processData(chunk: String) {
        // Додаємо чанк до буфера
        dataBuffer.append(chunk)
        
        // Захист від переповнення - зберігаємо тільки останні 500 символів
        if (dataBuffer.length > 500) {
            val lastPart = dataBuffer.toString().takeLast(500)
            dataBuffer.clear()
            dataBuffer.append(lastPart)
        }
        
        val bufferString = dataBuffer.toString()
        
        // Обробляємо всі повні JSON об'єкти в буфері
        var processedAny = false
        var lastProcessedEnd = -1
        
        // Шукаємо всі повні JSON об'єкти, починаючи з початку
        var searchStart = 0
        while (searchStart < bufferString.length) {
            // Шукаємо початок JSON
            val jsonStart = bufferString.indexOf('{', searchStart)
            if (jsonStart == -1) break
            
            // Шукаємо відповідну закриваючу дужку
            var depth = 1
            var jsonEnd = jsonStart + 1
            while (jsonEnd < bufferString.length && depth > 0) {
                when (bufferString[jsonEnd]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                jsonEnd++
            }
            
            if (depth == 0) {
                // Знайдено повний JSON!
                val jsonStr = bufferString.substring(jsonStart, jsonEnd)
                
                try {
                    val json = JSONObject(jsonStr)
                    val stability = json.getDouble("s").toFloat()
                    val x = if (json.has("x")) json.getDouble("x").toFloat() else 0f
                    val y = if (json.has("y")) json.getDouble("y").toFloat() else 0f
                    val z = if (json.has("z")) json.getDouble("z").toFloat() else 0f

                    // Зберігаємо останні дані
                    lastStability = stability
                    lastX = x
                    lastY = y
                    lastZ = z

                    // Оновлюємо UI в UI thread
                    runOnUiThread {
                        tvReceivedScore.text = "%.2f".format(stability)
                        
                        if (stability >= criticalThreshold) {
                            tvReceivedScore.setTextColor(getColor(android.R.color.holo_red_dark))
                        } else {
                            tvReceivedScore.setTextColor(getColor(android.R.color.holo_green_dark))
                        }
                        
                        packetCount++
                        tvPacketCount.text = "📦 Packets: $packetCount"
                        tvStatus.text = "Status: Connected"
                        tvConnectionInfo.text = "✅ Connected\n📡 Receiving data\n🕐 Last: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                        tvConnectionInfo.setTextColor(getColor(android.R.color.holo_green_dark))
                    }

                    // Перевірка критичного значення
                    if (stability >= criticalThreshold) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNotificationTime >= notificationCooldown) {
                            showCriticalNotification(stability)
                            lastNotificationTime = currentTime
                        }
                    }

                    // Записуємо в БД якщо запис активний
                    if (isRecording) {
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastRecord = currentTime - lastRecordTime
                        val intervalMs = recordIntervalSeconds * 1000L
                        
                        if (timeSinceLastRecord >= intervalMs) {
                            saveToDb(stability, x, y, z)
                            lastRecordTime = currentTime
                        }
                    }
                    
                    processedAny = true
                    lastProcessedEnd = jsonEnd
                    searchStart = jsonEnd // Продовжуємо пошук наступного JSON
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ JSON parse error for '$jsonStr': ${e.message}")
                    // Продовжуємо пошук
                    searchStart = jsonEnd
                }
            } else {
                // Неповний JSON - чекаємо більше даних
                break
            }
        }
        
        // Очищаємо оброблені JSON з буфера
        if (processedAny && lastProcessedEnd > 0) {
            if (lastProcessedEnd < bufferString.length) {
                val remaining = bufferString.substring(lastProcessedEnd)
                dataBuffer.clear()
                dataBuffer.append(remaining)
            } else {
                dataBuffer.clear()
            }
        } else if (bufferString.length > 200 && !bufferString.contains("{")) {
            // Якщо буфер великий і немає навіть початку JSON - очищаємо
            dataBuffer.clear()
        }
    }

    private fun saveToDb(stability: Float, x: Float, y: Float, z: Float) {
        Log.d(TAG, "🔄 saveToDb called: stability=$stability, x=$x, y=$y, z=$z")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val record = StabilityRecord(
                    timestamp = System.currentTimeMillis(),
                    stabilityScore = stability,
                    x = x, y = y, z = z
                )
                database.stabilityDao().insert(record)
                val count = database.stabilityDao().getRecordCount()
                Log.d(TAG, "✅ Saved to DB! Total records: $count")
                
                // Оновлюємо статус в UI
                launch(Dispatchers.Main) {
                    tvDbStatus.text = "💾 DB: Saved (Total: $count)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving to DB: ${e.message}", e)
                launch(Dispatchers.Main) {
                    tvDbStatus.text = "💾 DB: Error - ${e.message?.take(20)}"
                }
            }
        }
    }

    private fun startRecording() {
        if (!isSubscribed) {
            Log.w(TAG, "⚠️ Cannot start recording: not subscribed to server")
            Toast.makeText(this, "Please connect to server first", Toast.LENGTH_SHORT).show()
            return
        }
        
        isRecording = true
        lastRecordTime = System.currentTimeMillis()
        btnStartRecording.isEnabled = false
        btnStopRecording.isEnabled = true
        tvRecordingStatus.text = "Recording: ON (every ${recordIntervalSeconds}s)"
        tvRecordingStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        
        // Записуємо перший запис одразу
        if (lastStability != 0f) {
            saveToDb(lastStability, lastX, lastY, lastZ)
        }
        
        Toast.makeText(this, "Recording started (interval: ${recordIntervalSeconds}s)", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        btnStartRecording.isEnabled = true
        btnStopRecording.isEnabled = false
        tvRecordingStatus.text = "Recording: OFF"
        tvRecordingStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun deleteOldRecords() {
        val position = spinnerDeleteTime.selectedItemPosition
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val beforeTime = when (position) {
                0 -> now - 3600000L // 1 hour
                1 -> now - 21600000L // 6 hours
                2 -> now - 43200000L // 12 hours
                3 -> now - 86400000L // 24 hours
                4 -> now - 172800000L // 48 hours
                5 -> now - 604800000L // 1 week
                else -> 0L // All
            }
            
            if (beforeTime == 0L) {
                database.stabilityDao().deleteAll()
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ClientActivity, "All records deleted", Toast.LENGTH_SHORT).show()
                }
            } else {
                database.stabilityDao().deleteOldRecords(beforeTime)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@ClientActivity, "Old records deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stability Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for critical stability values"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCriticalNotification(stability: Float) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Critical Stability Alert")
            .setContentText("Stability value: ${"%.2f".format(stability)} (threshold: $criticalThreshold)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1, notification)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
        // Очищаємо handler
        handler.removeCallbacksAndMessages(null)
        bluetoothGatt?.close()
    }
}

