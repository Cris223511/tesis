package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class PatientStatsResponse(
    @SerializedName("patient_id") val patientId: Int,
    @SerializedName("patient_name") val patientName: String,
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("completed_sessions") val completedSessions: Int,
    @SerializedName("scheduled_sessions") val scheduledSessions: Int,
    @SerializedName("cancelled_sessions") val cancelledSessions: Int,
    @SerializedName("progress_percentage") val progressPercentage: Int,
    @SerializedName("last_session_date") val lastSessionDate: String?,
    @SerializedName("days_since_last_session") val daysSinceLastSession: Int,
    @SerializedName("average_session_duration") val averageSessionDuration: Int
)

data class PatientStatsApiResponse(
    val message: String,
    val data: PatientStatsResponse
)