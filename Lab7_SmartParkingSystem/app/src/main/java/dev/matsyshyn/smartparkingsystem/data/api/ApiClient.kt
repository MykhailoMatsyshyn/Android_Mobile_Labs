package dev.matsyshyn.smartparkingsystem.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Railway деплой: https://web-production-ca790.up.railway.app
    // Локальна розробка: http://192.168.0.104:5000 (для реального пристрою)
    // Емулятор: http://10.0.2.2:5000
    private const val BASE_URL = "https://web-production-ca790.up.railway.app/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val sensorApiService: SensorApiService = retrofit.create(SensorApiService::class.java)
    val deviceApiService: dev.matsyshyn.smartparkingsystem.data.api.DeviceApiService = retrofit.create(dev.matsyshyn.smartparkingsystem.data.api.DeviceApiService::class.java)
}

