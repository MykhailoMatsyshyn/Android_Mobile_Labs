package dev.matsyshyn.lab4.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Менеджер авторизації для управління користувачами
 * 
 * Що він робить:
 * 1. Зберігає інформацію про поточного користувача
 * 2. Перевіряє, чи користувач авторизований
 * 3. Надає доступ до Firebase Auth
 */
class AuthManager private constructor(context: Context) {
    
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        
        @Volatile
        private var INSTANCE: AuthManager? = null
        
        /**
         * Отримати єдиний екземпляр AuthManager (Singleton pattern)
         */
        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Отримати поточного користувача з Firebase
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }
    
    /**
     * Перевірити, чи користувач авторизований
     */
    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }
    
    /**
     * Отримати ID поточного користувача
     */
    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }
    
    /**
     * Отримати email поточного користувача
     */
    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }
    
    /**
     * Зберегти інформацію про користувача локально
     */
    private fun saveUserInfo(user: FirebaseUser) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, user.uid)
            putString(KEY_USER_EMAIL, user.email)
            apply()
        }
    }
    
    /**
     * Отримати збережений ID користувача (навіть якщо Firebase не доступний)
     */
    fun getSavedUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    /**
     * Отримати збережений email користувача
     */
    fun getSavedUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, "") ?: ""
    }
    
    /**
     * Вийти з акаунту
     */
    fun signOut() {
        firebaseAuth.signOut()
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * Отримати FirebaseAuth для прямого доступу (якщо потрібно)
     */
    fun getFirebaseAuth(): FirebaseAuth {
        return firebaseAuth
    }
    
    /**
     * Обробка успішної авторизації - збереження даних
     */
    fun onAuthSuccess(user: FirebaseUser) {
        saveUserInfo(user)
    }
}

