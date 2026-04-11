package com.example.serious_game_usil.`interface`

import com.example.serious_game_usil.data.emotion.*
import retrofit2.Response
import retrofit2.http.*

interface EmotionApiService {

    // Health check
    @GET("api/v1/health")
    suspend fun checkHealth(): Response<HealthResponse>

    // Análisis de emociones
    @POST("api/v1/analyze-emotion")
    suspend fun analyzeEmotion(@Body request: AnalyzeEmotionRequest): Response<EmotionAnalysisResponse>

    @GET("api/v1/session/{session_id}/analysis")
    suspend fun getSessionAnalysis(@Path("session_id") sessionId: Int): Response<EmotionAnalysisResponse>

    @POST("api/v1/sessions/analyses")
    suspend fun getSessionAnalyses(@Body request: SessionAnalysesRequest): Response<SessionAnalysesResponse>

    // Editar análisis (limitado por rol)
    @PUT("api/v1/edit-analysis/{id}")
    suspend fun editAnalysis(
        @Path("id") analysisId: String,
        @Body request: EditAnalysisRequest
    ): Response<EmotionAnalysisResponse>

    // Eliminar análisis (solo Admin)
    @DELETE("api/v1/delete-analysis/{id}")
    suspend fun deleteAnalysis(@Path("id") analysisId: String): Response<DeleteResponse>

    // Obtener mis análisis
    @GET("api/v1/my-analyses")
    suspend fun getMyAnalyses(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("order_by") orderBy: String = "created_at",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<AnalysesListResponse>

    // Obtener todos los análisis (solo Admin)
    @GET("api/v1/all-analyses")
    suspend fun getAllAnalyses(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("user_id") userId: Int? = null,
        @Query("order_by") orderBy: String = "created_at",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<AnalysesListResponse>

    // Obtener análisis por ID
    @GET("api/v1/analysis/{id}")
    suspend fun getAnalysisById(@Path("id") analysisId: String): Response<EmotionAnalysisResponse>

    // Obtener análisis de un usuario específico (para padres ver análisis de sus hijos)
    @GET("api/v1/user/{user_id}/analyses")
    suspend fun getUserAnalyses(
        @Path("user_id") userId: Int,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): Response<AnalysesListResponse>
}
