package dev.matsyshyn.speedtracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.app.NotificationCompat
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import dev.matsyshyn.speedtracker.AppDatabase
import dev.matsyshyn.speedtracker.TrackingDataDao
import dev.matsyshyn.speedtracker.TrackingDataEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

// Data Class для відправки в Firebase
/*data class TrackingData(
    val timestamp: String,
    val speedKmh: Float,
    val accelMagnitude: Float,
    val latitude: Double,
    val longitude: Double
)*/

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var firebaseDatabase: DatabaseReference
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    // UI Elements
    private lateinit var tvSpeed: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvAccel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var spinnerInterval: Spinner
    private lateinit var spinnerCriticalSpeed: Spinner
    private lateinit var recyclerViewOffline: RecyclerView
    private lateinit var tvOfflineRecordsCount: TextView
    private lateinit var offlineHeaderLayout: View
    private lateinit var ivOfflineExpandArrow: ImageView
    private lateinit var dividerOffline: View
    private lateinit var offlineAdapter: HistoryAdapter
    private lateinit var offlineCardView: androidx.cardview.widget.CardView
    private var isOfflineListExpanded = false

    // Data variables
    private var currentSpeed: Float = 0f
    private var maxSpeed: Float = 0f
    private var currentAccel: Float = 0f
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var currentInterval: Int = 10 // в секундах

    // Timer
    private var sendJob: Job? = null
    private var networkCheckJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // List for CSV export
    private val historyList = mutableListOf<TrackingData>()
    
    // SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "SpeedTrackerPrefs"
    private val KEY_INTERVAL = "send_interval_seconds"
    private val KEY_CRITICAL_SPEED = "critical_speed_threshold"
    
    // Notification
    private lateinit var notificationManager: NotificationManager
    private val CHANNEL_ID = "speed_tracker_critical"
    private val NOTIFICATION_ID = 1
    private var criticalSpeedThreshold: Float = 100f // Дефолт 100 км/год
    private var lastNotificationTime: Long = 0
    private val NOTIFICATION_COOLDOWN = 30000L // 30 секунд між сповіщеннями
    
    // Room Database для офлайн-режиму
    private lateinit var database: AppDatabase
    private lateinit var trackingDataDao: TrackingDataDao
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Ініціалізація UI
        tvSpeed = findViewById<TextView>(R.id.tvSpeed)
        tvMaxSpeed = findViewById<TextView>(R.id.tvMaxSpeed)
        tvAccel = findViewById<TextView>(R.id.tvAccel)
        tvStatus = findViewById<TextView>(R.id.tvStatus)
        statusIndicator = findViewById<View>(R.id.statusIndicator)
        spinnerInterval = findViewById<Spinner>(R.id.spinnerInterval)
        spinnerCriticalSpeed = findViewById<Spinner>(R.id.spinnerCriticalSpeed)

        // --- ДОДАНО: Кнопка переходу на Історію ---
        val btnHistory = findViewById<Button>(R.id.btnOpenHistory)
        btnHistory.setOnClickListener {
            val intent = android.content.Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
        // ------------------------------------------

        // Ініціалізація UI для офлайн-даних
        offlineCardView = findViewById<androidx.cardview.widget.CardView>(R.id.offlineCardView)
        recyclerViewOffline = findViewById<RecyclerView>(R.id.recyclerViewOffline)
        tvOfflineRecordsCount = findViewById<TextView>(R.id.tvOfflineRecordsCount)
        offlineHeaderLayout = findViewById<View>(R.id.offlineHeaderLayout)
        ivOfflineExpandArrow = findViewById<ImageView>(R.id.ivOfflineExpandArrow)
        dividerOffline = findViewById<View>(R.id.dividerOffline)
        
        // Налаштування RecyclerView для офлайн-даних
        offlineAdapter = HistoryAdapter(emptyList())
        recyclerViewOffline.layoutManager = LinearLayoutManager(this)
        recyclerViewOffline.adapter = offlineAdapter
        
        // Обробник кліку для розгортання/згортання списку офлайн-даних
        offlineHeaderLayout.setOnClickListener {
            toggleOfflineList()
        }
        
        // Завантаження офлайн-даних
        loadOfflineData()

        // 2. Ініціалізація SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentInterval = sharedPreferences.getInt(KEY_INTERVAL, 10) // Дефолт 10 секунд
        criticalSpeedThreshold = sharedPreferences.getFloat(KEY_CRITICAL_SPEED, 100f) // Дефолт 100 км/год

        // 3. Ініціалізація NotificationManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 4. Налаштування Spinner для вибору інтервалу
        setupIntervalSpinner()
        
        // 5. Налаштування Spinner для порогу критичної швидкості
        setupCriticalSpeedSpinner()

        // 6. Ініціалізація Room Database
        database = AppDatabase.getDatabase(this)
        trackingDataDao = database.trackingDataDao()
        
        // 7. Ініціалізація Firebase (для перевірки статусу)
        firebaseDatabase = FirebaseDatabase.getInstance("https://speedtrackerlab3-default-rtdb.europe-west1.firebasedatabase.app").getReference("speed_tracking")
        
        // 8. Запуск синхронізації несинхронізованих даних через Service
        startSyncJob()
        
        // 9. Запуск DataSyncService для синхронізації офлайн-даних
        DataSyncService.syncPending(this)

        // 7. Ініціалізація сенсорів
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        checkPermissions()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Критичні значення швидкості",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сповіщення про перевищення критичної швидкості"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun setupIntervalSpinner() {
        val intervals = arrayOf(
            "5 сек",
            "10 сек",
            "15 сек",
            "30 сек",
            "60 сек",
            "120 сек"
        )
        
        val intervalValues = arrayOf(5, 10, 15, 30, 60, 120)
        
        // Кастомний адаптер з правильними кольорами для dropdown
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, intervals) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                // Вибраний елемент - чорний текст
                view.setTextColor(resources.getColor(android.R.color.black, null))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                // Встановлюємо білий фон та чорний текст для кращої видимості
                view.setBackgroundColor(resources.getColor(android.R.color.white, null))
                view.setTextColor(resources.getColor(android.R.color.black, null))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInterval.adapter = adapter
        
        // Встановити поточний вибраний інтервал
        val currentIndex = intervalValues.indexOf(currentInterval)
        if (currentIndex >= 0) {
            spinnerInterval.setSelection(currentIndex)
        } else {
            // Якщо не знайдено, встановити 10 сек (індекс 1)
            spinnerInterval.setSelection(1)
            currentInterval = 10
        }
        
        spinnerInterval.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newInterval = intervalValues[position]
                if (newInterval != currentInterval) {
                    currentInterval = newInterval
                    sharedPreferences.edit().putInt(KEY_INTERVAL, currentInterval).apply()
                    
                    // Перезапустити цикл відправки з новим інтервалом
                    sendJob?.cancel()
                    startSendingDataLoop()
                    
                    Toast.makeText(this@MainActivity, "Інтервал змінено на $currentInterval сек", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun setupCriticalSpeedSpinner() {
        val speeds = arrayOf(
            "10 км/год (тест)",
            "50 км/год",
            "60 км/год",
            "70 км/год",
            "80 км/год",
            "90 км/год",
            "100 км/год",
            "120 км/год",
            "130 км/год"
        )
        
        val speedValues = arrayOf(10f, 50f, 60f, 70f, 80f, 90f, 100f, 120f, 130f)
        
        // Кастомний адаптер з правильними кольорами для dropdown
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, speeds) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                // Вибраний елемент - чорний текст
                view.setTextColor(resources.getColor(android.R.color.black, null))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                // Встановлюємо білий фон та чорний текст для кращої видимості
                view.setBackgroundColor(resources.getColor(android.R.color.white, null))
                view.setTextColor(resources.getColor(android.R.color.black, null))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCriticalSpeed.adapter = adapter
        
        // Встановити поточний вибраний поріг
        val currentIndex = speedValues.indexOf(criticalSpeedThreshold)
        if (currentIndex >= 0) {
            spinnerCriticalSpeed.setSelection(currentIndex)
        } else {
            // Якщо не знайдено, встановити 100 км/год (індекс 6)
            spinnerCriticalSpeed.setSelection(6)
            criticalSpeedThreshold = 100f
        }
        
        spinnerCriticalSpeed.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newThreshold = speedValues[position]
                if (newThreshold != criticalSpeedThreshold) {
                    criticalSpeedThreshold = newThreshold
                    sharedPreferences.edit().putFloat(KEY_CRITICAL_SPEED, criticalSpeedThreshold).apply()
                    Toast.makeText(this@MainActivity, "Поріг критичної швидкості: ${criticalSpeedThreshold.toInt()} км/год", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            startTracking()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
            // ДОДАЙ ЦЕЙ РЯДОК для роботи в приміщенні:
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Виконуємо початкову перевірку інтернету
        checkNetworkStatus()

        startSendingDataLoop()
    }

    private fun startSendingDataLoop() {
        sendJob?.cancel()
        sendJob = scope.launch {
            while (isActive) {
                sendDataToFirebase()
                delay(currentInterval * 1000L) // Використовуємо поточний інтервал
            }
        }
        
        // Запускаємо періодичну перевірку інтернету з тим самим інтервалом
        startNetworkCheckLoop()
    }
    
    private fun startNetworkCheckLoop() {
        networkCheckJob?.cancel()
        networkCheckJob = scope.launch {
            while (isActive) {
                checkNetworkStatus()
                delay(currentInterval * 1000L) // Перевіряємо кожні N секунд (де N = currentInterval)
            }
        }
    }
    
    private fun checkNetworkStatus() {
        val hasInternet = isNetworkAvailable()
        
        if (hasInternet) {
            // Оновлюємо статус, якщо є інтернет (але не перезаписуємо, якщо дані щойно відправлені)
            // Статус буде оновлено в sendDataToFirebase, тому тут просто перевіряємо
            // Якщо статус показує офлайн - оновлюємо на онлайн
            if (tvStatus.text.contains("Офлайн") || tvStatus.text.contains("офлайн")) {
                tvStatus.text = "Онлайн\n(очікування даних)"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                val blueColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                ViewCompat.setBackgroundTintList(statusIndicator, ColorStateList.valueOf(blueColor))
            }
        } else {
            // Оновлюємо статус, якщо немає інтернету
            if (!tvStatus.text.contains("Офлайн") && !tvStatus.text.contains("офлайн")) {
                tvStatus.text = "Офлайн режим\n(очікування з'єднання)"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                val orangeColor = ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                ViewCompat.setBackgroundTintList(statusIndicator, ColorStateList.valueOf(orangeColor))
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    private fun sendDataToFirebase() {
        // ВИПРАВЛЕНО: Locale.US для стабільного формату дати
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val data = TrackingData(
            timestamp = timestamp,
            speedKmh = currentSpeed,
            accelMagnitude = currentAccel,
            latitude = currentLat,
            longitude = currentLon,
            intervalSeconds = currentInterval
        )

        historyList.add(data)

        // Відправляємо дані через Service
        DataSyncService.sendData(this, data)
        
        // Оновлюємо статус на UI
        if (isNetworkAvailable()) {
            tvStatus.text = "Відправка даних...\n($timestamp)"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            val blueColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            ViewCompat.setBackgroundTintList(statusIndicator, ColorStateList.valueOf(blueColor))
        } else {
            tvStatus.text = "Офлайн режим\n(збережено локально)"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            val orangeColor = ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            ViewCompat.setBackgroundTintList(statusIndicator, ColorStateList.valueOf(orangeColor))
        }
        
        // Оновлюємо список офлайн-даних
        loadOfflineData()
    }
    
    // Метод залишається для сумісності, але тепер зберігання відбувається в Service
    private fun saveToLocalCache(data: TrackingData) {
        // Тепер зберігання відбувається в DataSyncService
        // Цей метод залишається для сумісності, якщо потрібно
    }
    
    private fun loadOfflineData() {
        syncScope.launch {
            try {
                val unsyncedEntities = trackingDataDao.getUnsyncedDataList()
                val offlineDataList = unsyncedEntities.map { entity ->
                    TrackingData(
                        timestamp = entity.timestamp,
                        speedKmh = entity.speedKmh,
                        accelMagnitude = entity.accelMagnitude,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        intervalSeconds = entity.intervalSeconds
                    )
                }
                
                withContext(Dispatchers.Main) {
                    offlineAdapter.updateData(offlineDataList.reversed()) // Нові записи вгорі
                    tvOfflineRecordsCount.text = "${offlineDataList.size} записів"
                    
                    // Показуємо/ховаємо CardView в залежності від наявності даних
                    if (offlineDataList.isEmpty()) {
                        offlineCardView.visibility = View.GONE
                    } else {
                        offlineCardView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun toggleOfflineList() {
        isOfflineListExpanded = !isOfflineListExpanded
        
        if (isOfflineListExpanded) {
            recyclerViewOffline.visibility = View.VISIBLE
            dividerOffline.visibility = View.VISIBLE
            ivOfflineExpandArrow.rotation = 180f
        } else {
            recyclerViewOffline.visibility = View.GONE
            dividerOffline.visibility = View.GONE
            ivOfflineExpandArrow.rotation = 0f
        }
    }
    
    private fun startSyncJob() {
        syncScope.launch {
            while (isActive) {
                if (isNetworkAvailable()) {
                    // Викликаємо Service для синхронізації
                    withContext(Dispatchers.Main) {
                        DataSyncService.syncPending(this@MainActivity)
                    }
                    // Оновлюємо список офлайн-даних після синхронізації
                    delay(2000) // Затримка для завершення синхронізації
                    withContext(Dispatchers.Main) {
                        loadOfflineData()
                    }
                }
                delay(30000) // Перевіряємо кожні 30 секунд
            }
        }
    }
    
    private suspend fun syncPendingData() {
        // Тепер синхронізація відбувається через DataSyncService
        // Цей метод викликає Service для синхронізації
        withContext(Dispatchers.Main) {
            DataSyncService.syncPending(this@MainActivity)
            // Оновлюємо список офлайн-даних після синхронізації
            delay(2000) // Невелика затримка для завершення синхронізації
            loadOfflineData()
        }
    }

    // --- Sensor Logic ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            currentAccel = sqrt(x * x + y * y + z * z)
            // ВИПРАВЛЕНО: Locale.US
            tvAccel.text = String.format(Locale.US, "%.2f", currentAccel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Location Logic ---
    override fun onLocationChanged(location: Location) {
        val speedMs = location.speed
        currentSpeed = speedMs * 3.6f

        currentLat = location.latitude
        currentLon = location.longitude

        if (currentSpeed > maxSpeed) {
            maxSpeed = currentSpeed
        }

        // Оновлюємо статус коли GPS отримав локацію
        if (tvStatus.text.toString().contains("Очікування GPS")) {
            tvStatus.text = "GPS активний"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            val blueColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            ViewCompat.setBackgroundTintList(statusIndicator, ColorStateList.valueOf(blueColor))
        }

        // Перевірка критичної швидкості
        checkCriticalSpeed()

        // ВИПРАВЛЕНО: Locale.US
        tvSpeed.text = String.format(Locale.US, "%.1f", currentSpeed)
        tvMaxSpeed.text = String.format(Locale.US, "%.1f км/год", maxSpeed)
    }
    
    private fun checkCriticalSpeed() {
        if (currentSpeed > criticalSpeedThreshold) {
            val currentTime = System.currentTimeMillis()
            // Показуємо сповіщення не частіше ніж раз на 30 секунд
            if (currentTime - lastNotificationTime > NOTIFICATION_COOLDOWN) {
                showCriticalSpeedNotification(currentSpeed)
                lastNotificationTime = currentTime
            }
        }
    }
    
    private fun showCriticalSpeedNotification(speed: Float) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Критична швидкість!")
            .setContentText("Швидкість перевищена: ${String.format(Locale.US, "%.1f", speed)} км/год")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Увага! Поточна швидкість ${String.format(Locale.US, "%.1f", speed)} км/год перевищує встановлений поріг ${String.format(Locale.US, "%.0f", criticalSpeedThreshold)} км/год."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun exportToCsv() {
        val fileName = "tracking_history.csv"
        val content = StringBuilder("Timestamp,Speed,Accel,Lat,Lon\n")

        for (item in historyList) {
            content.append("${item.timestamp},${item.speedKmh},${item.accelMagnitude},${item.latitude},${item.longitude}\n")
        }

        try {
            val file = File(getExternalFilesDir(null), fileName)
            FileOutputStream(file).use {
                it.write(content.toString().toByteArray())
            }
            Toast.makeText(this, "Збережено: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка збереження: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Оновлюємо список офлайн-даних при поверненні на екран
        loadOfflineData()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        sendJob?.cancel()
        networkCheckJob?.cancel()
        syncScope.cancel()
    }
}