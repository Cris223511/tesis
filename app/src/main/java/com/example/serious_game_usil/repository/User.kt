package com.example.serious_game_usil.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.serious_game_usil.data.User
import com.example.serious_game_usil.guards.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class UserRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getCurrentUser(): User {

        return User(
            id = AuthManager.getUserId(),
            nombresApellidos = AuthManager.getNombresApellidos(),
            correo = AuthManager.getCorreo(),
            telefono = AuthManager.getTelefono(),
            tipoDocumento = AuthManager.getTipoDocumento(),
            numDocumento = AuthManager.getNumDocumento(),
            sexo = AuthManager.getSexo(),
            foto = AuthManager.getFoto(),
            roles = AuthManager.getUserRoles()
        )
    }
}