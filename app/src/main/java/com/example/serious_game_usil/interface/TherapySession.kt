package com.example.serious_game_usil.`interface`

import com.google.gson.annotations.SerializedName

data class TherapySession(
    val id: Int,
    @SerializedName("paciente_id") val pacienteId: Int,
    @SerializedName("terapeuta_id") val terapeutaId: Int,
    @SerializedName("fecha_sesion") val fechaSesion: String,
    @SerializedName("hora_inicio") val horaInicio: String,
    @SerializedName("hora_fin") val horaFin: String,
    val duracion: Int,
    val ubicacion: String?,
    val direccion: String?,
    val descripcion: String?,
    val objetivos: List<String>?,
    val materiales: List<String>?,
    @SerializedName("notas_terapeuta") val notasTerapeuta: String?,
    val estado: String,
    @SerializedName("tipo_sesion") val tipoSesion: String?,
    val modalidad: String?,
    @SerializedName("update_count") val updateCount: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val paciente: PacienteInfo,
    val terapeuta: TerapeutaInfo,
    val cuidador: CuidadorInfo?
)

data class PacienteInfo(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("foto_movil") val fotoMovil: String?
)

data class TerapeutaInfo(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?
)

data class CuidadorInfo(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?
)

data class CreateTherapySessionRequest(
    @SerializedName("paciente_id") val pacienteId: Int,
    @SerializedName("terapeuta_id") val terapeutaId: Int?,
    @SerializedName("fecha_sesion") val fechaSesion: String,
    @SerializedName("hora_inicio") val horaInicio: String,
    @SerializedName("hora_fin") val horaFin: String,
    val duracion: Int,
    val ubicacion: String?,
    val direccion: String?,
    val descripcion: String?,
    val objetivos: List<String>?,
    val materiales: List<String>?,
    @SerializedName("tipo_sesion") val tipoSesion: String?,
    val modalidad: String?
)

data class PatientListItem(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("serial_id") val serialId: String,
    val edad: Int?,
    @SerializedName("foto_movil") val fotoMovil: String?
)

data class SessionReportRequest(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("format") val format: String,
    @SerializedName("include_emotions") val includeEmotions: Boolean = false
)

data class GeneralReportRequest(
    @SerializedName("therapist_id") val therapistId: Int? = null,
    @SerializedName("patient_id") val patientId: Int? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("include_statistics") val includeStatistics: Boolean = true
)

data class ReportResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("file_url") val fileUrl: String?,
    @SerializedName("report_data") val reportData: ReportData?
)

data class ReportData(
    @SerializedName("total_sessions") val totalSessions: Int,
    @SerializedName("completed_sessions") val completedSessions: Int,
    @SerializedName("cancelled_sessions") val cancelledSessions: Int,
    @SerializedName("average_duration") val averageDuration: Int,
    @SerializedName("unique_patients") val uniquePatients: Int,
    val conclusions: String?,
    val recommendations: String?
)