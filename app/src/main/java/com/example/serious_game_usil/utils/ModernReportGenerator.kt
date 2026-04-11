package com.example.serious_game_usil.utils

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.serious_game_usil.`interface`.TherapySession
import java.text.SimpleDateFormat
import java.util.*

class ModernReportGenerator {

    companion object {
        private const val A4_WIDTH = 595
        private const val A4_HEIGHT = 842
        private const val MARGIN_TOP = 60f
        private const val MARGIN_LEFT = 50f
        private const val MARGIN_RIGHT = 50f
        private const val MARGIN_BOTTOM = 60f
        private const val CONTENT_WIDTH = A4_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

        private val COLOR_PRIMARY = Color.rgb(84, 110, 122)
        private val COLOR_PRIMARY_DARK = Color.rgb(55, 71, 79)
        private val COLOR_SECONDARY = Color.rgb(96, 125, 139)
        private val COLOR_TEXT_PRIMARY = Color.rgb(33, 33, 33)
        private val COLOR_TEXT_SECONDARY = Color.rgb(117, 117, 117)
        private val COLOR_BACKGROUND = Color.rgb(250, 250, 250)
        private val COLOR_BORDER = Color.rgb(229, 229, 229)
        private val COLOR_SUCCESS = Color.rgb(102, 187, 106)
        private val COLOR_WARNING = Color.rgb(255, 167, 38)
        private val COLOR_INFO = Color.rgb(120, 144, 156)

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private val sessionDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val fullDateFormat = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    }

    data class ReportStyle(
        val headerHeight: Float = 120f,
        val sectionSpacing: Float = 30f,
        val lineSpacing: Float = 22f,
        val cardPadding: Float = 15f,
        val cornerRadius: Float = 8f
    )

    fun generateModernSessionPdf(
        document: PdfDocument,
        session: TherapySession,
        includeAnalysis: Boolean = false
    ): PdfDocument {
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val style = ReportStyle()

        var currentY = MARGIN_TOP

        currentY = drawModernHeader(canvas, session, currentY, style)
        currentY = drawPatientSection(canvas, session, currentY, style)
        currentY = drawTherapistSection(canvas, session, currentY, style)
        currentY = drawSessionDetailsSection(canvas, session, currentY, style)

        if (!session.descripcion.isNullOrBlank()) {
            currentY = drawDescriptionSection(canvas, session, currentY, style)
        }

        if (!session.objetivos.isNullOrEmpty()) {
            currentY = drawObjectivesSection(canvas, session, currentY, style)
        }

        if (!session.notasTerapeuta.isNullOrBlank()) {
            currentY = drawNotesSection(canvas, session, currentY, style)
        }

        drawModernFooter(canvas)

        document.finishPage(page)
        return document
    }

