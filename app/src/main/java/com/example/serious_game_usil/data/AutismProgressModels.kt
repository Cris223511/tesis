package com.example.serious_game_usil.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.google.gson.annotations.SerializedName


data class ThreeMonthComparison(
    @SerializedName("child_id") val childId: Int,
    @SerializedName("child_name") val childName: String,
    @SerializedName("current_month") val currentMonth: AutismProgressMetrics?,
    @SerializedName("months") val months: List<MonthlyProgress>,
    @SerializedName("summary") val summary: ProgressSummary,
    @SerializedName("recommendations") val recommendations: List<String>
)

data class MonthlyProgress(
    @SerializedName("month") val month: String,        // "Ago", "Jul", "Jun"
    @SerializedName("year") val year: Int,
    @SerializedName("overall_score") val overallScore: Int,  // 0-100
    @SerializedName("trend") val trend: String,              // "improving", "stable", "declining"
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("areas") val areas: List<AreaProgress>
)

data class AreaProgress(
    @SerializedName("name") val name: String,
    @SerializedName("score") val score: Int,    // 0-100
    @SerializedName("trend") val trend: Double  // cambio vs mes anterior
)

data class ProgressSummary(
    @SerializedName("overall_trend") val overallTrend: String,
    @SerializedName("strongest_areas") val strongestAreas: List<String>,
    @SerializedName("improving_areas") val improvingAreas: List<String>,
    @SerializedName("areas_needing_work") val areasNeedingWork: List<String>,
    @SerializedName("total_sessions_3m") val totalSessions3M: Int,
    @SerializedName("avg_session_time") val avgSessionTime: Int  // en minutos
)

data class AutismProgressMetrics(
    @SerializedName("child_id") val childId: Int,
    @SerializedName("year") val year: Int,
    @SerializedName("month") val month: Int,
    @SerializedName("avg_social_interaction") val avgSocialInteraction: Double,
    @SerializedName("avg_communication") val avgCommunication: Double,
    @SerializedName("avg_sensory_processing") val avgSensoryProcessing: Double,
    @SerializedName("avg_attention_focus") val avgAttentionFocus: Double,
    @SerializedName("avg_emotional_regulation") val avgEmotionalRegulation: Double,
    @SerializedName("avg_motor_skills") val avgMotorSkills: Double,
    @SerializedName("avg_problem_solving") val avgProblemSolving: Double,
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("completed_sessions") val completedSessions: Int,
    @SerializedName("meltdown_count") val meltdownCount: Int
)

// UI Models para la presentación
data class ProgressBarData(
    val label: String,
    val currentValue: Int,    // 0-100
    val previousValue: Int,   // 0-100 para comparación
    @ColorRes val colorRes: Int,
    val trend: TrendDirection,
    val description: String = ""
)

data class MonthComparisonCard(
    val month: String,
    val year: Int,
    val score: Int,           // 0-100
    val sessionsCount: Int,
    val trend: TrendDirection,
    val isCurrentMonth: Boolean = false,
    @ColorRes val backgroundColor: Int,
    @DrawableRes val trendIcon: Int
)

enum class TrendDirection {
    IMPROVING,    // Verde, flecha hacia arriba
    STABLE,       // Azul, línea horizontal
    DECLINING     // Rojo, flecha hacia abajo
}

// Extensiones para facilitar el manejo de datos
fun MonthlyProgress.getTrendDirection(): TrendDirection {
    return when(trend) {
        "improving" -> TrendDirection.IMPROVING
        "declining" -> TrendDirection.DECLINING
        else -> TrendDirection.STABLE
    }
}

fun AreaProgress.getTrendDirection(): TrendDirection {
    return when {
        trend > 5 -> TrendDirection.IMPROVING
        trend < -5 -> TrendDirection.DECLINING
        else -> TrendDirection.STABLE
    }
}

fun Int.toProgressText(): String {
    return when {
        this >= 80 -> "Excelente"
        this >= 60 -> "Bueno"
        this >= 40 -> "Regular"
        this >= 20 -> "Necesita mejora"
        else -> "Requiere atención"
    }
}

fun TrendDirection.toColorRes(): Int {
    return when(this) {
        TrendDirection.IMPROVING -> android.R.color.holo_green_dark
        TrendDirection.DECLINING -> android.R.color.holo_red_dark
        TrendDirection.STABLE -> android.R.color.holo_blue_dark
    }
}

fun TrendDirection.toIconRes(): Int {
    return when(this) {
        TrendDirection.IMPROVING -> android.R.drawable.arrow_up_float
        TrendDirection.DECLINING -> android.R.drawable.arrow_down_float
        TrendDirection.STABLE -> android.R.drawable.ic_menu_view
    }
}