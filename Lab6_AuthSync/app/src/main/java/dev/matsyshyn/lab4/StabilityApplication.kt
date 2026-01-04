package dev.matsyshyn.lab4

import android.app.Application
import com.facebook.stetho.Stetho
import dev.matsyshyn.lab4.db.AppDatabase

class StabilityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Ініціалізація Stetho для перегляду БД
        Stetho.initializeWithDefaults(this)
        
        // Додатково: ініціалізуємо БД для Stetho (опціонально)
        // Це дозволяє Stetho бачити Room базу даних
        val database = AppDatabase.getDatabase(this)
        // Stetho автоматично підхопить SQLite базу даних
    }
}

