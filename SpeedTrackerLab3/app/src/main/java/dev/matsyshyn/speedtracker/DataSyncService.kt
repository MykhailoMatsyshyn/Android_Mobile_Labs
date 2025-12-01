package dev.matsyshyn.speedtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DataSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var firebaseDatabase: DatabaseReference
    private lateinit var database: AppDatabase
    private lateinit var trackingDataDao: TrackingDataDao
    
    private val CHANNEL_ID = "data_sync_service"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        
        // Ініціалізація Firebase
        firebaseDatabase = FirebaseDatabase.getInstance("https://speedtrackerlab3-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("speed_tracking")
        
        // Ініціалізація Room Database
        database = AppDatabase.getDatabase(this)
        trackingDataDao = database.trackingDataDao()
        
        // Створення каналу сповіщень
        createNotificationChannel()
        
        // Запуск як foreground service
        startForeground(NOTIFICATION_ID, createNotification("Синхронізація даних..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND_DATA -> {
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_TRACKING_DATA, TrackingData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_TRACKING_DATA) as? TrackingData
                }
                data?.let {
                    sendDataToFirebase(it)
                }
            }
            ACTION_SYNC_PENDING -> {
                syncPendingData()
            }
        }
        
        return START_STICKY // Service перезапускається при збої
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendDataToFirebase(data: TrackingData) {
        serviceScope.launch {
            try {
                if (isNetworkAvailable()) {
                    // Спробувати відправити на Firebase
                    firebaseDatabase.push().setValue(data)
                        .addOnSuccessListener {
                            updateNotification("Дані відправлено: ${data.timestamp}")
                        }
                        .addOnFailureListener { exception ->
                            // Якщо не вдалося відправити, зберігаємо локально
                            saveToLocalCache(data)
                            updateNotification("Помилка відправки, збережено локально")
                        }
                } else {
                    // Немає інтернету - зберігаємо локально
                    saveToLocalCache(data)
                    updateNotification("Офлайн режим, збережено локально")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                saveToLocalCache(data)
                updateNotification("Помилка: ${e.message}")
            }
        }
    }

    private fun saveToLocalCache(data: TrackingData) {
        serviceScope.launch {
            try {
                val entity = TrackingDataEntity(
                    timestamp = data.timestamp,
                    speedKmh = data.speedKmh,
                    accelMagnitude = data.accelMagnitude,
                    latitude = data.latitude,
                    longitude = data.longitude,
                    intervalSeconds = data.intervalSeconds,
                    synced = false
                )
                trackingDataDao.insert(entity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun syncPendingData() {
        serviceScope.launch {
            try {
                if (!isNetworkAvailable()) {
                    updateNotification("Немає інтернету для синхронізації")
                    return@launch
                }

                val unsyncedData = trackingDataDao.getUnsyncedDataList()
                
                if (unsyncedData.isEmpty()) {
                    updateNotification("Всі дані синхронізовані")
                    // Видаляємо старі синхронізовані дані
                    trackingDataDao.deleteSyncedData()
                    return@launch
                }

                updateNotification("Синхронізація ${unsyncedData.size} записів...")
                
                var syncedCount = 0
                for (entity in unsyncedData) {
                    val data = TrackingData(
                        timestamp = entity.timestamp,
                        speedKmh = entity.speedKmh,
                        accelMagnitude = entity.accelMagnitude,
                        latitude = entity.latitude,
                        longitude = entity.longitude,
                        intervalSeconds = entity.intervalSeconds
                    )
                    
                    firebaseDatabase.push().setValue(data)
                        .addOnSuccessListener {
                            serviceScope.launch {
                                trackingDataDao.markAsSynced(entity.id)
                                syncedCount++
                                updateNotification("Синхронізовано: $syncedCount/${unsyncedData.size}")
                            }
                        }
                        .addOnFailureListener {
                            // Якщо не вдалося, спробуємо наступного разу
                        }
                    
                    delay(500) // Невелика затримка між запитами
                }
                
                if (syncedCount == unsyncedData.size) {
                    updateNotification("Синхронізацію завершено")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Помилка синхронізації: ${e.message}")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Синхронізація даних",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сповіщення про синхронізацію даних"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Speed Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_SEND_DATA = "dev.matsyshyn.speedtracker.SEND_DATA"
        const val ACTION_SYNC_PENDING = "dev.matsyshyn.speedtracker.SYNC_PENDING"
        const val EXTRA_TRACKING_DATA = "tracking_data"

        fun sendData(context: Context, data: TrackingData) {
            val intent = Intent(context, DataSyncService::class.java).apply {
                action = ACTION_SEND_DATA
                putExtra(EXTRA_TRACKING_DATA, data as java.io.Serializable)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun syncPending(context: Context) {
            val intent = Intent(context, DataSyncService::class.java).apply {
                action = ACTION_SYNC_PENDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

