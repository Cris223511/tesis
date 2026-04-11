package com.example.serious_game_usil.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.repository.EmotionRepository
import com.example.serious_game_usil.repository.TherapySessionRepository
import com.example.serious_game_usil.utils.GeneralReportDocumentRenderer
import com.example.serious_game_usil.utils.SessionReportDocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportService(private val context: Context) {

    private val repository = TherapySessionRepository()
    private val emotionRepository = EmotionRepository.getInstance()
    private val sessionDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val logTag = "ReportService"

    data class SessionReport(
        val session: TherapySession,
        val generatedAt: Date,
        val filePath: String? = null,
        val format: ReportFormat
    )

    data class GeneralReport(
        val sessions: List<TherapySession>,
        val therapistName: String,
        val dateRange: Pair<Date, Date>,
        val totalSessions: Int,
        val completedSessions: Int,
        val cancelledSessions: Int,
        val averageDuration: Int,
        val uniquePatients: Int,
        val conclusions: String,
        val recommendations: String,
        val generatedAt: Date,
        val filePath: String? = null
    )

    data class EmotionSnapshot(
        val emotionLabel: String,
        val confidencePercent: Int,
        val analyzedAt: Date,
        val sourceNote: String
    )

    enum class ReportFormat {
        PDF, JPG, HTML, JSON
    }

    suspend fun generateSessionReport(
        sessionId: Int,
        format: ReportFormat = ReportFormat.PDF,
        includeEmotionAnalysis: Boolean = false
    ): Flow<Result<SessionReport>> = flow {
        try {
            repository.getSession(sessionId).collect { result ->
                when (result) {
                    is com.example.serious_game_usil.data.ApiResult.Success -> {
                        val session = result.data
                        val report = when (format) {
                            ReportFormat.PDF -> generatePdfReport(session, includeEmotionAnalysis)
                            ReportFormat.JPG -> generateImageReport(session, includeEmotionAnalysis)
                            ReportFormat.HTML -> generateHtmlReport(session)
                            ReportFormat.JSON -> generateJsonReport(session)
                        }
                        emit(Result.success(report))
                    }
                    is com.example.serious_game_usil.data.ApiResult.Error -> {
                        emit(Result.failure(Exception(result.message)))
                    }
                    is com.example.serious_game_usil.data.ApiResult.NetworkError -> {
                        emit(Result.failure(result.exception))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun generateGeneralReport(
        format: ReportFormat = ReportFormat.PDF,
        therapistId: Int? = null,
        startDate: Date? = null,
        endDate: Date? = null,
        patientId: Int? = null
    ): Flow<Result<GeneralReport>> = flow {
        try {
            repository.getSessions().collect { result ->
                when (result) {
                    is com.example.serious_game_usil.data.ApiResult.Success -> {
                        var sessions = result.data

                        if (therapistId != null) {
                            sessions = sessions.filter { it.terapeutaId == therapistId }
                        }

                        if (patientId != null) {
                            sessions = sessions.filter { it.pacienteId == patientId }
                        }

                        if (startDate != null && endDate != null) {
                            sessions = sessions.filter { session ->
                                val sessionDate = parseSessionDate(session.fechaSesion)
                                sessionDate != null &&
                                !sessionDate.before(startDate) &&
                                !sessionDate.after(endDate)
                            }
                        }

                        val generalReport = analyzeAndCreateGeneralReport(
                            sessions = sessions,
                            selectedRange = if (startDate != null && endDate != null) {
                                startDate to endDate
                            } else {
                                null
                            }
                        )
                        val reportFile = when (format) {
                            ReportFormat.JPG -> generateGeneralImageReport(generalReport)
                            else -> generateGeneralPdfReport(generalReport)
                        }

                        emit(Result.success(reportFile))
                    }
                    is com.example.serious_game_usil.data.ApiResult.Error -> {
                        emit(Result.failure(Exception(result.message)))
                    }
                    is com.example.serious_game_usil.data.ApiResult.NetworkError -> {
                        emit(Result.failure(result.exception))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun generatePdfReport(
        session: TherapySession,
        includeEmotionAnalysis: Boolean
    ): SessionReport = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val renderer = SessionReportDocumentRenderer()
        val emotionSnapshot = if (includeEmotionAnalysis) {
            loadSessionEmotionSnapshot(session)
        } else {
            null
        }

        renderer.renderPdf(document, session, includeEmotionAnalysis, emotionSnapshot)

        val fileName = "reporte_sesion_${session.id}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        FileOutputStream(file).use { stream ->
            document.writeTo(stream)
        }
        document.close()

        SessionReport(
            session = session,
            generatedAt = Date(),
            filePath = file.absolutePath,
            format = ReportFormat.PDF
        )
    }

    private suspend fun generateImageReport(
        session: TherapySession,
        includeEmotionAnalysis: Boolean
    ): SessionReport = withContext(Dispatchers.IO) {
        val renderer = SessionReportDocumentRenderer()
        val emotionSnapshot = if (includeEmotionAnalysis) {
            loadSessionEmotionSnapshot(session)
        } else {
            null
        }
        val bitmap: Bitmap = renderer.renderBitmap(
            session = session,
            includeAnalysis = includeEmotionAnalysis,
            emotionSnapshot = emotionSnapshot
        )

        val fileName = "reporte_sesion_${session.id}_${System.currentTimeMillis()}.jpg"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        SessionReport(
            session = session,
            generatedAt = Date(),
            filePath = file.absolutePath,
            format = ReportFormat.JPG
        )
    }

    private suspend fun generateHtmlReport(session: TherapySession): SessionReport = withContext(Dispatchers.IO) {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head>")
            appendLine("<meta charset='UTF-8'>")
            appendLine("<title>Reporte Sesión #${session.id}</title>")
            appendLine("<style>")
            appendLine("body { font-family: Arial, sans-serif; margin: 20px; }")
            appendLine("h1 { color: #2196F3; }")
            appendLine("h2 { color: #1976D2; margin-top: 20px; }")
            appendLine(".info-row { margin: 10px 0; }")
            appendLine(".label { font-weight: bold; }")
            appendLine("</style>")
            appendLine("</head><body>")

            appendLine("<h1>REPORTE DE SESIÓN TERAPÉUTICA</h1>")

            appendLine("<h2>Información del Paciente</h2>")
            appendLine("<div class='info-row'><span class='label'>Nombre:</span> ${session.paciente.nombresApellidos}</div>")

            appendLine("<h2>Información del Terapeuta</h2>")
            appendLine("<div class='info-row'><span class='label'>Nombre:</span> ${session.terapeuta.nombresApellidos}</div>")
            appendLine("<div class='info-row'><span class='label'>Email:</span> ${session.terapeuta.correo}</div>")

            appendLine("<h2>Detalles de la Sesión</h2>")
            appendLine("<div class='info-row'><span class='label'>Fecha:</span> ${formatDate(session.fechaSesion)}</div>")
            appendLine("<div class='info-row'><span class='label'>Hora:</span> ${session.horaInicio} - ${session.horaFin}</div>")
            appendLine("<div class='info-row'><span class='label'>Duración:</span> ${session.duracion} minutos</div>")
            appendLine("<div class='info-row'><span class='label'>Estado:</span> ${getStatusText(session.estado)}</div>")

            if (!session.descripcion.isNullOrBlank()) {
                appendLine("<h2>Descripción</h2>")
                appendLine("<p>${session.descripcion}</p>")
            }

            if (!session.objetivos.isNullOrEmpty()) {
                appendLine("<h2>Objetivos</h2>")
                appendLine("<ul>")
                session.objetivos.forEach { objetivo ->
                    appendLine("<li>$objetivo</li>")
                }
                appendLine("</ul>")
            }

            appendLine("</body></html>")
        }

        val fileName = "reporte_sesion_${session.id}_${System.currentTimeMillis()}.html"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.writeText(html)

        SessionReport(
            session = session,
            generatedAt = Date(),
            filePath = file.absolutePath,
            format = ReportFormat.HTML
        )
    }

    private suspend fun generateJsonReport(session: TherapySession): SessionReport = withContext(Dispatchers.IO) {
        val json = com.google.gson.Gson().toJson(session)

        val fileName = "reporte_sesion_${session.id}_${System.currentTimeMillis()}.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.writeText(json)

        SessionReport(
            session = session,
            generatedAt = Date(),
            filePath = file.absolutePath,
            format = ReportFormat.JSON
        )
    }

    private fun analyzeAndCreateGeneralReport(
        sessions: List<TherapySession>,
        selectedRange: Pair<Date, Date>? = null
    ): GeneralReport {
        val completedSessions = sessions.filter { it.estado == "completada" }
        val cancelledSessions = sessions.filter { it.estado == "cancelada" }
        val uniquePatients = sessions.map { it.pacienteId }.distinct().size
        val averageDuration = if (sessions.isNotEmpty()) {
            sessions.map { it.duracion }.average().toInt()
        } else 0

        val detectedRange = if (sessions.isNotEmpty()) {
            val dates = sessions.mapNotNull { parseSessionDate(it.fechaSesion) }
            Pair(dates.minOrNull() ?: Date(), dates.maxOrNull() ?: Date())
        } else {
            Pair(Date(), Date())
        }
        val dateRange = selectedRange ?: detectedRange

        val therapistName = sessions.firstOrNull()?.terapeuta?.nombresApellidos ?: "N/A"

        val conclusions = generateConclusions(sessions, completedSessions.size, cancelledSessions.size)
        val recommendations = generateRecommendations(completedSessions.size, cancelledSessions.size, uniquePatients)

        return GeneralReport(
            sessions = sessions,
            therapistName = therapistName,
            dateRange = dateRange,
            totalSessions = sessions.size,
            completedSessions = completedSessions.size,
            cancelledSessions = cancelledSessions.size,
            averageDuration = averageDuration,
            uniquePatients = uniquePatients,
            conclusions = conclusions,
            recommendations = recommendations,
            generatedAt = Date()
        )
    }

    private suspend fun generateGeneralPdfReport(report: GeneralReport): GeneralReport = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val renderer = GeneralReportDocumentRenderer()
        val emotionSnapshots = loadEmotionSnapshotsForPatients(
            patientIds = report.sessions.map { it.pacienteId }.toSet(),
            preferredRange = report.dateRange,
            sessions = report.sessions
        ).mapValues { (_, snapshot) ->
            GeneralReportDocumentRenderer.PatientEmotionSnapshot(
                emotionLabel = snapshot.emotionLabel,
                confidencePercent = snapshot.confidencePercent,
                sourceNote = snapshot.sourceNote
            )
        }

        val statistics = GeneralReportDocumentRenderer.SummaryStats(
            totalSessions = report.totalSessions,
            completedSessions = report.completedSessions,
            cancelledSessions = report.cancelledSessions,
            averageDuration = report.averageDuration,
            uniquePatients = report.uniquePatients,
            successRate = if (report.totalSessions > 0)
                (report.completedSessions * 100 / report.totalSessions) else 0
        )

        renderer.renderPdf(
            document = document,
            therapistName = report.therapistName,
            dateRange = report.dateRange,
            sessions = report.sessions,
            emotionSnapshots = emotionSnapshots,
            statistics = statistics,
            conclusions = report.conclusions,
            recommendations = report.recommendations
        )

        val fileName = "reporte_general_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        FileOutputStream(file).use { stream ->
            document.writeTo(stream)
        }
        document.close()

        report.copy(filePath = file.absolutePath)
    }

    private suspend fun generateGeneralImageReport(report: GeneralReport): GeneralReport = withContext(Dispatchers.IO) {
        val renderer = GeneralReportDocumentRenderer()
        val emotionSnapshots = loadEmotionSnapshotsForPatients(
            patientIds = report.sessions.map { it.pacienteId }.toSet(),
            preferredRange = report.dateRange,
            sessions = report.sessions
        ).mapValues { (_, snapshot) ->
            GeneralReportDocumentRenderer.PatientEmotionSnapshot(
                emotionLabel = snapshot.emotionLabel,
                confidencePercent = snapshot.confidencePercent,
                sourceNote = snapshot.sourceNote
            )
        }
        val statistics = GeneralReportDocumentRenderer.SummaryStats(
            totalSessions = report.totalSessions,
            completedSessions = report.completedSessions,
            cancelledSessions = report.cancelledSessions,
            averageDuration = report.averageDuration,
            uniquePatients = report.uniquePatients,
            successRate = if (report.totalSessions > 0)
                (report.completedSessions * 100 / report.totalSessions) else 0
        )

        val bitmap = renderer.renderBitmap(
            therapistName = report.therapistName,
            dateRange = report.dateRange,
            sessions = report.sessions,
            emotionSnapshots = emotionSnapshots,
            statistics = statistics,
            conclusions = report.conclusions,
            recommendations = report.recommendations
        )

        val fileName = "reporte_general_${System.currentTimeMillis()}.jpg"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        report.copy(filePath = file.absolutePath)
    }

    private fun generateConclusions(
        sessions: List<TherapySession>,
        completed: Int,
        cancelled: Int
    ): String {
        val completionRate = if (sessions.isNotEmpty()) {
            (completed.toFloat() / sessions.size * 100).toInt()
        } else 0

        return buildString {
            appendLine("Durante el período analizado se registraron ${sessions.size} sesiones terapéuticas.")
            appendLine("Se logró una tasa de completitud del $completionRate%, con $completed sesiones completadas exitosamente.")
            if (cancelled > 0) {
                appendLine("Se registraron $cancelled cancelaciones que requieren seguimiento.")
            }
            appendLine("El progreso general de los pacientes muestra tendencias positivas en las áreas evaluadas.")
        }
    }

    private fun generateRecommendations(
        completed: Int,
        cancelled: Int,
        uniquePatients: Int
    ): String {
        return buildString {
            if (cancelled > completed * 0.2) {
                appendLine("• Reducir la tasa de cancelaciones mediante recordatorios previos.")
            }
            appendLine("• Mantener el seguimiento regular con los $uniquePatients pacientes activos.")
            appendLine("• Documentar detalladamente las observaciones en cada sesión.")
            appendLine("• Evaluar periódicamente el progreso mediante análisis de emociones.")
            appendLine("• Ajustar los objetivos terapéuticos según los resultados observados.")
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val date = parseSessionDate(dateString) ?: return dateString
            val outputFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun parseSessionDate(dateString: String): Date? {
        return runCatching { sessionDateFormat.parse(dateString) }.getOrNull()
    }

    private fun getStatusText(status: String?): String {
        return when (status?.lowercase()) {
            "programada" -> "Programada"
            "completada" -> "Completada"
            "cancelada" -> "Cancelada"
            "en_progreso" -> "En Progreso"
            else -> "Sin Estado"
        }
    }

    private suspend fun loadSessionEmotionSnapshot(session: TherapySession): EmotionSnapshot? {
        return emotionRepository.getSessionAnalysis(session.id).fold(
            onSuccess = { analysis ->
                EmotionSnapshot(
                    emotionLabel = formatEmotionLabel(analysis.dominantEmotion),
                    confidencePercent = (analysis.confidenceScore * 100).toInt().coerceIn(0, 100),
                    analyzedAt = analysis.createdAt,
                    sourceNote = "Analisis emocional de la sesion"
                )
            },
            onFailure = { error ->
                Log.w(logTag, "No se pudo obtener analisis de la sesion ${session.id}: ${error.message}")
                null
            }
        )
    }

    private suspend fun loadEmotionSnapshotsForPatients(
        patientIds: Set<Int>,
        preferredRange: Pair<Date, Date>? = null,
        sessions: List<TherapySession> = emptyList()
    ): Map<Int, EmotionSnapshot> {
        if (patientIds.isEmpty()) return emptyMap()
        val scopedSessions = sessions.filter { patientIds.contains(it.pacienteId) }
        if (scopedSessions.isEmpty()) return emptyMap()

        val response = emotionRepository.getSessionAnalyses(scopedSessions.map { it.id }).getOrElse { error ->
            Log.w(logTag, "No se pudieron obtener analisis emocionales por sesion: ${error.message}")
            return emptyMap()
        }

        val analysesBySessionId = response.analyses.associateBy { it.sessionId }
        val orderedSessions = scopedSessions.sortedByDescending { parseSessionDate(it.fechaSesion)?.time ?: 0L }
        val patientSnapshots = mutableMapOf<Int, EmotionSnapshot>()

        for (session in orderedSessions) {
            if (patientSnapshots.containsKey(session.pacienteId)) continue
            val analysis = analysesBySessionId[session.id] ?: continue
            if (preferredRange != null) {
                val sessionDate = parseSessionDate(session.fechaSesion) ?: continue
                if (sessionDate.before(preferredRange.first) || sessionDate.after(preferredRange.second)) continue
            }

            patientSnapshots[session.pacienteId] = EmotionSnapshot(
                emotionLabel = formatEmotionLabel(analysis.dominantEmotion),
                confidencePercent = (analysis.confidenceScore * 100).toInt().coerceIn(0, 100),
                analyzedAt = analysis.createdAt,
                sourceNote = "Analisis de la sesion #${session.id}"
            )
        }

        return patientSnapshots
    }

    private fun formatEmotionLabel(raw: String): String {
        return when (raw.lowercase(Locale.getDefault())) {
            "happy" -> "Felicidad"
            "sad" -> "Tristeza"
            "angry" -> "Enojo"
            "surprise" -> "Sorpresa"
            "disgust" -> "Disgusto"
            "neutral", "fear" -> "Sin predominio claro"
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }

}
