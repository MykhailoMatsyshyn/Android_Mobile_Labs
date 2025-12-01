package dev.matsyshyn

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileWriter
import java.io.FileReader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class ClientActivity : AppCompatActivity() {

    // Ті самі UUID, що і на Сервері
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // UI елементи
    private lateinit var tvStatus: TextView
    private lateinit var tvValue: TextView
    private lateinit var tvDirection: TextView
    private lateinit var btnScan: MaterialButton
    private lateinit var tvMin: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvMax: TextView
    private lateinit var tvTrend: TextView
    private lateinit var tvHistoryInfo: TextView
    private lateinit var chart: LineChart
    private lateinit var tvFoundDevices: TextView
    private lateinit var cardFoundDevices: MaterialCardView
    private lateinit var btnExport: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var tvChangeRate: TextView
    
    // Список знайдених пристроїв
    private val foundDevices = mutableListOf<String>()

    // Bluetooth
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // Історія вимірювань (максимум 100)
    private val MAX_HISTORY = 100
    private val measurements = mutableListOf<Measurement>()
    private val gson = Gson()
    private val historyFile = "measurements_history.json"

    // Клас для зберігання вимірювання
    data class Measurement(
        val value: Double,
        val direction: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        initViews()
        setupChart()
        loadHistory() // Завантажуємо збережену історію
        
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        btnScan.setOnClickListener {
            startBleScan()
        }
        
        btnExport.setOnClickListener {
            exportData()
        }
        
        btnClear.setOnClickListener {
            clearHistory()
        }
    }
    
    override fun onPause() {
        super.onPause()
        saveHistory() // Зберігаємо історію при паузі
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveHistory() // Зберігаємо історію при закритті
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvClientStatus)
        tvValue = findViewById(R.id.tvMagneticValue)
        tvDirection = findViewById(R.id.tvDirection)
        btnScan = findViewById(R.id.btnScan)
        tvMin = findViewById(R.id.tvMin)
        tvAvg = findViewById(R.id.tvAvg)
        tvMax = findViewById(R.id.tvMax)
        tvTrend = findViewById(R.id.tvTrend)
        tvHistoryInfo = findViewById(R.id.tvHistoryInfo)
        chart = findViewById(R.id.chart)
        tvFoundDevices = findViewById(R.id.tvFoundDevices)
        cardFoundDevices = findViewById(R.id.cardFoundDevices)
        btnExport = findViewById(R.id.btnExport)
        btnClear = findViewById(R.id.btnClear)
        tvChangeRate = findViewById(R.id.tvChangeRate)
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setBackgroundColor(Color.WHITE)
        
        // Налаштування осі X
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.GRAY
        xAxis.textSize = 10f
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                if (measurements.isEmpty()) return ""
                val index = value.toInt().coerceIn(0, measurements.size - 1)
                return dateFormat.format(Date(measurements[index].timestamp))
            }
        }
        
        // Налаштування осі Y
        val yAxis = chart.axisLeft
        yAxis.textColor = Color.GRAY
        yAxis.setDrawGridLines(true)
        yAxis.gridColor = Color.LTGRAY
        yAxis.axisMinimum = 0f
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        
        // Початкові дані
        updateChart()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasPermissions()) return

        tvStatus.text = "Сканування..."
        btnScan.isEnabled = false
        foundDevices.clear()
        cardFoundDevices.visibility = View.VISIBLE
        tvFoundDevices.text = "Шукаємо пристрої з UUID:\n1234...def0\n\nОчікування..."

        val scanner = bluetoothAdapter?.bluetoothLeScanner

        // Фільтр по нашому Service UUID - знайде тільки наш пристрій
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        
        // Таймаут сканування (10 секунд)
        Handler(Looper.getMainLooper()).postDelayed({
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            if (foundDevices.isEmpty()) {
                tvFoundDevices.text = "Пристроїв не знайдено.\nПеревірте, чи запущений сервер."
                btnScan.isEnabled = true
            }
        }, 10000)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            
            // Додаємо до списку, якщо ще немає
            val deviceInfo = "$deviceName\n($deviceAddress)"
            if (!foundDevices.contains(deviceInfo)) {
                foundDevices.add(deviceInfo)
                
                runOnUiThread {
                    val devicesText = if (foundDevices.size == 1) {
                        "✅ Знайдено пристрій:\n\n${foundDevices.joinToString("\n\n")}"
                    } else {
                        "✅ Знайдено пристроїв: ${foundDevices.size}\n\n${foundDevices.joinToString("\n\n")}"
                    }
                    tvFoundDevices.text = devicesText
                }
            }

            tvStatus.text = "Знайдено: $deviceName"

            // Зупиняємо сканування після знаходження
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
            tvStatus.text = "Підключення..."
            bluetoothGatt = device.connectGatt(this@ClientActivity, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                tvStatus.text = "Помилка сканування: $errorCode"
                tvFoundDevices.text = "❌ Помилка сканування: $errorCode"
                btnScan.isEnabled = true
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { tvStatus.text = "Підключено. Пошук сервісів..." }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvStatus.text = "Відключено"
                    btnScan.isEnabled = true
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHAR_UUID)

                if (characteristic != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        gatt.requestMtu(512)
                    }
                    
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)

                    runOnUiThread { tvStatus.text = "Очікування даних..." }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d("BLE_CLIENT", "MTU змінено: $mtu байт, статус: $status")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val dataBytes = characteristic.value
            
            if (dataBytes == null || dataBytes.isEmpty()) {
                Log.e("BLE_CLIENT", "Отримано порожні дані!")
                return
            }
            
            val receivedString = String(dataBytes, Charsets.UTF_8)
            Log.d("BLE_CLIENT", "Отримано: '$receivedString'")

            runOnUiThread {
                try {
                    val json = JSONObject(receivedString)
                    val mag = json.getDouble("magnetic")
                    val dir = json.getString("direction")

                    // Додаємо вимірювання в історію
                    addMeasurement(mag, dir)
                    
                    // Оновлюємо UI
                    updateUI(mag, dir)
                    updateStatistics()
                    updateChart()
                    
                } catch (e: Exception) {
                    tvValue.text = "Error: ${e.message}"
                    Log.e("BLE_JSON", "Помилка парсингу: ${e.message}", e)
                }
            }
        }
    }

    private fun addMeasurement(value: Double, direction: String) {
        measurements.add(Measurement(value, direction))
        
        // Обмежуємо історію до MAX_HISTORY
        if (measurements.size > MAX_HISTORY) {
            measurements.removeAt(0)
        }
        
        // Автозбереження кожні 10 вимірювань
        if (measurements.size % 10 == 0) {
            saveHistory()
        }
    }
    
    // Збереження історії в файл
    private fun saveHistory() {
        try {
            val file = File(filesDir, historyFile)
            val json = gson.toJson(measurements)
            FileWriter(file).use { it.write(json) }
            Log.d("BLE_CLIENT", "Історія збережена: ${measurements.size} вимірювань")
        } catch (e: Exception) {
            Log.e("BLE_CLIENT", "Помилка збереження історії: ${e.message}")
        }
    }
    
    // Завантаження історії з файлу
    private fun loadHistory() {
        try {
            val file = File(filesDir, historyFile)
            if (file.exists()) {
                val json = FileReader(file).readText()
                val type = object : TypeToken<List<Measurement>>() {}.type
                val loaded = gson.fromJson<List<Measurement>>(json, type)
                if (loaded != null) {
                    measurements.clear()
                    measurements.addAll(loaded.takeLast(MAX_HISTORY)) // Беремо останні 100
                    Log.d("BLE_CLIENT", "Історія завантажена: ${measurements.size} вимірювань")
                    updateStatistics()
                    updateChart()
                }
            }
        } catch (e: Exception) {
            Log.e("BLE_CLIENT", "Помилка завантаження історії: ${e.message}")
        }
    }

    private fun updateUI(value: Double, direction: String) {
        tvValue.text = "%.1f мкТл".format(value)
        tvDirection.text = "Напрямок: $direction"
        
        // Розрахунок швидкості зміни
        if (measurements.size >= 2) {
            val current = measurements.last().value
            val previous = measurements[measurements.size - 2].value
            val change = current - previous
            val changeRate = if (change > 0) {
                "↑ +${"%.1f".format(change)} мкТл/с"
            } else if (change < 0) {
                "↓ ${"%.1f".format(change)} мкТл/с"
            } else {
                "→ 0.0 мкТл/с"
            }
            tvChangeRate.text = changeRate
            tvChangeRate.setTextColor(when {
                abs(change) > 10 -> Color.parseColor("#F44336")
                abs(change) > 5 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#4CAF50")
            })
        } else {
            tvChangeRate.text = ""
        }
        
        // Змінюємо колір залежно від значення
        val color = when {
            value > 100 -> Color.RED
            value > 50 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#2196F3")
        }
        tvValue.setTextColor(color)
    }

    private fun updateStatistics() {
        if (measurements.isEmpty()) {
            tvMin.text = "---"
            tvAvg.text = "---"
            tvMax.text = "---"
            tvTrend.text = "---"
            tvHistoryInfo.text = "Історія: 0 / $MAX_HISTORY вимірювань"
            return
        }

        val values = measurements.map { it.value }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val avg = values.average()

        tvMin.text = "%.1f".format(min)
        tvAvg.text = "%.1f".format(avg)
        tvMax.text = "%.1f".format(max)
        val savedCount = if (File(filesDir, historyFile).exists()) measurements.size else 0
        tvHistoryInfo.text = "Історія: ${measurements.size} / $MAX_HISTORY вимірювань${if (savedCount > 0) " (збережено)" else ""}"

        // Визначення тренду (порівнюємо останні 10 вимірювань з попередніми 10)
        val trend = calculateTrend()
        tvTrend.text = trend.text
        tvTrend.setTextColor(trend.color)
    }

    private fun calculateTrend(): Trend {
        if (measurements.size < 20) {
            return Trend("Недостатньо даних", Color.GRAY)
        }

        val recent = measurements.takeLast(10).map { it.value }.average()
        val previous = measurements.dropLast(10).takeLast(10).map { it.value }.average()
        
        val change = recent - previous
        val changePercent = abs(change / previous * 100)

        return when {
            changePercent < 1.0 -> Trend("Стабільний", Color.GRAY)
            change > 0 -> Trend("↑ Зростання (${"%.1f".format(changePercent)}%)", Color.parseColor("#4CAF50"))
            else -> Trend("↓ Спадання (${"%.1f".format(changePercent)}%)", Color.parseColor("#F44336"))
        }
    }

    data class Trend(val text: String, val color: Int)

    private fun updateChart() {
        if (measurements.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val entries = measurements.mapIndexed { index, measurement ->
            Entry(index.toFloat(), measurement.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "Магнітне поле")
        dataSet.color = Color.parseColor("#2196F3")
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(Color.parseColor("#2196F3"))
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#2196F3")
        dataSet.fillAlpha = 50

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.invalidate()
    }

    // Експорт даних в файл
    private fun exportData() {
        if (measurements.isEmpty()) {
            Toast.makeText(this, "Немає даних для експорту", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "magnetic_data_${dateFormat.format(Date())}.json"
            val file = File(getExternalFilesDir(null), fileName)
            
            val exportData = mapOf(
                "export_date" to System.currentTimeMillis(),
                "total_measurements" to measurements.size,
                "data" to measurements.map { 
                    mapOf(
                        "value" to it.value,
                        "direction" to it.direction,
                        "timestamp" to it.timestamp,
                        "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                    )
                }
            )
            
            FileWriter(file).use { it.write(gson.toJson(exportData)) }
            
            Toast.makeText(this, "Дані експортовано: $fileName", Toast.LENGTH_LONG).show()
            Log.d("BLE_CLIENT", "Експортовано ${measurements.size} вимірювань в $fileName")
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка експорту: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("BLE_CLIENT", "Помилка експорту: ${e.message}", e)
        }
    }
    
    // Очищення історії
    private fun clearHistory() {
        measurements.clear()
        saveHistory()
        updateStatistics()
        updateChart()
        tvHistoryInfo.text = "Історія: 0 / $MAX_HISTORY вимірювань"
        Toast.makeText(this, "Історія очищена", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1)
                return false
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 1)
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Дозволи надано", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Потрібні дозволи для роботи BLE", Toast.LENGTH_LONG).show()
            }
        }
    }
}