    private fun drawModernHeader(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        val headerRect = RectF(
            MARGIN_LEFT - 10,
            startY - 10,
            A4_WIDTH - MARGIN_RIGHT + 10f,
            startY + style.headerHeight
        )
        paint.color = COLOR_PRIMARY
        canvas.drawRoundRect(headerRect, style.cornerRadius, style.cornerRadius, paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(
            "REPORTE DE SESIÓN TERAPÉUTICA",
            MARGIN_LEFT + 10,
            startY + 35,
            paint
        )

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(
            "Sesión #${session.id}",
            MARGIN_LEFT + 10,
            startY + 60,
            paint
        )

        val statusText = getStatusText(session.estado)
        val statusColor = getStatusColor(session.estado)
        val statusWidth = paint.measureText(statusText) + 20

        val statusRect = RectF(
            A4_WIDTH - MARGIN_RIGHT - statusWidth - 10,
            startY + 70,
            A4_WIDTH - MARGIN_RIGHT - 10f,
            startY + 95
        )
        paint.color = Color.WHITE
        paint.alpha = 230
        canvas.drawRoundRect(statusRect, 12f, 12f, paint)

        paint.color = statusColor
        paint.textSize = 14f
        paint.alpha = 255
        canvas.drawText(
            statusText,
            statusRect.left + 10,
            statusRect.top + 18,
            paint
        )

        return startY + style.headerHeight + style.sectionSpacing
    }

    private fun drawPatientSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "INFORMACIÓN DEL PACIENTE", currentY)
        currentY += 10f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + 80
        )
        drawCard(canvas, cardRect, style)

        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        canvas.drawText(
            "Nombre: ${session.paciente.nombresApellidos}",
            MARGIN_LEFT + style.cardPadding,
            currentY + 30,
            paint
        )

        session.paciente.fotoMovil?.let {
            canvas.drawText(
                "ID Paciente: ${session.pacienteId}",
                MARGIN_LEFT + style.cardPadding,
                currentY + 55,
                paint
            )
        }

        return currentY + 80 + style.sectionSpacing
    }

    private fun drawTherapistSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "INFORMACIÓN DEL TERAPEUTA", currentY)
        currentY += 10f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + 105
        )
        drawCard(canvas, cardRect, style)

        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        var textY = currentY + 30
        canvas.drawText(
            "Nombre: ${session.terapeuta.nombresApellidos}",
            MARGIN_LEFT + style.cardPadding,
            textY,
            paint
        )

        textY += 25
        canvas.drawText(
            "Correo: ${session.terapeuta.correo}",
            MARGIN_LEFT + style.cardPadding,
            textY,
            paint
        )

        session.terapeuta.telefono?.let {
            textY += 25
            canvas.drawText(
                "Teléfono: $it",
                MARGIN_LEFT + style.cardPadding,
                textY,
                paint
            )
        }

        return currentY + 105 + style.sectionSpacing
    }

    private fun drawSessionDetailsSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "DETALLES DE LA SESIÓN", currentY)
        currentY += 10f

        val details = mutableListOf<Pair<String, String>>()
        details.add("Fecha" to formatSessionDateLong(session.fechaSesion))
        details.add("Hora" to "${session.horaInicio} - ${session.horaFin}")
        details.add("Duración" to "${session.duracion} minutos")
        details.add("Ubicación" to (session.ubicacion ?: "No especificada"))
        details.add("Modalidad" to (session.modalidad ?: "Presencial"))
        details.add("Tipo de Sesión" to (session.tipoSesion ?: "Individual"))

        val cardHeight = details.size * 25f + 30f
        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + cardHeight
        )
        drawCard(canvas, cardRect, style)

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 14f
        }

        var textY = currentY + 25
        details.forEach { (label, value) ->
            paint.color = COLOR_TEXT_SECONDARY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(
                "$label:",
                MARGIN_LEFT + style.cardPadding,
                textY,
                paint
            )

            paint.color = COLOR_TEXT_PRIMARY
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(
                value,
                MARGIN_LEFT + 120,
                textY,
                paint
            )

            textY += 25
        }

        return currentY + cardHeight + style.sectionSpacing
    }

    private fun drawDescriptionSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "DESCRIPCIÓN", currentY)
        currentY += 10f

        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        val lines = wrapText(session.descripcion ?: "", paint, CONTENT_WIDTH - 30)
        val cardHeight = lines.size * style.lineSpacing + 30f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + cardHeight
        )
        drawCard(canvas, cardRect, style)

        var textY = currentY + 25
        lines.forEach { line ->
            canvas.drawText(
                line,
                MARGIN_LEFT + style.cardPadding,
                textY,
                paint
            )
            textY += style.lineSpacing
        }

        return currentY + cardHeight + style.sectionSpacing
    }

    private fun drawObjectivesSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        if (currentY > A4_HEIGHT - 200) {
            return currentY
        }

        currentY = drawSectionTitle(canvas, "OBJETIVOS", currentY)
        currentY += 10f

        val objectives = session.objetivos ?: listOf()
        val cardHeight = objectives.size * 25f + 30f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + cardHeight
        )
        drawCard(canvas, cardRect, style)

        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        val bulletPaint = Paint().apply {
            isAntiAlias = true
            color = COLOR_PRIMARY
            this.style = Paint.Style.FILL
        }

        var textY = currentY + 25
        objectives.forEach { objetivo ->
            canvas.drawCircle(
                MARGIN_LEFT + style.cardPadding,
                textY - 5,
                3f,
                bulletPaint
            )

            canvas.drawText(
                objetivo,
                MARGIN_LEFT + style.cardPadding + 15,
                textY,
                paint
            )
            textY += 25
        }

        return currentY + cardHeight + style.sectionSpacing
    }

    private fun drawNotesSection(
        canvas: Canvas,
        session: TherapySession,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        if (currentY > A4_HEIGHT - 150) {
            return currentY
        }

        currentY = drawSectionTitle(canvas, "NOTAS DEL TERAPEUTA", currentY)
        currentY += 10f

        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        val lines = wrapText(session.notasTerapeuta ?: "", paint, CONTENT_WIDTH - 30)
        val cardHeight = lines.size * style.lineSpacing + 30f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + cardHeight
        )
        drawCard(canvas, cardRect, style)

        var textY = currentY + 25
        lines.forEach { line ->
            canvas.drawText(
                line,
                MARGIN_LEFT + style.cardPadding,
                textY,
                paint
            )
            textY += style.lineSpacing
        }

        return currentY + cardHeight + style.sectionSpacing
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_PRIMARY_DARK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText(title, MARGIN_LEFT, y + 20, paint)

        paint.strokeWidth = 2f
        paint.color = COLOR_PRIMARY
        canvas.drawLine(
            MARGIN_LEFT,
            y + 25,
            MARGIN_LEFT + paint.measureText(title),
            y + 25,
            paint
        )

        return y + 30
    }

    private fun drawCard(canvas: Canvas, rect: RectF, style: ReportStyle) {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        paint.color = Color.WHITE
        paint.setShadowLayer(4f, 0f, 2f, Color.argb(30, 0, 0, 0))
        canvas.drawRoundRect(rect, style.cornerRadius, style.cornerRadius, paint)

        paint.clearShadowLayer()
        paint.color = COLOR_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, style.cornerRadius, style.cornerRadius, paint)
    }

    private fun drawModernFooter(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        paint.color = COLOR_BORDER
        canvas.drawLine(
            MARGIN_LEFT,
            A4_HEIGHT - MARGIN_BOTTOM.toFloat(),
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            A4_HEIGHT - MARGIN_BOTTOM.toFloat(),
            paint
        )

        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 11f

        val date = "Generado: ${fullDateFormat.format(Date())}"
        canvas.drawText(
            date,
            MARGIN_LEFT,
            A4_HEIGHT - MARGIN_BOTTOM + 20f,
            paint
        )

        val system = "Sistema de Gestión Terapéutica"
        val textWidth = paint.measureText(system)
        canvas.drawText(
            system,
            A4_WIDTH - MARGIN_RIGHT - textWidth,
            A4_HEIGHT - MARGIN_BOTTOM + 20f,
            paint
        )
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    private fun parseSessionDate(value: String): Date? {
        return runCatching { sessionDateFormat.parse(value) }.getOrNull()
            ?: runCatching { dateFormat.parse(value) }.getOrNull()
    }

    private fun formatSessionDateLong(value: String): String {
        val parsedDate = parseSessionDate(value) ?: return value
        return fullDateFormat.format(parsedDate)
    }

    private fun formatSessionDateShort(value: String): String {
        val parsedDate = parseSessionDate(value) ?: return value
        return dateFormat.format(parsedDate)
    }

    private fun getStatusText(status: String?): String {
        return when (status?.lowercase()) {
            "programada" -> "PROGRAMADA"
            "completada" -> "COMPLETADA"
            "cancelada" -> "CANCELADA"
            "en_progreso" -> "EN PROGRESO"
            else -> "PENDIENTE"
        }
    }

    private fun getStatusColor(status: String?): Int {
        return when (status?.lowercase()) {
            "completada" -> COLOR_SUCCESS
            "cancelada" -> COLOR_WARNING
            "en_progreso" -> COLOR_INFO
            else -> COLOR_TEXT_SECONDARY
        }
    }

    fun generateModernGeneralReport(
        document: PdfDocument,
        sessions: List<TherapySession>,
        therapistName: String,
        dateRange: Pair<Date, Date>,
        statistics: ReportStatistics
    ): PdfDocument {
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val style = ReportStyle()

        var currentY = MARGIN_TOP

        currentY = drawGeneralHeader(canvas, therapistName, dateRange, currentY, style)
        currentY = drawStatisticsSection(canvas, statistics, currentY, style)
        currentY = drawSessionsSummaryTable(canvas, sessions.take(5), currentY, style)
        currentY = drawAnalysisSection(canvas, statistics, currentY, style)

        drawModernFooter(canvas)

        document.finishPage(page)
        return document
    }

    private fun drawGeneralHeader(
        canvas: Canvas,
        therapistName: String,
        dateRange: Pair<Date, Date>,
        startY: Float,
        style: ReportStyle
    ): Float {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        val headerRect = RectF(
            MARGIN_LEFT - 10,
            startY - 10,
            A4_WIDTH - MARGIN_RIGHT + 10f,
            startY + style.headerHeight + 20
        )
        paint.color = COLOR_PRIMARY_DARK
        canvas.drawRoundRect(headerRect, style.cornerRadius, style.cornerRadius, paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(
            "REPORTE CONSOLIDADO",
            MARGIN_LEFT + 10,
            startY + 35,
            paint
        )

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(
            "Terapeuta: $therapistName",
            MARGIN_LEFT + 10,
            startY + 60,
            paint
        )

        val period = "${dateFormat.format(dateRange.first)} - ${dateFormat.format(dateRange.second)}"
        canvas.drawText(
            "Período: $period",
            MARGIN_LEFT + 10,
            startY + 85,
            paint
        )

        return startY + style.headerHeight + 40 + style.sectionSpacing
    }

    private fun drawStatisticsSection(
        canvas: Canvas,
        statistics: ReportStatistics,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "ESTADÍSTICAS", currentY)
        currentY += 10f

        val stats = listOf(
            Triple("Total Sesiones", statistics.totalSessions.toString(), COLOR_PRIMARY),
            Triple("Completadas", statistics.completedSessions.toString(), COLOR_SUCCESS),
            Triple("Canceladas", statistics.cancelledSessions.toString(), COLOR_WARNING),
            Triple("Tasa Éxito", "${statistics.successRate}%", COLOR_INFO)
        )

        val cardWidth = (CONTENT_WIDTH - 30) / 4
        var x = MARGIN_LEFT

        stats.forEach { (label, value, color) ->
            val cardRect = RectF(x, currentY, x + cardWidth, currentY + 80)
            drawStatCard(canvas, cardRect, label, value, color, style)
            x += cardWidth + 10
        }

        return currentY + 80 + style.sectionSpacing
    }

    private fun drawStatCard(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        color: Int,
        style: ReportStyle
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
        }

        paint.color = Color.WHITE
        paint.setShadowLayer(3f, 0f, 1f, Color.argb(20, 0, 0, 0))
        canvas.drawRoundRect(rect, style.cornerRadius, style.cornerRadius, paint)
        paint.clearShadowLayer()

        paint.color = color
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val valueX = rect.centerX() - paint.measureText(value) / 2
        canvas.drawText(value, valueX, rect.top + 35, paint)

        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val labelX = rect.centerX() - paint.measureText(label) / 2
        canvas.drawText(label, labelX, rect.top + 55, paint)
    }

    private fun drawSessionsSummaryTable(
        canvas: Canvas,
        sessions: List<TherapySession>,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "SESIONES RECIENTES", currentY)
        currentY += 10f

        val tableHeight = (sessions.size + 1) * 30f + 20f
        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + tableHeight
        )
        drawCard(canvas, cardRect, style)

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 13f
        }

        var y = currentY + 25

        paint.color = COLOR_PRIMARY_DARK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Fecha", MARGIN_LEFT + 15, y, paint)
        canvas.drawText("Paciente", MARGIN_LEFT + 100, y, paint)
        canvas.drawText("Duración", MARGIN_LEFT + 280, y, paint)
        canvas.drawText("Estado", MARGIN_LEFT + 360, y, paint)

        y += 5
        paint.color = COLOR_BORDER
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN_LEFT + 10, y, A4_WIDTH - MARGIN_RIGHT - 10f, y, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        sessions.forEach { session ->
            y += 25
            paint.color = COLOR_TEXT_PRIMARY
            canvas.drawText(
                formatSessionDateShort(session.fechaSesion),
                MARGIN_LEFT + 15,
                y,
                paint
            )
            canvas.drawText(
                session.paciente.nombresApellidos.take(20),
                MARGIN_LEFT + 100,
                y,
                paint
            )
            canvas.drawText(
                "${session.duracion} min",
                MARGIN_LEFT + 280,
                y,
                paint
            )

            paint.color = getStatusColor(session.estado)
            canvas.drawText(
                getStatusText(session.estado),
                MARGIN_LEFT + 360,
                y,
                paint
            )
        }

        return currentY + tableHeight + style.sectionSpacing
    }

    private fun drawAnalysisSection(
        canvas: Canvas,
        statistics: ReportStatistics,
        startY: Float,
        style: ReportStyle
    ): Float {
        var currentY = startY

        currentY = drawSectionTitle(canvas, "ANÁLISIS Y CONCLUSIONES", currentY)
        currentY += 10f

        val conclusions = generateConclusions(statistics)
        val paint = Paint().apply {
            isAntiAlias = true
            color = COLOR_TEXT_PRIMARY
            textSize = 14f
        }

        val lines = wrapText(conclusions, paint, CONTENT_WIDTH - 30)
        val cardHeight = lines.size * style.lineSpacing + 30f

        val cardRect = RectF(
            MARGIN_LEFT,
            currentY,
            A4_WIDTH - MARGIN_RIGHT.toFloat(),
            currentY + cardHeight
        )
        drawCard(canvas, cardRect, style)

        var textY = currentY + 25
        lines.forEach { line ->
            canvas.drawText(
                line,
                MARGIN_LEFT + style.cardPadding,
                textY,
                paint
            )
            textY += style.lineSpacing
        }

        return currentY + cardHeight + style.sectionSpacing
    }

    private fun generateConclusions(statistics: ReportStatistics): String {
        return buildString {
            append("Durante el período analizado se registraron ${statistics.totalSessions} sesiones terapéuticas. ")
            append("Se alcanzó una tasa de éxito del ${statistics.successRate}% con ${statistics.completedSessions} sesiones completadas. ")

            if (statistics.cancelledSessions > 0) {
                append("Se identificaron ${statistics.cancelledSessions} cancelaciones que requieren análisis. ")
            }

            append("El tiempo promedio de sesión fue de ${statistics.averageDuration} minutos, ")
            append("atendiendo a ${statistics.uniquePatients} pacientes únicos.")
        }
    }

    data class ReportStatistics(
        val totalSessions: Int,
        val completedSessions: Int,
        val cancelledSessions: Int,
        val averageDuration: Int,
        val uniquePatients: Int,
        val successRate: Int
    )
}
