package dev.matsyshyn.lab4

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.matsyshyn.lab4.auth.AuthManager

/**
 * Головна активність додатку
 * 
 * Функціонал:
 * 1. Перевірка авторизації користувача
 * 2. Навігація до ServerActivity та ClientActivity
 * 3. Відображення інформації про користувача
 * 4. Вихід з акаунту
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var authManager: AuthManager
    private lateinit var tvUserInfo: TextView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ініціалізація AuthManager
        authManager = AuthManager.getInstance(this)
        
        // Перевірка авторизації
        if (!authManager.isUserLoggedIn()) {
            navigateToLogin()
            return
        }

        // Ініціалізація компонентів
        initViews()
        
        // Налаштування обробників
        setupListeners()
        
        // Відображення інформації про користувача
        displayUserInfo()
    }

    /**
     * Ініціалізація View компонентів
     */
    private fun initViews() {
        tvUserInfo = findViewById(R.id.tvUserInfo)
        btnLogout = findViewById(R.id.btnLogout)
    }

    /**
     * Налаштування обробників подій
     */
    private fun setupListeners() {
        findViewById<Button>(R.id.btnServer).setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnClient).setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
        
        btnLogout.setOnClickListener {
            performLogout()
        }
    }

    /**
     * Відображення інформації про поточного користувача
     */
    private fun displayUserInfo() {
        val userEmail = authManager.getCurrentUserEmail() ?: "Невідомий користувач"
        tvUserInfo.text = "Користувач: $userEmail"
    }

    /**
     * Вихід з акаунту
     */
    private fun performLogout() {
        authManager.signOut()
        Toast.makeText(this, "Ви вийшли з акаунту", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    /**
     * Перехід на екран авторизації
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}