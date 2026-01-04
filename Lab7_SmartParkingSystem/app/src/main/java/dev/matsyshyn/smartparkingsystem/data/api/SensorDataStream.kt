package dev.matsyshyn.smartparkingsystem.data.api

import com.google.gson.Gson
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import dev.matsyshyn.smartparkingsystem.data.api.SensorDataResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * SSE (Server-Sent Events) клієнт для потокової передачі даних сенсорів
 */
class SensorDataStream(
    // Railway деплой: https://web-production-ca790.up.railway.app
    // Локальна розробка: http://192.168.0.104:5000 (для реального пристрою)
    // Емулятор: http://10.0.2.2:5000
    private val baseUrl: String = "https://web-production-ca790.up.railway.app"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Без обмеження для streaming
        .build()
    
    private val gson = Gson()
    private var eventSource: EventSource? = null
    
    /**
     * Підписується на потокову передачу даних сенсорів
     */
    fun streamSensorData(): Flow<SensorData> = callbackFlow {
        val request = Request.Builder()
            .url("$baseUrl/api/sensor-data/stream")
            .build()
        
        val factory = EventSources.createFactory(client)
        
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                android.util.Log.d("SensorDataStream", "✅ SSE з'єднання відкрито: ${response.code}")
            }
            
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                try {
                    android.util.Log.d("SensorDataStream", "📡 Отримано SSE подію: type=$type, data length=${data.length}")
                    // Парсимо JSON дані
                    // SSE формат: "data: {...}" або просто "{...}"
                    val jsonData = if (data.startsWith("data: ")) {
                        data.substring(6) // Видаляємо "data: "
                    } else {
                        data
                    }
                    val response = gson.fromJson(jsonData, SensorDataResponse::class.java)
                    val sensorData = response.toSensorData()
                    android.util.Log.d("SensorDataStream", "✅ Парсено дані: free_spots=${sensorData.freeSpots}, co=${sensorData.coLevel}")
                    trySend(sensorData)
                } catch (e: Exception) {
                    android.util.Log.e("SensorDataStream", "❌ Помилка парсингу SSE даних: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            
            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("SensorDataStream", "🔌 SSE з'єднання закрито")
                close()
            }
            
            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                android.util.Log.e("SensorDataStream", "❌ Помилка SSE: ${t?.message}, response code: ${response?.code}")
                close(t ?: Exception("Unknown error"))
            }
        })
        
        awaitClose {
            eventSource?.cancel()
            eventSource = null
        }
    }
    
    fun close() {
        eventSource?.cancel()
        eventSource = null
    }
}

