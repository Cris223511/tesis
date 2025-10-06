package com.example.serious_game_usil.repository

import android.util.Log
import com.example.serious_game_usil.`interface`.EmotionApiService
import com.example.serious_game_usil.`interface`.EmotionRetrofitClient
import com.example.serious_game_usil.data.emotion.*
import com.example.serious_game_usil.guards.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class EmotionRepository {
    private val apiService: EmotionApiService = EmotionRetrofitClient.getEmotionApiService()
    private val TAG = "EmotionRepository"

    // Verificar estado del servicio
    suspend fun checkServiceHealth(): Result<HealthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = EmotionRetrofitClient.getEmotionApiServiceNoAuth().checkHealth()
                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Service health check failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Analizar emociones en una imagen
    suspend fun analyzeEmotion(
        imageBase64: String,
        description: String? = null,
        childId: Int? = null
    ): Result<EmotionAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()
                val request = AnalyzeEmotionRequest(
                    image = imageBase64,
                    description = description,
                    childId = childId
                )

                val response = apiService.analyzeEmotion(request)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Analyze emotion error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Editar descripción del análisis
    suspend fun editAnalysis(
        analysisId: String,
        newDescription: String
    ): Result<EmotionAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()
                val request = EditAnalysisRequest(description = newDescription)
                val response = apiService.editAnalysis(analysisId, request)

                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = when (response.code()) {
                        429 -> "Has alcanzado el límite de ediciones diarias (2 por día)"
                        403 -> "No tienes permisos para editar este análisis"
                        404 -> "Análisis no encontrado"
                        else -> "Error al editar: ${response.message()}"
                    }
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Edit analysis error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Eliminar análisis (solo admin/terapeuta)
    suspend fun deleteAnalysis(analysisId: String): Result<DeleteResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()

                // Verificar permisos localmente primero
                val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
                if (!userRoles.any { it in listOf("admin", "administrador", "terapeuta") }) {
                    return@withContext Result.failure(
                        Exception("Solo administradores y terapeutas pueden eliminar análisis")
                    )
                }

                val response = apiService.deleteAnalysis(analysisId)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Delete analysis error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Obtener mis análisis o los de mis hijos (cuidador)
    suspend fun getMyAnalyses(
        page: Int = 1,
        perPage: Int = 20,
        orderBy: String = "created_at",
        orderDirection: String = "desc"
    ): Result<AnalysesListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()
                val response = apiService.getMyAnalyses(page, perPage, orderBy, orderDirection)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get my analyses error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Obtener todos los análisis (solo admin/terapeuta)
    suspend fun getAllAnalyses(
        page: Int = 1,
        perPage: Int = 20,
        userId: Int? = null,
        orderBy: String = "created_at",
        orderDirection: String = "desc"
    ): Result<AnalysesListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()

                // Verificar permisos localmente
                val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
                if (!userRoles.any { it in listOf("admin", "administrador", "terapeuta") }) {
                    return@withContext Result.failure(
                        Exception("Solo administradores y terapeutas pueden ver todos los análisis")
                    )
                }

                val response = apiService.getAllAnalyses(page, perPage, userId, orderBy, orderDirection)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get all analyses error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Obtener análisis por ID
    suspend fun getAnalysisById(analysisId: String): Result<EmotionAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()
                val response = apiService.getAnalysisById(analysisId)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get analysis by ID error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Obtener análisis de un usuario específico (para cuidadores ver análisis de sus hijos)
    suspend fun getUserAnalyses(
        userId: Int,
        page: Int = 1,
        perPage: Int = 20
    ): Result<AnalysesListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                syncTokens()
                val response = apiService.getUserAnalyses(userId, page, perPage)
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get user analyses error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // Función helper para sincronizar tokens
    private fun syncTokens() {
        val currentToken = AuthManager.getAccessToken()
        EmotionRetrofitClient.setAuthToken(currentToken)
    }

    // Función helper para manejar respuestas
    private fun <T> handleResponse(response: Response<T>): Result<T> {
        return if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            val errorMessage = when (response.code()) {
                401 -> "No autorizado. Por favor inicia sesión nuevamente."
                403 -> "No tienes permisos para realizar esta acción."
                404 -> "Recurso no encontrado."
                429 -> "Demasiadas solicitudes. Por favor intenta más tarde."
                500 -> "Error del servidor. Por favor intenta más tarde."
                else -> "Error desconocido: ${response.message()}"
            }
            Log.e(TAG, "API Error: Code=${response.code()}, Message=${response.message()}")
            Result.failure(Exception(errorMessage))
        }
    }

    companion object {
        @Volatile
        private var instance: EmotionRepository? = null

        fun getInstance(): EmotionRepository {
            return instance ?: synchronized(this) {
                instance ?: EmotionRepository().also { instance = it }
            }
        }

        // Verificar si el usuario puede editar análisis
        fun canUserEdit(): Boolean {
            val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
            return userRoles.any {
                it in listOf("admin", "administrador", "cuidador", "terapeuta")
            }
        }

        // Verificar si el usuario puede eliminar análisis
        fun canUserDelete(): Boolean {
            val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
            return userRoles.any {
                it in listOf("admin", "administrador", "terapeuta")
            }
        }

        // Verificar si el usuario puede ver todos los análisis
        fun canUserViewAll(): Boolean {
            val userRoles = AuthManager.getUserRoles().map { it.lowercase() }
            return userRoles.any {
                it in listOf("admin", "administrador", "terapeuta")
            }
        }
    }
}