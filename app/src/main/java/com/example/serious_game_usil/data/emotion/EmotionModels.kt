package com.example.serious_game_usil.data.emotion

import com.google.gson.annotations.SerializedName
import java.util.Date

// Response de health check
data class HealthResponse(
    val status: String,
    val message: String
)

// Request para análisis de emociones
data class AnalyzeEmotionRequest(
    val image: String,  // Base64 encoded image
    val description: String? = null,
    @SerializedName("child_id")
    val childId: Int? = null  // ID del niño si es análisis para hijo
)

// Response de análisis de emociones
data class EmotionAnalysisResponse(
    val id: String,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("child_id")
    val childId: Int?,
    @SerializedName("child_name")
    val childName: String?,
    val image: String,
    val description: String?,
    val results: List<EmotionResult>,
    @SerializedName("dominant_emotion")
    val dominantEmotion: String,
    @SerializedName("confidence_score")
    val confidenceScore: Float,
    @SerializedName("created_at")
    val createdAt: Date,
    @SerializedName("updated_at")
    val updatedAt: Date?,
    @SerializedName("can_edit")
    val canEdit: Boolean,
    @SerializedName("can_delete")
    val canDelete: Boolean
)

// Resultado individual de emociones
data class EmotionResult(
    @SerializedName("face_index")
    val faceIndex: Int,
    val emotions: EmotionScores,
    @SerializedName("dominant_emotion")
    val dominantEmotion: String,
    @SerializedName("face_location")
    val faceLocation: FaceLocation?
)

// Scores de emociones
data class EmotionScores(
    val angry: Float,
    val disgust: Float,
    val fear: Float,
    val happy: Float,
    val neutral: Float,
    val sad: Float,
    val surprise: Float
)

// Ubicación del rostro en la imagen
data class FaceLocation(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

// Request para editar análisis
data class EditAnalysisRequest(
    val description: String
)

// Response de eliminación
data class DeleteResponse(
    val message: String,
    val success: Boolean
)

// Response de lista de análisis
data class AnalysesListResponse(
    val analyses: List<EmotionAnalysisResponse>,
    val pagination: PaginationInfo,
    @SerializedName("rate_limit")
    val rateLimit: RateLimitInfo?
)

// Información de paginación
data class PaginationInfo(
    val page: Int,
    @SerializedName("per_page")
    val perPage: Int,
    val total: Int,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("has_next")
    val hasNext: Boolean,
    @SerializedName("has_prev")
    val hasPrev: Boolean
)

// Información de rate limiting
data class RateLimitInfo(
    @SerializedName("edits_remaining")
    val editsRemaining: Int?,
    @SerializedName("edits_max")
    val editsMax: Int?,
    @SerializedName("reset_at")
    val resetAt: Date?
)

// Estados de emociones para UI
enum class EmotionType(val displayName: String, val color: String) {
    ANGRY("Enojado", "#FF4444"),
    DISGUST("Disgusto", "#9C27B0"),
    FEAR("Miedo", "#FF9800"),
    HAPPY("Feliz", "#4CAF50"),
    NEUTRAL("Neutral", "#607D8B"),
    SAD("Triste", "#2196F3"),
    SURPRISE("Sorpresa", "#FFEB3B");

    companion object {
        fun fromString(value: String): EmotionType {
            return values().find { it.name.equals(value, ignoreCase = true) } ?: NEUTRAL
        }
    }
}

// Request para análisis batch (múltiples imágenes)
data class BatchAnalysisRequest(
    val images: List<String>,
    val descriptions: List<String?>? = null,
    @SerializedName("child_id")
    val childId: Int? = null
)

// Response para análisis batch
data class BatchAnalysisResponse(
    val results: List<EmotionAnalysisResponse>,
    val summary: EmotionSummary
)

// Resumen de emociones
data class EmotionSummary(
    @SerializedName("total_faces")
    val totalFaces: Int,
    @SerializedName("average_emotions")
    val averageEmotions: EmotionScores,
    @SerializedName("dominant_emotion")
    val dominantEmotion: String,
    @SerializedName("emotion_distribution")
    val emotionDistribution: Map<String, Int>
)

// Error response del servicio
data class EmotionErrorResponse(
    val error: String,
    val message: String,
    val code: String?,
    val details: Map<String, Any>?
)