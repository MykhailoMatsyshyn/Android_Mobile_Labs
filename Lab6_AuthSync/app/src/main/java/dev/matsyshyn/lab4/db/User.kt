package dev.matsyshyn.lab4.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Модель користувача для збереження в базі даних
 * 
 * @param id - унікальний ідентифікатор (автоматично генерується)
 * @param username - ім'я користувача (унікальне)
 * @param email - електронна пошта (унікальна)
 * @param passwordHash - хеш пароля (для безпеки зберігаємо хеш, а не сам пароль)
 * @param createdAt - час створення акаунту
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String, // Зберігаємо хеш пароля, а не сам пароль!
    val createdAt: Long = System.currentTimeMillis()
)

