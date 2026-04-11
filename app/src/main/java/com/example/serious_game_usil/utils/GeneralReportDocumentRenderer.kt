package com.example.serious_game_usil.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.serious_game_usil.`interface`.TherapySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class GeneralReportDocumentRenderer {

    data class SummaryStats(
        val totalSessions: Int,
        val completedSessions: Int,
        val cancelledSessions: Int,
        val averageDuration: Int,
        val uniquePatients: Int,
        val successRate: Int
    )

    data class PatientEmotionSnapshot(
        val emotionLabel: String,
        val confidencePercent: Int,
        val sourceNote: String
    )

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val longDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))
    private val generatedAtFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun renderPdf(
        document: PdfDocument,
        therapistName: String,
        dateRange: Pair<Date, Date>,
        sessions: List<TherapySession>,
        emotionSnapshots: Map<Int, PatientEmotionSnapshot>,
        statistics: SummaryStats,
        conclusions: String,
        recommendations: String
    ): PdfDocument {
        val width = 595
        val height = max(
            842,
            measureHeight(width.toFloat(), sessions, conclusions, recommendations).toInt()
        )
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = document.startPage(pageInfo)
        drawReport(
            canvas = page.canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            therapistName = therapistName,
            dateRange = dateRange,
            sessions = sessions,
            emotionSnapshots = emotionSnapshots,
            statistics = statistics,
            conclusions = conclusions,
            recommendations = recommendations
        )
        document.finishPage(page)
        return document
    }

    fun renderBitmap(
        therapistName: String,
        dateRange: Pair<Date, Date>,
        sessions: List<TherapySession>,
        emotionSnapshots: Map<Int, PatientEmotionSnapshot>,
        statistics: SummaryStats,
        conclusions: String,
        recommendations: String
    ): Bitmap {
        val width = 1440f
        val height = measureHeight(width, sessions, conclusions, recommendations)
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawReport(
            canvas = canvas,
            width = width,
            height = height,
            therapistName = therapistName,
            dateRange = dateRange,
            sessions = sessions,
            emotionSnapshots = emotionSnapshots,
            statistics = statistics,
            conclusions = conclusions,
            recommendations = recommendations
        )
        return bitmap
    }

    private fun drawReport(
        canvas: Canvas,
        width: Float,
        height: Float,
        therapistName: String,
        dateRange: Pair<Date, Date>,
        sessions: List<TherapySession>,
        emotionSnapshots: Map<Int, PatientEmotionSnapshot>,
        statistics: SummaryStats,
        conclusions: String,
        recommendations: String
    ) {
        val spacing = Spacing(width)
        val palette = Palette()
        val paints = Paints(spacing, palette)
        val left = spacing.outer
        val right = width - spacing.outer
        var currentY = spacing.outer

        canvas.drawColor(palette.pageBackground)

        currentY = drawHeader(canvas, therapistName, dateRange, statistics, left, right, currentY, spacing, paints)
        currentY += spacing.sectionGap
        currentY = drawSummarySection(canvas, statistics, left, right, currentY, spacing, paints)
        currentY += spacing.sectionGap
        currentY = drawPatientsSection(canvas, sessions, emotionSnapshots, left, right, currentY, spacing, paints)
        currentY += spacing.sectionGap
        currentY = drawTextSection(canvas, "Conclusiones", conclusions, left, right, currentY, spacing, paints)
        currentY += spacing.cardGap
        currentY = drawTextSection(canvas, "Recomendaciones", recommendations, left, right, currentY, spacing, paints)

        drawFooter(canvas, width, height, spacing, paints)
    }

    private fun drawHeader(
        canvas: Canvas,
        therapistName: String,
        dateRange: Pair<Date, Date>,
        statistics: SummaryStats,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val bottom = top + spacing.headerHeight
        val rect = RectF(left, top, right, bottom)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                paints.palette.headerStart,
                paints.palette.headerEnd,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, headerPaint)

        val titleX = left + spacing.inner
        val titleMaxWidth = right - left - spacing.inner * 2
        val titleLines = wrapText("Reporte general de sesiones", paints.titlePaint, titleMaxWidth)
        var titleY = top + spacing.inner + paints.titlePaint.textSize
        titleLines.forEach { line ->
            canvas.drawText(line, titleX, titleY, paints.titlePaint)
            titleY += spacing.titleLineHeight
        }
        canvas.drawText(
            "Resumen terapéutico consolidado por paciente",
            titleX,
            titleY + spacing.smallGap,
            paints.subtitlePaint
        )

        val infoTop = bottom - spacing.headerInfoHeight
        val boxes = listOf(
            "Terapeuta" to therapistName,
            "Período" to "${shortDateFormat.format(dateRange.first)} - ${shortDateFormat.format(dateRange.second)}",
            "Pacientes" to statistics.uniquePatients.toString(),
            "Sesiones" to statistics.totalSessions.toString()
        )
        val boxWidth = (right - left - spacing.inner * 2 - spacing.smallGap * 3) / 4f
        boxes.forEachIndexed { index, (label, value) ->
            val boxLeft = left + spacing.inner + index * (boxWidth + spacing.smallGap)
            val boxRect = RectF(boxLeft, infoTop, boxLeft + boxWidth, bottom - spacing.inner)
            canvas.drawRoundRect(boxRect, spacing.radiusMedium, spacing.radiusMedium, paints.overlayPaint)
            canvas.drawText(label.uppercase(), boxRect.left + spacing.cardInset, boxRect.top + spacing.cardInset + paints.overlinePaint.textSize, paints.overlinePaint)
            drawWrappedText(canvas, value, boxRect.left + spacing.cardInset, boxRect.top + spacing.cardInset + paints.overlinePaint.textSize + spacing.smallGap + paints.metricPaint.textSize, boxWidth - spacing.cardInset * 2, paints.metricPaint, spacing.lineHeight)
        }
        return bottom
    }

    private fun drawSummarySection(
        canvas: Canvas,
        statistics: SummaryStats,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val sectionRect = RectF(left, top, right, top + spacing.summarySectionHeight)
        drawSurfaceCard(canvas, sectionRect, paints, spacing)
        val titleY = sectionRect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText("Resumen estadístico", sectionRect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)
        val cardTop = titleY + spacing.mediumGap
        val items = listOf(
            "Completadas" to statistics.completedSessions.toString(),
            "Canceladas" to statistics.cancelledSessions.toString(),
            "Éxito" to "${statistics.successRate}%",
            "Duración prom." to "${statistics.averageDuration} min"
        )
        val cardWidth = (right - left - spacing.smallGap * 3) / 4f
        val cardHeight = spacing.summaryCardHeight
        items.forEachIndexed { index, (label, value) ->
            val cardLeft = left + index * (cardWidth + spacing.smallGap)
            val rect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
            drawSurfaceCard(canvas, rect, paints, spacing)
            canvas.drawText(label.uppercase(), rect.left + spacing.cardInset, rect.top + spacing.cardInset + paints.overlineDarkPaint.textSize, paints.overlineDarkPaint)
            canvas.drawText(value, rect.left + spacing.cardInset, rect.top + spacing.cardInset + paints.overlineDarkPaint.textSize + spacing.mediumGap + paints.metricDarkPaint.textSize, paints.metricDarkPaint)
        }
        return sectionRect.bottom
    }

    private fun drawPatientsSection(
        canvas: Canvas,
        sessions: List<TherapySession>,
        emotionSnapshots: Map<Int, PatientEmotionSnapshot>,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val grouped = sessions.groupBy { it.pacienteId }.values.sortedBy { group ->
            group.firstOrNull()?.paciente?.nombresApellidos ?: ""
        }

        val titleRect = RectF(left, top, right, top + spacing.patientSectionHeaderHeight)
        drawSurfaceCard(canvas, titleRect, paints, spacing)
        canvas.drawText(
            "Pacientes y sesiones",
            titleRect.left + spacing.cardInset,
            titleRect.top + spacing.cardInset + paints.sectionTitlePaint.textSize,
            paints.sectionTitlePaint
        )

        var currentY = titleRect.bottom + spacing.mediumGap
        grouped.forEach { patientSessions ->
            currentY = drawPatientBlock(
                canvas = canvas,
                sessions = patientSessions,
                emotionSnapshot = emotionSnapshots[patientSessions.first().pacienteId],
                left = left,
                right = right,
                top = currentY,
                spacing = spacing,
                paints = paints
            )
            currentY += spacing.cardGap
        }

        if (grouped.isEmpty()) {
            val rect = RectF(left, currentY, right, currentY + spacing.emptyHeight)
            drawSurfaceCard(canvas, rect, paints, spacing)
            canvas.drawText("No hay sesiones dentro del rango seleccionado.", rect.left + spacing.cardInset, rect.top + spacing.cardInset + paints.bodyPaint.textSize, paints.bodyPaint)
            currentY = rect.bottom
        }

        return currentY
    }

    private fun drawPatientBlock(
        canvas: Canvas,
        sessions: List<TherapySession>,
        emotionSnapshot: PatientEmotionSnapshot?,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val patientName = sessions.first().paciente.nombresApellidos
        val caregiverName = sessions.first().cuidador?.nombresApellidos ?: "No asignado"
        val sortedSessions = sessions.sortedBy { it.fechaSesion }
        val blockHeight = spacing.patientHeaderHeight + spacing.patientEmotionHeight + sortedSessions.size * spacing.sessionRowHeight + spacing.cardInset
        val rect = RectF(left, top, right, top + blockHeight)
        drawSurfaceCard(canvas, rect, paints, spacing)

        val headerRect = RectF(rect.left, rect.top, rect.right, rect.top + spacing.patientHeaderHeight)
        canvas.drawRoundRect(headerRect, spacing.radiusLarge, spacing.radiusLarge, paints.patientHeaderPaint)
        canvas.drawText(patientName, headerRect.left + spacing.cardInset, headerRect.top + spacing.cardInset + paints.patientTitlePaint.textSize, paints.patientTitlePaint)
        canvas.drawText("Cuidador: $caregiverName", headerRect.left + spacing.cardInset, headerRect.top + spacing.cardInset + paints.patientTitlePaint.textSize + spacing.smallGap + paints.patientSubtitlePaint.textSize, paints.patientSubtitlePaint)
        val summaryText = "${sortedSessions.size} sesiones"
        val summaryWidth = paints.patientSubtitlePaint.measureText(summaryText)
        canvas.drawText(summaryText, headerRect.right - spacing.cardInset - summaryWidth, headerRect.top + spacing.cardInset + paints.patientSubtitlePaint.textSize, paints.patientSubtitlePaint)

        val emotionRect = RectF(
            rect.left + spacing.cardInset,
            headerRect.bottom + spacing.smallGap,
            rect.right - spacing.cardInset,
            headerRect.bottom + spacing.smallGap + spacing.patientEmotionHeight
        )
        canvas.drawRoundRect(emotionRect, spacing.radiusMedium, spacing.radiusMedium, paints.patientEmotionPaint)
        val emotionTitle = emotionSnapshot?.emotionLabel ?: "Sin estado emocional"
        val emotionPercent = emotionSnapshot?.confidencePercent?.let { "$it%" } ?: "N/D"
        canvas.drawText(
            "Estado emocional",
            emotionRect.left + spacing.cardInset,
            emotionRect.top + spacing.cardInset + paints.overlineDarkPaint.textSize,
            paints.overlineDarkPaint
        )
        canvas.drawText(
            emotionTitle,
            emotionRect.left + spacing.cardInset,
            emotionRect.top + spacing.cardInset + paints.overlineDarkPaint.textSize + spacing.mediumGap + paints.patientTitlePaint.textSize,
            paints.patientTitlePaint
        )
        val percentWidth = paints.metricDarkPaint.measureText(emotionPercent)
        canvas.drawText(
            emotionPercent,
            emotionRect.right - spacing.cardInset - percentWidth,
            emotionRect.top + spacing.cardInset + paints.metricDarkPaint.textSize,
            paints.metricDarkPaint
        )
        val sourceNote = emotionSnapshot?.sourceNote ?: "Sin analisis en ml-service"
        canvas.drawText(
            sourceNote,
            emotionRect.left + spacing.cardInset,
            emotionRect.bottom - spacing.cardInset,
            paints.patientSubtitlePaint
        )

        var y = emotionRect.bottom + spacing.cardInset
        sortedSessions.forEachIndexed { index, session ->
            if (index > 0) {
                canvas.drawLine(left + spacing.cardInset, y - spacing.smallGap, right - spacing.cardInset, y - spacing.smallGap, paints.dividerPaint)
            }
            val rowTop = y
            canvas.drawText("Sesión #${session.id}", left + spacing.cardInset, rowTop + paints.rowTitlePaint.textSize, paints.rowTitlePaint)
            canvas.drawText(formatSessionDate(session.fechaSesion), left + spacing.cardInset, rowTop + paints.rowTitlePaint.textSize + spacing.smallGap + paints.rowBodyPaint.textSize, paints.rowBodyPaint)
            val middleX = left + (right - left) * 0.45f
            canvas.drawText("${session.horaInicio} - ${session.horaFin}", middleX, rowTop + paints.rowTitlePaint.textSize, paints.rowBodyPaint)
            canvas.drawText(session.tipoSesion ?: "Terapia", middleX, rowTop + paints.rowTitlePaint.textSize + spacing.smallGap + paints.rowBodyPaint.textSize, paints.rowBodyPaint)
            val rightText = "${session.duracion} min · ${statusLabel(session.estado)}"
            val rightWidth = paints.rowBodyPaint.measureText(rightText)
            canvas.drawText(rightText, right - spacing.cardInset - rightWidth, rowTop + paints.rowTitlePaint.textSize, paints.rowBodyPaint)
            y += spacing.sessionRowHeight
        }

        return rect.bottom
    }

    private fun drawTextSection(
        canvas: Canvas,
        title: String,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val lines = wrapText(text.replace("\n", " "), paints.bodyPaint, right - left - spacing.cardInset * 2)
        val cardHeight = spacing.cardInset * 2 + paints.sectionTitlePaint.textSize + spacing.mediumGap + lines.size * spacing.lineHeight
        val rect = RectF(left, top, right, top + cardHeight)
        drawSurfaceCard(canvas, rect, paints, spacing)
        val titleY = rect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText(title, rect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)
        var y = titleY + spacing.mediumGap + paints.bodyPaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, rect.left + spacing.cardInset, y, paints.bodyPaint)
            y += spacing.lineHeight
        }
        return rect.bottom
    }

    private fun drawFooter(canvas: Canvas, width: Float, height: Float, spacing: Spacing, paints: Paints) {
        val lineY = height - spacing.outer
        canvas.drawLine(spacing.outer, lineY - spacing.mediumGap, width - spacing.outer, lineY - spacing.mediumGap, paints.dividerPaint)
        canvas.drawText("Reporte generado el ${generatedAtFormat.format(Date())}", spacing.outer, lineY, paints.footerPaint)
        val rightText = "Serious Game · Sistema terapéutico"
        val textWidth = paints.footerPaint.measureText(rightText)
        canvas.drawText(rightText, width - spacing.outer - textWidth, lineY, paints.footerPaint)
    }

    private fun drawSurfaceCard(canvas: Canvas, rect: RectF, paints: Paints, spacing: Spacing) {
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.surfacePaint)
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.strokePaint)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float) {
        var currentY = startY
        wrapText(text, paint, maxWidth).forEach { line ->
            canvas.drawText(line, x, currentY, paint)
            currentY += lineHeight
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(next) <= maxWidth) {
                current = next
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun measureHeight(width: Float, sessions: List<TherapySession>, conclusions: String, recommendations: String): Float {
        val spacing = Spacing(width)
        val paints = Paints(spacing, Palette())
        val grouped = sessions.groupBy { it.pacienteId }.values
        var total = spacing.outer
        total += spacing.headerHeight + spacing.sectionGap
        total += spacing.summarySectionHeight + spacing.sectionGap
        total += spacing.patientSectionHeaderHeight + spacing.mediumGap
        total += grouped.sumOf { (spacing.patientHeaderHeight + it.size * spacing.sessionRowHeight + spacing.cardInset + spacing.cardGap).toDouble() }.toFloat()
        total += textSectionHeight(conclusions, width, spacing, paints) + spacing.cardGap
        total += textSectionHeight(recommendations, width, spacing, paints) + spacing.outer + 50f
        if (grouped.isEmpty()) {
            total += spacing.emptyHeight
        }
        return max(total, 842f)
    }

    private fun textSectionHeight(text: String, width: Float, spacing: Spacing, paints: Paints): Float {
        val lines = wrapText(text.replace("\n", " "), paints.bodyPaint, width - spacing.outer * 2 - spacing.cardInset * 2)
        return paints.sectionTitlePaint.textSize + spacing.mediumGap + spacing.cardInset * 2 + lines.size * spacing.lineHeight
    }

    private fun formatSessionDate(value: String): String {
        return runCatching {
            val parsed = apiDateFormat.parse(value) ?: return value
            longDateFormat.format(parsed)
        }.getOrElse { value }
    }

    private fun statusLabel(status: String?): String {
        return when (status?.lowercase()) {
            "completada" -> "Completada"
            "cancelada" -> "Cancelada"
            "programada" -> "Programada"
            else -> "Pendiente"
        }
    }

    private data class Palette(
        val pageBackground: Int = Color.parseColor("#F3F7FF"),
        val headerStart: Int = Color.parseColor("#4E7BFF"),
        val headerEnd: Int = Color.parseColor("#315DDB"),
        val surface: Int = Color.WHITE,
        val patientHeader: Int = Color.parseColor("#EDF3FF"),
        val stroke: Int = Color.parseColor("#D4DEFF"),
        val accent: Int = Color.parseColor("#4E7BFF"),
        val title: Int = Color.WHITE,
        val subtitle: Int = Color.parseColor("#E8EEFF"),
        val textPrimary: Int = Color.parseColor("#1F2A44"),
        val textSecondary: Int = Color.parseColor("#66738F"),
        val divider: Int = Color.parseColor("#D8E3FF")
    )

    private class Spacing(width: Float) {
        val outer = width * 0.055f
        val inner = width * 0.05f
        val cardInset = width * 0.022f
        val smallGap = width * 0.012f
        val mediumGap = width * 0.02f
        val cardGap = width * 0.024f
        val sectionGap = width * 0.034f
        val lineHeight = width * 0.022f
        val titleLineHeight = width * 0.026f
        val radiusLarge = width * 0.024f
        val radiusMedium = width * 0.018f
        val headerHeight = width * 0.33f
        val headerInfoHeight = width * 0.12f
        val summaryCardHeight = width * 0.14f
        val summarySectionHeight = width * 0.24f
        val patientSectionHeaderHeight = width * 0.09f
        val patientHeaderHeight = width * 0.12f
        val patientEmotionHeight = width * 0.11f
        val sessionRowHeight = width * 0.07f
        val emptyHeight = width * 0.12f
    }

    private class Paints(spacing: Spacing, val palette: Palette) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = spacing.outer * 0.68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.subtitle
            textSize = spacing.outer * 0.28f
        }
        val overlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.subtitle
            textSize = spacing.outer * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val overlineDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = spacing.outer * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.title
            textSize = spacing.outer * 0.24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metricDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = widthToText(spacing, 0.03f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.3f
        }
        val rowTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = spacing.outer * 0.26f
        }
        val patientTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val patientSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = spacing.outer * 0.2f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = spacing.outer * 0.24f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.divider
            strokeWidth = 1f
        }
        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
        }
        val patientHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.patientHeader
        }
        val patientEmotionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F6F9FF")
        }
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(54, 255, 255, 255)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.stroke
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        private fun widthToText(spacing: Spacing, factor: Float): Float = spacing.outer / 0.055f * factor
    }
}
