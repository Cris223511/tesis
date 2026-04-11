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

class SessionReportDocumentRenderer {

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))
    private val generatedAtFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun renderPdf(
        document: PdfDocument,
        session: TherapySession,
        includeAnalysis: Boolean,
        emotionSnapshot: com.example.serious_game_usil.service.ReportService.EmotionSnapshot? = null
    ): PdfDocument {
        val width = 595
        val height = max(842, measureContentHeight(width.toFloat(), session, includeAnalysis).toInt())
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
        val page = document.startPage(pageInfo)
        drawReport(page.canvas, width.toFloat(), height.toFloat(), session, includeAnalysis, emotionSnapshot)
        document.finishPage(page)
        return document
    }

    fun renderBitmap(
        session: TherapySession,
        includeAnalysis: Boolean,
        emotionSnapshot: com.example.serious_game_usil.service.ReportService.EmotionSnapshot? = null
    ): Bitmap {
        val width = 1440f
        val height = measureContentHeight(width, session, includeAnalysis)
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawReport(canvas, width, height, session, includeAnalysis, emotionSnapshot)
        return bitmap
    }

    private fun drawReport(
        canvas: Canvas,
        width: Float,
        height: Float,
        session: TherapySession,
        includeAnalysis: Boolean,
        emotionSnapshot: com.example.serious_game_usil.service.ReportService.EmotionSnapshot?
    ) {
        val palette = Palette()
        val spacing = Spacing(width)
        val paints = Paints(spacing, palette)

        canvas.drawColor(palette.pageBackground)

        val contentLeft = spacing.outer
        val contentRight = width - spacing.outer
        var currentY = spacing.outer

        currentY = drawHeader(canvas, session, contentLeft, contentRight, currentY, spacing, paints)
        currentY += spacing.sectionGap

        currentY = drawInfoGrid(
            canvas = canvas,
            title = "Información principal",
            cards = listOf(
                "Paciente" to session.paciente.nombresApellidos,
                "Cuidador" to (session.cuidador?.nombresApellidos ?: "No asignado"),
                "Terapeuta" to session.terapeuta.nombresApellidos
            ),
            left = contentLeft,
            right = contentRight,
            top = currentY,
            spacing = spacing,
            paints = paints
        )
        currentY += spacing.sectionGap

        currentY = drawDetailsSection(canvas, session, contentLeft, contentRight, currentY, spacing, paints)
        currentY += spacing.sectionGap

        currentY = drawBodySection(
            canvas = canvas,
            title = "Objetivos",
            body = formatList(session.objetivos, "No se registraron objetivos."),
            left = contentLeft,
            right = contentRight,
            top = currentY,
            spacing = spacing,
            paints = paints
        )
        currentY += spacing.cardGap

        currentY = drawBodySection(
            canvas = canvas,
            title = "Descripción",
            body = session.descripcion ?: "No se registró descripción para esta sesión.",
            left = contentLeft,
            right = contentRight,
            top = currentY,
            spacing = spacing,
            paints = paints
        )
        currentY += spacing.cardGap

        currentY = drawBodySection(
            canvas = canvas,
            title = "Materiales",
            body = formatList(session.materiales, "No se registraron materiales."),
            left = contentLeft,
            right = contentRight,
            top = currentY,
            spacing = spacing,
            paints = paints
        )
        currentY += spacing.cardGap

        currentY = drawBodySection(
            canvas = canvas,
            title = "Notas del terapeuta",
            body = session.notasTerapeuta ?: "Sin observaciones registradas.",
            left = contentLeft,
            right = contentRight,
            top = currentY,
            spacing = spacing,
            paints = paints
        )
        currentY += spacing.sectionGap

        if (includeAnalysis) {
            currentY = drawAnalysisPlaceholder(canvas, session, emotionSnapshot, contentLeft, contentRight, currentY, spacing, paints)
            currentY += spacing.sectionGap
        }

        drawFooter(canvas, width, height, spacing, paints)
    }

    private fun drawHeader(
        canvas: Canvas,
        session: TherapySession,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val bottom = top + spacing.headerHeight
        val rect = RectF(left, top, right, bottom)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, gradientPaint)

        val chipText = statusLabel(session.estado)
        val chipWidth = max(spacing.statusChipMinWidth, paints.chipTextPaint.measureText(chipText) + spacing.inner)
        val chipRect = RectF(
            right - spacing.inner - chipWidth,
            top + spacing.inner,
            right - spacing.inner,
            top + spacing.inner + spacing.chipHeight
        )
        canvas.drawRoundRect(chipRect, spacing.chipHeight / 2f, spacing.chipHeight / 2f, paints.chipBackgroundPaint)
        canvas.drawText(
            chipText,
            chipRect.centerX() - paints.chipTextPaint.measureText(chipText) / 2f,
            chipRect.top + spacing.chipHeight / 2f + paints.chipTextPaint.textSize / 3f,
            paints.chipTextPaint
        )

        val titleStartX = left + spacing.inner
        val titleMaxWidth = chipRect.left - titleStartX - spacing.mediumGap
        val titleLines = wrapText("Reporte de sesión terapéutica", paints.titlePaint, titleMaxWidth)
        var titleY = top + spacing.inner + paints.titlePaint.textSize
        titleLines.forEach { line ->
            canvas.drawText(line, titleStartX, titleY, paints.titlePaint)
            titleY += spacing.titleLineHeight
        }

        val subtitleY = titleY + spacing.smallGap
        canvas.drawText(
            "Seguimiento clínico y operativo de la sesión",
            titleStartX,
            subtitleY,
            paints.subtitlePaint
        )

        val infoTop = bottom - spacing.infoStripHeight
        val columns = listOf(
            "Sesión" to "#${session.id}",
            "Fecha" to formatSessionDate(session.fechaSesion),
            "Horario" to "${session.horaInicio} - ${session.horaFin}",
            "Duración" to "${session.duracion} min"
        )
        val boxWidth = (right - left - spacing.inner * 2 - spacing.smallGap * 3) / 4f

        columns.forEachIndexed { index, (label, value) ->
            val cardLeft = left + spacing.inner + (boxWidth + spacing.smallGap) * index
            val cardRect = RectF(cardLeft, infoTop, cardLeft + boxWidth, bottom - spacing.inner)
            canvas.drawRoundRect(cardRect, spacing.radiusMedium, spacing.radiusMedium, paints.overlayCardPaint)
            canvas.drawText(label.uppercase(), cardRect.left + spacing.cardInset, cardRect.top + spacing.cardInset + paints.overlinePaint.textSize, paints.overlinePaint)
            drawWrappedText(
                canvas,
                value,
                cardRect.left + spacing.cardInset,
                cardRect.top + spacing.cardInset + paints.overlinePaint.textSize + spacing.smallGap + paints.metricPaint.textSize,
                boxWidth - spacing.cardInset * 2,
                paints.metricPaint,
                spacing.lineHeight
            )
        }

        return bottom
    }

    private fun drawInfoGrid(
        canvas: Canvas,
        title: String,
        cards: List<Pair<String, String>>,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val containerRect = RectF(left, top, right, top + spacing.infoSectionHeight)
        drawSurfaceCard(canvas, containerRect, paints, spacing)
        val titleY = containerRect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText(title, containerRect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)

        val cardTop = titleY + spacing.mediumGap
        val availableWidth = right - left
        val cardWidth = (availableWidth - spacing.smallGap * 2) / 3f
        val cardHeight = spacing.smallInfoCardHeight

        cards.forEachIndexed { index, (label, value) ->
            val cardLeft = left + (cardWidth + spacing.smallGap) * index
            val rect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
            drawSurfaceCard(canvas, rect, paints, spacing)
            canvas.drawText(label.uppercase(), rect.left + spacing.cardInset, rect.top + spacing.cardInset + paints.overlineDarkPaint.textSize, paints.overlineDarkPaint)
            drawWrappedText(
                canvas,
                value,
                rect.left + spacing.cardInset,
                rect.top + spacing.cardInset + paints.overlineDarkPaint.textSize + spacing.smallGap + paints.cardValuePaint.textSize,
                cardWidth - spacing.cardInset * 2,
                paints.cardValuePaint,
                spacing.lineHeight
            )
        }

        return containerRect.bottom
    }

    private fun drawDetailsSection(
        canvas: Canvas,
        session: TherapySession,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val rect = RectF(left, top, right, top + spacing.detailsHeight)
        drawSurfaceCard(canvas, rect, paints, spacing)
        val titleY = rect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText("Detalles de la sesión", rect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)

        val rows = listOf(
            "Número de sesión" to "Sesión ${session.id}",
            "Fecha" to formatSessionDate(session.fechaSesion),
            "Hora" to "${session.horaInicio} - ${session.horaFin}",
            "Duración" to "${session.duracion} minutos",
            "Tipo" to (session.tipoSesion ?: "Terapia individual"),
            "Modalidad" to (session.modalidad ?: "Presencial"),
            "Ubicación" to (session.ubicacion ?: session.direccion ?: "No especificada")
        )

        var y = titleY + spacing.mediumGap + paints.rowLabelPaint.textSize
        rows.forEach { (label, value) ->
            canvas.drawText("$label:", rect.left + spacing.cardInset, y, paints.rowLabelPaint)
            drawWrappedText(canvas, value, rect.left + spacing.detailsValueX, y, rect.width() - spacing.detailsValueX - spacing.cardInset, paints.rowValuePaint, spacing.lineHeight)
            y += spacing.rowSpacing
        }

        return rect.bottom
    }

    private fun drawBodySection(
        canvas: Canvas,
        title: String,
        body: String,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val bodyLines = wrapText(body, paints.bodyPaint, right - left - spacing.cardInset * 2)
        val cardHeight = spacing.cardInset * 2 + paints.sectionTitlePaint.textSize + spacing.mediumGap + bodyLines.size * spacing.lineHeight + spacing.smallGap
        val rect = RectF(left, top, right, top + cardHeight)
        drawSurfaceCard(canvas, rect, paints, spacing)
        val titleY = rect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText(title, rect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)

        var y = titleY + spacing.mediumGap + paints.bodyPaint.textSize
        bodyLines.forEach { line ->
            canvas.drawText(line, rect.left + spacing.cardInset, y, paints.bodyPaint)
            y += spacing.lineHeight
        }

        return rect.bottom
    }

    private fun drawAnalysisPlaceholder(
        canvas: Canvas,
        session: TherapySession,
        emotionSnapshot: com.example.serious_game_usil.service.ReportService.EmotionSnapshot?,
        left: Float,
        right: Float,
        top: Float,
        spacing: Spacing,
        paints: Paints
    ): Float {
        val rect = RectF(left, top, right, top + spacing.analysisHeight)
        drawHighlightCard(canvas, rect, paints, spacing)
        val titleY = rect.top + spacing.cardInset + paints.sectionTitlePaint.textSize
        canvas.drawText("Análisis de la sesión", rect.left + spacing.cardInset, titleY, paints.sectionTitlePaint)

        val summary = listOf(
            "Estado final" to statusLabel(session.estado),
            "Emoción" to (emotionSnapshot?.emotionLabel ?: "Sin datos"),
            "Porcentaje" to (emotionSnapshot?.confidencePercent?.let { "$it%" } ?: "N/D")
        )
        val boxWidth = (rect.width() - spacing.cardInset * 2 - spacing.smallGap * 2) / 3f
        summary.forEachIndexed { index, (label, value) ->
            val boxLeft = rect.left + spacing.cardInset + index * (boxWidth + spacing.smallGap)
            val boxTop = titleY + spacing.mediumGap
            val boxRect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + spacing.analysisBoxHeight)
            canvas.drawRoundRect(boxRect, spacing.radiusMedium, spacing.radiusMedium, paints.surfaceSoftPaint)
            canvas.drawText(label.uppercase(), boxRect.left + spacing.cardInset, boxRect.top + spacing.cardInset + paints.overlineDarkPaint.textSize, paints.overlineDarkPaint)
            drawWrappedText(
                canvas,
                value,
                boxRect.left + spacing.cardInset,
                boxRect.top + spacing.cardInset + paints.overlineDarkPaint.textSize + spacing.smallGap + paints.cardValuePaint.textSize,
                boxWidth - spacing.cardInset * 2,
                paints.cardValuePaint,
                spacing.lineHeight
            )
        }

        val note = emotionSnapshot?.let {
            "${it.sourceNote}: ${it.emotionLabel} con ${it.confidencePercent}% de confianza."
        } ?: "No se encontró un análisis emocional asociado en ml-service para este paciente."
        val noteLines = wrapText(note, paints.bodyMutedPaint, rect.width() - spacing.cardInset * 2)
        var y = titleY + spacing.mediumGap + spacing.analysisBoxHeight + spacing.mediumGap + paints.bodyMutedPaint.textSize
        noteLines.forEach { line ->
            canvas.drawText(line, rect.left + spacing.cardInset, y, paints.bodyMutedPaint)
            y += spacing.lineHeight
        }

        return rect.bottom
    }

    private fun drawFooter(canvas: Canvas, width: Float, height: Float, spacing: Spacing, paints: Paints) {
        val lineY = height - spacing.outer
        canvas.drawLine(spacing.outer, lineY - spacing.mediumGap, width - spacing.outer, lineY - spacing.mediumGap, paints.dividerPaint)
        canvas.drawText("Reporte generado el ${generatedAtFormat.format(Date())}", spacing.outer, lineY, paints.footerPaint)
        val footerRight = "Serious Game · Sistema terapéutico"
        val textWidth = paints.footerPaint.measureText(footerRight)
        canvas.drawText(footerRight, width - spacing.outer - textWidth, lineY, paints.footerPaint)
    }

    private fun drawSurfaceCard(canvas: Canvas, rect: RectF, paints: Paints, spacing: Spacing) {
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.surfacePaint)
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.surfaceStrokePaint)
    }

    private fun drawHighlightCard(canvas: Canvas, rect: RectF, paints: Paints, spacing: Spacing) {
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.highlightPaint)
        canvas.drawRoundRect(rect, spacing.radiusLarge, spacing.radiusLarge, paints.surfaceStrokePaint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ) {
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

    private fun measureContentHeight(width: Float, session: TherapySession, includeAnalysis: Boolean): Float {
        val spacing = Spacing(width)
        val paints = Paints(spacing, Palette())
        val bodyWidth = width - spacing.outer * 2 - spacing.cardInset * 2

        var total = spacing.outer
        total += spacing.headerHeight + spacing.sectionGap
        total += spacing.infoSectionHeight + spacing.sectionGap
        total += spacing.detailsHeight + spacing.sectionGap

        total += sectionHeight(formatList(session.objetivos, "No se registraron objetivos."), paints.bodyPaint, bodyWidth, spacing)
        total += spacing.cardGap
        total += sectionHeight(session.descripcion ?: "No se registró descripción para esta sesión.", paints.bodyPaint, bodyWidth, spacing)
        total += spacing.cardGap
        total += sectionHeight(formatList(session.materiales, "No se registraron materiales."), paints.bodyPaint, bodyWidth, spacing)
        total += spacing.cardGap
        total += sectionHeight(session.notasTerapeuta ?: "Sin observaciones registradas.", paints.bodyPaint, bodyWidth, spacing)
        total += spacing.sectionGap

        if (includeAnalysis) {
            total += spacing.analysisHeight + spacing.sectionGap
        }

        total += spacing.outer + 40f
        return total
    }

    private fun sectionHeight(text: String, paint: Paint, width: Float, spacing: Spacing): Float {
        val lines = wrapText(text, paint, width)
        return paintsSectionTitleHeight(spacing) + spacing.mediumGap + spacing.cardInset * 2 + lines.size * spacing.lineHeight + spacing.smallGap
    }

    private fun paintsSectionTitleHeight(spacing: Spacing): Float = spacing.sectionTitleSize

    private fun formatSessionDate(value: String): String {
        return runCatching {
            val date = apiDateFormat.parse(value) ?: return value
            outputDateFormat.format(date)
        }.getOrElse { value }
    }

    private fun formatList(items: List<String>?, emptyText: String): String {
        if (items.isNullOrEmpty()) return emptyText
        return items.joinToString(separator = "\n") { "• $it" }
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
        val headerStart: Int = Color.parseColor("#4D7CFE"),
        val headerEnd: Int = Color.parseColor("#2F5FE8"),
        val surface: Int = Color.WHITE,
        val surfaceSoft: Int = Color.parseColor("#EEF4FF"),
        val highlight: Int = Color.parseColor("#E8F0FF"),
        val stroke: Int = Color.parseColor("#D4DEFF"),
        val title: Int = Color.WHITE,
        val subtitle: Int = Color.parseColor("#E8EEFF"),
        val textPrimary: Int = Color.parseColor("#1F2A44"),
        val textSecondary: Int = Color.parseColor("#66738F"),
        val accent: Int = Color.parseColor("#4E7BFF"),
        val chipBackground: Int = Color.parseColor("#EDF7EF"),
        val chipText: Int = Color.parseColor("#4C9A67"),
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
        val sectionAccentWidth = width * 0.05f
        val sectionAccentHeight = width * 0.008f
        val headerHeight = width * 0.33f
        val infoStripHeight = width * 0.12f
        val smallInfoCardHeight = width * 0.18f
        val infoSectionHeight = width * 0.27f
        val detailsHeight = width * 0.34f
        val analysisHeight = width * 0.26f
        val analysisBoxHeight = width * 0.11f
        val chipHeight = width * 0.04f
        val statusChipMinWidth = width * 0.13f
        val rowSpacing = width * 0.038f
        val detailsValueX = width * 0.22f
        val sectionTitleSize = width * 0.03f
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
        val chipBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.chipBackground
        }
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.chipText
            textSize = spacing.outer * 0.34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.sectionTitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = spacing.outer * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.28f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = spacing.outer * 0.3f
        }
        val bodyMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary
            textSize = spacing.outer * 0.28f
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
        val overlayCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(54, 255, 255, 255)
        }
        val surfaceSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surfaceSoft
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.highlight
        }
        val surfaceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.stroke
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
    }
}
