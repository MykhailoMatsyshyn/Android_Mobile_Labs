package dev.matsyshyn.lab4.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO (Data Access Object) для роботи з користувачами в базі даних
 * 
 * Це інтерфейс, який визначає методи для роботи з таблицею users
 */
@Dao
interface UserDao {
    
    /**
     * Вставка нового користувача
     * OnConflictStrategy.IGNORE - якщо користувач з таким username вже існує, ігноруємо
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long
    
    /**
     * Пошук користувача за ім'ям користувача
     * Повертає User або null, якщо не знайдено
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?
    
    /**
     * Пошук користувача за email
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?
    
    /**
     * Пошук користувача за ID
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?
    
    /**
     * Перевірка, чи існує користувач з таким username
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    suspend fun usernameExists(username: String): Boolean
    
    /**
     * Перевірка, чи існує користувач з таким email
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    suspend fun emailExists(email: String): Boolean
}

