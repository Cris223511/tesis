package com.example.serious_game_usil.presentation.ui.terapeuta

import com.example.serious_game_usil.`interface`.TherapySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportPreviewUiModel(
    val badge: String,
    val title: String,
    val subtitle: String,
    val metricOneValue: String,
    val metricOneLabel: String,
    val metricTwoValue: String,
    val metricTwoLabel: String,
    val metricThreeValue: String,
    val metricThreeLabel: String,
    val body: String,
    val checklist: String,
    val actionLabel: String
)

object ReportPreviewFormatter {
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))

    fun emptyIndividual(formatLabel: String): ReportPreviewUiModel {
        return ReportPreviewUiModel(
            badge = "Vista previa",
            title = "Reporte individual",
            subtitle = "Selecciona una sesión para revisar los datos antes de exportar.",
            metricOneValue = "--",
            metricOneLabel = "Sesión",
            metricTwoValue = formatLabel,
            metricTwoLabel = "Formato",
            metricThreeValue = "Pendiente",
            metricThreeLabel = "Estado",
            body = "El reporte individual mostrará paciente, terapeuta, horario, estado y análisis emocional según la configuración elegida.",
            checklist = "• Elige una sesión del listado.\n• Revisa el formato de salida.\n• Ajusta si deseas incluir análisis emocional.",
            actionLabel = "Generar reporte individual"
        )
    }

    fun emptyGeneral(formatLabel: String): ReportPreviewUiModel {
        return ReportPreviewUiModel(
            badge = "Vista previa",
            title = "Reporte general",
            subtitle = "Define un rango de fechas para consolidar sesiones y continuidad terapéutica.",
            metricOneValue = "--",
            metricOneLabel = "Período",
            metricTwoValue = formatLabel,
            metricTwoLabel = "Formato",
            metricThreeValue = "Pendiente",
            metricThreeLabel = "Estado",
            body = "El reporte general resumirá volumen de sesiones, pacientes atendidos y consistencia del programa dentro del período seleccionado.",
            checklist = "• Marca fecha de inicio y fin.\n• Usa accesos rápidos si necesitas un corte reciente.\n• Verifica que el rango cubra las sesiones que quieres consolidar.",
            actionLabel = "Generar reporte general"
        )
    }

    fun individual(
        session: TherapySession,
        formatLabel: String,
        includeAnalysis: Boolean
    ): ReportPreviewUiModel {
        val sessionDate = formatApiDate(session.fechaSesion)
        val analysisText = if (includeAnalysis) {
            "Incluye análisis emocional y métricas del reconocimiento."
        } else {
            "Se exportará solo el detalle clínico y operativo de la sesión."
        }

        return ReportPreviewUiModel(
            badge = "Reporte individual",
            title = session.paciente.nombresApellidos,
            subtitle = "${sessionDate} • ${session.horaInicio} - ${session.horaFin}",
            metricOneValue = "Sesión #${session.id}",
            metricOneLabel = "Selección actual",
            metricTwoValue = formatLabel,
            metricTwoLabel = "Formato",
            metricThreeValue = getSessionStatusLabel(session.estado),
            metricThreeLabel = "Estado",
            body = buildString {
                append("Paciente: ${session.paciente.nombresApellidos}\n")
                append("Terapeuta: ${session.terapeuta.nombresApellidos}\n")
                append("Tipo: ${session.tipoSesion ?: "Terapia individual"}\n")
                append("Modalidad: ${session.modalidad ?: "Presencial"}\n")
                append("Duración estimada: ${session.duracion} minutos")
            },
            checklist = "• ${analysisText}\n• Se mantendrá una estructura visual limpia y apta para compartir.\n• El documento respetará la fecha y estado originales de la sesión.",
            actionLabel = "Generar reporte individual"
        )
    }

    fun general(
        sessions: List<TherapySession>,
        startDate: Date,
        endDate: Date,
        formatLabel: String,
        includeConclusions: Boolean
    ): ReportPreviewUiModel {
        val completedSessions = sessions.count { it.estado.equals("completada", ignoreCase = true) }
        val uniquePatients = sessions.map { it.pacienteId }.distinct().size
        val averageDuration = if (sessions.isNotEmpty()) {
            sessions.map { it.duracion }.average().toInt()
        } else {
            0
        }
        val conclusionText = if (includeConclusions) {
            "Se agregarán conclusiones automáticas con enfoque terapéutico."
        } else {
            "El reporte se centrará en los datos duros del período."
        }

        return ReportPreviewUiModel(
            badge = "Reporte general",
            title = "${displayDateFormat.format(startDate)} - ${displayDateFormat.format(endDate)}",
            subtitle = if (sessions.isEmpty()) {
                "No hay sesiones dentro del rango seleccionado."
            } else {
                "${sessions.size} sesiones detectadas en el período"
            },
            metricOneValue = sessions.size.toString(),
            metricOneLabel = "Sesiones",
            metricTwoValue = uniquePatients.toString(),
            metricTwoLabel = "Pacientes",
            metricThreeValue = if (sessions.isEmpty()) {
                "Sin datos"
            } else {
                "${averageDuration} min promedio"
            },
            metricThreeLabel = "Carga terapéutica",
            body = buildString {
                append("Sesiones completadas: $completedSessions\n")
                append("Sesiones no completadas: ${sessions.size - completedSessions}\n")
                append("Formato de salida: $formatLabel\n")
                append("Enfoque: resumen consolidado por período")
            },
            checklist = "• ${conclusionText}\n• Se respetará el rango exacto seleccionado.\n• El documento se optimiza para lectura rápida y presentación ordenada.\n• La continuidad del programa quedará visible sin saturar la pantalla.",
            actionLabel = "Generar reporte general"
        )
    }

    private fun formatApiDate(value: String): String {
        return runCatching {
            val parsedDate = apiDateFormat.parse(value) ?: return value
            displayDateFormat.format(parsedDate)
        }.getOrElse { value }
    }

    private fun getSessionStatusLabel(status: String?): String {
        return when (status?.lowercase()) {
            "completada" -> "Completada"
            "programada" -> "Programada"
            "cancelada" -> "Cancelada"
            else -> "En revisión"
        }
    }
}
