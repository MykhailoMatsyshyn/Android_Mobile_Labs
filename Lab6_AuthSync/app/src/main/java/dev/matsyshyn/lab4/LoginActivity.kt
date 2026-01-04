package dev.matsyshyn.lab4

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dev.matsyshyn.lab4.auth.AuthManager

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var authManager: AuthManager
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        
        authManager = AuthManager.getInstance(this)
        firebaseAuth = authManager.getFirebaseAuth()
        
        if (authManager.isUserLoggedIn()) {
            navigateToMain()
            return
        }
        
        setupListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            performLogin()
        }
        
        btnRegister.setOnClickListener {
            navigateToRegister()
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateInput(email, password)) {
            return
        }

        showLoading(true)
        hideError()

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        authManager.onAuthSuccess(user)
                        showToast("Вітаємо, ${user.email}!")
                        navigateToMain()
                    }
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (TextUtils.isEmpty(email)) {
            showError("Введіть email")
            etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Невірний формат email")
            etEmail.requestFocus()
            return false
        }

        if (TextUtils.isEmpty(password)) {
            showError("Введіть пароль")
            etPassword.requestFocus()
            return false
        }

        return true
    }

    private fun handleAuthError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> 
                "Користувача з таким email не знайдено"
            is FirebaseAuthInvalidCredentialsException -> 
                "Невірний email або пароль"
            is FirebaseAuthWeakPasswordException -> 
                "Пароль занадто слабкий"
            is FirebaseAuthUserCollisionException -> 
                "Користувач з таким email вже існує"
            else -> 
                "Помилка авторизації: ${exception?.message ?: "Невідома помилка"}"
        }
        showError(errorMessage)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        
        btnLogin.alpha = if (show) 0.6f else 1.0f
        btnRegister.alpha = if (show) 0.6f else 1.0f
        btnLogin.isEnabled = !show
        btnRegister.isEnabled = !show
    }

    private fun showError(message: String) {
        tvError.text = "⚠️ $message"
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}



