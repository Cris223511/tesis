package com.example.serious_game_usil.guards

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.serious_game_usil.`interface`.EmotionRetrofitClient
import com.example.serious_game_usil.network.RetrofitClient


object AuthManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_ROLES = "user_roles"
    private const val KEY_NOMBRES_APELLIDOS = "nombres_apellidos"
    private const val KEY_CORREO = "correo"
    private const val KEY_TELEFONO = "telefono"
    private const val KEY_TIPO_DOCUMENTO = "tipo_documento"
    private const val KEY_NUM_DOCUMENTO = "num_documento"
    private const val KEY_SEXO = "sexo"
    private const val KEY_FOTO = "foto"
    private const val KEY_ROLE_IDS = "role_ids"
    private const val KEY_PROTECTED_ROUTE = "protected_route"
    private const val KEY_IS_AUTHENTICATED = "is_authenticated"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAuthData(
        accessToken: String,
        refreshToken: String,
        userId: Int,
        nombresApellidos: String,
        correo: String,
        telefono: String,
        tipoDocumento: String,
        numDocumento: String,
        sexo: String,
        foto: String?,
        roles: List<String>,
        roleIds: List<Int>,
        protectedRoute: String
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putInt(KEY_USER_ID, userId)
            putString(KEY_NOMBRES_APELLIDOS, nombresApellidos)
            putString(KEY_CORREO, correo)
            putString(KEY_TELEFONO, telefono)
            putString(KEY_TIPO_DOCUMENTO, tipoDocumento)
            putString(KEY_NUM_DOCUMENTO, numDocumento)
            putString(KEY_SEXO, sexo)
            putString(KEY_FOTO, foto ?: "")
            putString(KEY_USER_ROLES, gson.toJson(roles))
            putString(KEY_ROLE_IDS, gson.toJson(roleIds))
            putString(KEY_PROTECTED_ROUTE, protectedRoute)
            putBoolean(KEY_IS_AUTHENTICATED, true)
            apply()
        }

        // Actualizar token en ambos clientes Retrofit
        RetrofitClient.setAuthToken(accessToken)
        EmotionRetrofitClient.setAuthToken(accessToken)
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)

    fun getUserRoles(): List<String> {
        val rolesJson = prefs.getString(KEY_USER_ROLES, "[]")
        return try {
            gson.fromJson(rolesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getNombresApellidos(): String = prefs.getString(KEY_NOMBRES_APELLIDOS, "") ?: ""

    fun getCorreo(): String = prefs.getString(KEY_CORREO, "") ?: ""

    fun getTelefono(): String = prefs.getString(KEY_TELEFONO, "") ?: ""

    fun getTipoDocumento(): String = prefs.getString(KEY_TIPO_DOCUMENTO, "") ?: ""

    fun getNumDocumento(): String = prefs.getString(KEY_NUM_DOCUMENTO, "") ?: ""

    fun getSexo(): String = prefs.getString(KEY_SEXO, "") ?: ""

    fun getFoto(): String? = prefs.getString(KEY_FOTO, null)

    fun getRoleIds(): List<Int> {
        val roleIdsJson = prefs.getString(KEY_ROLE_IDS, "[]")
        return try {
            gson.fromJson(roleIdsJson, object : TypeToken<List<Int>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getProtectedRoute(): String = prefs.getString(KEY_PROTECTED_ROUTE, "/dashboard") ?: "/dashboard"

    fun isAuthenticated(): Boolean = prefs.getBoolean(KEY_IS_AUTHENTICATED, false)

    fun hasRole(role: String): Boolean = getUserRoles().contains(role)

    fun hasAnyRole(vararg roles: String): Boolean = roles.any { hasRole(it) }

    fun canAccessRoute(route: String): Boolean {
        val userRoles = getUserRoles()
        val normalizedRoles = userRoles.map { it.lowercase() }

        return when {
            // Rutas de admin
            route.contains("/admin/dashboard") ->
                normalizedRoles.any { it in listOf("admin", "administrador") }
            route.contains("/admin/list-user") ->

                normalizedRoles.any { it in listOf("admin", "administrador") }

            route.contains("/admin/list-roles") ->

                normalizedRoles.any { it in listOf("admin", "administrador") }
            // Rutas de padre
            route.contains("/padre/dashboard") || route.contains("/parent/dashboard") ->
                normalizedRoles.any { it in listOf("cuidador", "cuidador", "parent", "admin", "administrador") }

            // Rutas de hijo/estudiante
            route.contains("/paciente") || route.contains("/student") ->
                normalizedRoles.any { it in listOf("paciente", "paciente", "student", "estudiante") }



            // Rutas de especialista
            route.contains("/terapeuta") || route.contains("/specialist") ->
                normalizedRoles.any { it in listOf("terapeuta", "terapeuta") }


            // Ruta de análisis de emociones - para cuidadores, terapeutas, administradores y pacientes con permisos
            route.contains("/emotion-analysis") ->
                normalizedRoles.any { it in listOf("admin", "administrador", "cuidador", "terapeuta", "paciente") }

            // ruta globales
            route.contains("/profile") ->
                normalizedRoles.any { it in listOf("admin", "administrador", "paciente", "cuidador") }
            route == "/dashboard" -> isAuthenticated()

            // Por defecto, permitir si está autenticado
            else -> isAuthenticated()
        }
    }

    fun updateAccessToken(newToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, newToken).apply()
        RetrofitClient.setAuthToken(newToken)
        EmotionRetrofitClient.setAuthToken(newToken)
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
        RetrofitClient.setAuthToken(accessToken)
        EmotionRetrofitClient.setAuthToken(accessToken)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        RetrofitClient.setAuthToken(null)
        EmotionRetrofitClient.setAuthToken(null)
    }

    fun logout() {
        clearSession()
    }
}
