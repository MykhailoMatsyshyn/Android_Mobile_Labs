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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dev.matsyshyn.lab4.auth.AuthManager

class RegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etPasswordConfirm: TextInputEditText
    private lateinit var btnRegister: Button
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var authManager: AuthManager
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

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
        etUsername = findViewById(R.id.etUsername)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        btnRegister = findViewById(R.id.btnRegister)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            performRegister()
        }
        
        btnLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun performRegister() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val passwordConfirm = etPasswordConfirm.text.toString()

        if (!validateInput(username, email, password, passwordConfirm)) {
            return
        }

        showLoading(true)
        hideError()

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                showLoading(false)
                
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        authManager.onAuthSuccess(user)
                        showToast("Реєстрація успішна! Вітаємо, ${user.email}!")
                        navigateToMain()
                    }
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun validateInput(
        username: String,
        email: String,
        password: String,
        passwordConfirm: String
    ): Boolean {
        if (TextUtils.isEmpty(username)) {
            showError("Введіть ім'я користувача")
            etUsername.requestFocus()
            return false
        }

        if (username.length < 3) {
            showError("Ім'я користувача повинно містити мінімум 3 символи")
            etUsername.requestFocus()
            return false
        }

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

        if (password.length < 6) {
            showError("Пароль повинен містити мінімум 6 символів")
            etPassword.requestFocus()
            return false
        }

        if (TextUtils.isEmpty(passwordConfirm)) {
            showError("Підтвердіть пароль")
            etPasswordConfirm.requestFocus()
            return false
        }

        if (password != passwordConfirm) {
            showError("Паролі не співпадають")
            etPasswordConfirm.requestFocus()
            return false
        }

        return true
    }

    private fun handleAuthError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthWeakPasswordException -> 
                "Пароль занадто слабкий. Використовуйте мінімум 6 символів"
            is FirebaseAuthUserCollisionException -> 
                "Користувач з таким email вже існує"
            is FirebaseAuthInvalidCredentialsException -> 
                "Невірний формат email"
            else -> 
                "Помилка реєстрації: ${exception?.message ?: "Невідома помилка"}"
        }
        showError(errorMessage)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        
        btnRegister.alpha = if (show) 0.6f else 1.0f
        btnLogin.alpha = if (show) 0.6f else 1.0f
        btnRegister.isEnabled = !show
        btnLogin.isEnabled = !show
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

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}



