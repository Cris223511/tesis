package com.example.serious_game_usil.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.serious_game_usil.data.User
import com.example.serious_game_usil.data.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class UserRepository private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    suspend fun getCurrentUser(): User = withContext(Dispatchers.IO) {
        // Simular llamada a red/base de datos
        delay(500)

        // En producción, esto vendría de la base de datos o API
        val userId = sharedPreferences.getLong("user_id", 1L)
        val userName = sharedPreferences.getString("user_name", "Jhafet Cánepa") ?: "Usuario"
        val userEmail = sharedPreferences.getString("user_email", "jhafet@example.com") ?: ""
        val userAvatar = sharedPreferences.getString("user_avatar", null)

        User(
            id = userId,
            name = userName,
            email = userEmail,
            avatarUrl = userAvatar ?: "https://ui-avatars.com/api/?name=$userName&background=1976D2&color=fff",
            role = UserRole.PARENT,
            isActive = true
        )
    }

    suspend fun updateUser(user: User): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            sharedPreferences.edit().apply {
                putLong("user_id", user.id)
                putString("user_name", user.name)
                putString("user_email", user.email)
                putString("user_avatar", user.avatarUrl)
                apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateLastNavigationItem(item: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().apply {
            putString("last_navigation_item", item)
            putLong("last_navigation_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    suspend fun getLastNavigationItem(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString("last_navigation_item", null)
    }

    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            sharedPreferences.edit().clear().apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.getLong("user_id", -1L) != -1L
    }

    suspend fun saveAuthToken(token: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().apply {
            putString("auth_token", token)
            putLong("token_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        val token = sharedPreferences.getString("auth_token", null)
        val timestamp = sharedPreferences.getLong("token_timestamp", 0L)

        // Verificar si el token ha expirado (ejemplo: 30 días)
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        if (System.currentTimeMillis() - timestamp > thirtyDaysInMillis) {
            return@withContext null
        }

        token
    }

    companion object {
        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}