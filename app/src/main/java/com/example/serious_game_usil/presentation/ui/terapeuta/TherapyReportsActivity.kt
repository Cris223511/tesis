package com.example.serious_game_usil.presentation.ui.terapeuta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.databinding.ActivityTherapyReportsBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.repository.TherapySessionRepository
import com.example.serious_game_usil.service.ReportService
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TherapyReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTherapyReportsBinding
    private lateinit var reportService: ReportService
    private lateinit var repository: TherapySessionRepository

    private var allSessions: List<TherapySession> = emptyList()
    private var selectedSessionId: Int? = null
    private var startDate: Date? = null
    private var endDate: Date? = null

    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val sessionDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AuthManager.init(this)
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        binding = ActivityTherapyReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reportService = ReportService(this)
        repository = TherapySessionRepository()

        setupToolbar()
        setupViews()
        loadSessions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        setupReportTypeToggle()
        setupDatePickers()
        setupQuickDateChips()
        setupFormatChips()
        setupGenerateButton()
        applyReportMode(binding.toggleReportType.checkedButtonId)
    }

    private fun setupReportTypeToggle() {
        binding.toggleReportType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                applyReportMode(checkedId)
            }
        }
    }

    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                startDate = selectedDate.atStartOfDay()
                binding.etStartDate.setText(displayDateFormat.format(startDate!!))
                updatePreview()
            }
        }

        binding.etEndDate.setOnClickListener {
            showDatePicker { selectedDate ->
                endDate = selectedDate.atEndOfDay()
                binding.etEndDate.setText(displayDateFormat.format(endDate!!))
                updatePreview()
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleccionar fecha")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            onDateSelected(Date(selection))
        }

        datePicker.show(supportFragmentManager, "date_picker")
    }

    private fun setupQuickDateChips() {
        binding.chipToday.setOnClickListener { applyQuickDateRange(daySpan = 1) }
        binding.chipWeek.setOnClickListener { applyQuickDateRange(daySpan = 7) }
        binding.chipQuarter.setOnClickListener { applyQuickDateRange(monthSpan = 3) }
        binding.chipSixMonths.setOnClickListener { applyQuickDateRange(monthSpan = 6) }
        binding.chipYear.setOnClickListener { applyQuickDateRange(monthSpan = 12) }
    }

    private fun applyQuickDateRange(daySpan: Int? = null, monthSpan: Int? = null) {
        val endCalendar = Calendar.getInstance().apply { setToEndOfDay() }
        val startCalendar = Calendar.getInstance().apply { setToStartOfDay() }

        if (daySpan != null) {
            startCalendar.add(Calendar.DAY_OF_MONTH, -(daySpan - 1))
        }
        if (monthSpan != null) {
            startCalendar.add(Calendar.MONTH, -monthSpan)
        }

        startDate = startCalendar.time
        endDate = endCalendar.time
        binding.etStartDate.setText(displayDateFormat.format(startDate!!))
        binding.etEndDate.setText(displayDateFormat.format(endDate!!))
        updatePreview()
    }

    private fun setupFormatChips() {
        binding.chipGroupFormat.setOnCheckedStateChangeListener { _, _ ->
            updatePreview()
        }
    }

    private fun setupGenerateButton() {
        binding.fabGenerateReport.setOnClickListener {
            generateReport()
        }
    }

    private fun applyReportMode(checkedId: Int) {
        val isIndividual = checkedId == R.id.btnIndividualReport

        binding.cardSessionSelection.visibility = if (isIndividual) View.VISIBLE else View.GONE
        binding.cardDateRange.visibility = if (isIndividual) View.GONE else View.VISIBLE
        binding.switchIncludeConclusions.isEnabled = !isIndividual
        binding.switchIncludeConclusions.alpha = if (isIndividual) 0.5f else 1f

        binding.tvHeaderMode.text = if (isIndividual) {
            "Modo actual: Individual"
        } else {
            "Modo actual: General"
        }
        binding.tvHeaderSupport.text = if (isIndividual) {
            "Exporta una sesión puntual con contexto clínico, horario y análisis asociado."
        } else {
            "Consolida continuidad, volumen y resultados del programa dentro de un rango de fechas."
        }
        binding.tvModeDescription.text = if (isIndividual) {
            "Ideal para compartir el detalle de una sesión concreta sin perder claridad visual."
        } else {
            "Útil para seguimiento integral, reportes periódicos y revisión del avance terapéutico."
        }

        updatePreview()
    }

    private fun loadSessions() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            repository.getSessions().collect { result ->
                binding.progressBar.visibility = View.GONE

                when (result) {
                    is ApiResult.Success -> {
                        allSessions = result.data.sortedByDescending { session ->
                            parseSessionDate(session.fechaSesion)?.time ?: Long.MIN_VALUE
                        }
                        setupSessionsDropdown()
                        updatePreview()
                    }
                    is ApiResult.Error -> showError("Error cargando sesiones: ${result.message}")
                    is ApiResult.NetworkError -> showError("Error de conexión")
                }
            }
        }
    }

    private fun setupSessionsDropdown() {
        val sessionItems = allSessions.map { session ->
            val formattedDate = parseSessionDate(session.fechaSesion)?.let(displayDateFormat::format)
                ?: session.fechaSesion
            "${session.paciente.nombresApellidos} • $formattedDate"
        }

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            sessionItems
        )

        binding.autoCompleteSession.setAdapter(adapter)
        binding.autoCompleteSession.setOnItemClickListener { _, _, position, _ ->
            selectedSessionId = allSessions[position].id
            updatePreview()
        }
    }

    private fun updatePreview() {
        val previewModel = buildPreviewModel()
        renderPreview(previewModel)
        binding.fabGenerateReport.text = previewModel.actionLabel
    }

    private fun buildPreviewModel(): ReportPreviewUiModel {
        val isIndividual = binding.btnIndividualReport.isChecked
        val formatLabel = getSelectedFormatLabel()

        return if (isIndividual) {
            val selectedSession = allSessions.firstOrNull { it.id == selectedSessionId }
            if (selectedSession == null) {
                ReportPreviewFormatter.emptyIndividual(formatLabel)
            } else {
                ReportPreviewFormatter.individual(
                    session = selectedSession,
                    formatLabel = formatLabel,
                    includeAnalysis = binding.switchIncludeAnalysis.isChecked
                )
            }
        } else {
            if (startDate == null || endDate == null) {
                ReportPreviewFormatter.emptyGeneral(formatLabel)
            } else {
                ReportPreviewFormatter.general(
                    sessions = filterSessionsByDate(),
                    startDate = startDate!!,
                    endDate = endDate!!,
                    formatLabel = formatLabel,
                    includeConclusions = binding.switchIncludeConclusions.isChecked
                )
            }
        }
    }

    private fun renderPreview(model: ReportPreviewUiModel) {
        binding.cardPreview.visibility = View.VISIBLE
        binding.tvPreviewBadge.text = model.badge
        binding.tvPreviewTitle.text = model.title
        binding.tvPreviewSubtitle.text = model.subtitle
        binding.tvPreviewMetricOneValue.text = model.metricOneValue
        binding.tvPreviewMetricOneLabel.text = model.metricOneLabel
        binding.tvPreviewMetricTwoValue.text = model.metricTwoValue
        binding.tvPreviewMetricTwoLabel.text = model.metricTwoLabel
        binding.tvPreviewMetricThreeValue.text = model.metricThreeValue
        binding.tvPreviewMetricThreeLabel.text = model.metricThreeLabel
        binding.tvPreviewBody.text = model.body
        binding.tvPreviewChecklist.text = model.checklist
    }

    private fun filterSessionsByDate(): List<TherapySession> {
        if (startDate == null || endDate == null) return emptyList()

        return allSessions.filter { session ->
            val sessionDate = parseSessionDate(session.fechaSesion) ?: return@filter false
            sessionDate >= startDate && sessionDate <= endDate
        }
    }

    private fun parseSessionDate(value: String): Date? {
        return runCatching { sessionDateFormat.parse(value) }.getOrNull()
    }

    private fun Date.atStartOfDay(): Date {
        return Calendar.getInstance().apply {
            time = this@atStartOfDay
            setToStartOfDay()
        }.time
    }

    private fun Date.atEndOfDay(): Date {
        return Calendar.getInstance().apply {
            time = this@atEndOfDay
            setToEndOfDay()
        }.time
    }

    private fun Calendar.setToStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    private fun generateReport() {
        val isIndividual = binding.btnIndividualReport.isChecked

        if (isIndividual && selectedSessionId == null) {
            showError("Selecciona una sesión antes de generar el reporte")
            return
        }

        if (!isIndividual && (startDate == null || endDate == null)) {
            showError("Selecciona el rango de fechas del reporte")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.fabGenerateReport.isEnabled = false

        lifecycleScope.launch {
            try {
                val format = getSelectedFormat()
                if (isIndividual) {
                    generateIndividualReport(selectedSessionId!!, format)
                } else {
                    generateGeneralReport(format)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.fabGenerateReport.isEnabled = true
                showError("Error generando reporte: ${e.message}")
            }
        }
    }

    private suspend fun generateIndividualReport(sessionId: Int, format: ReportService.ReportFormat) {
        reportService.generateSessionReport(
            sessionId = sessionId,
            format = format,
            includeEmotionAnalysis = binding.switchIncludeAnalysis.isChecked
        ).collect { result ->
            binding.progressBar.visibility = View.GONE
            binding.fabGenerateReport.isEnabled = true

            result.fold(
                onSuccess = { report -> showSuccessDialog(report.filePath) },
                onFailure = { error -> showError("Error: ${error.message}") }
            )
        }
    }

    private suspend fun generateGeneralReport(format: ReportService.ReportFormat) {
        val therapistFilterId = if (AuthManager.isAdmin()) {
            null
        } else {
            AuthManager.getUserId()
        }

        reportService.generateGeneralReport(
            format = format,
            therapistId = therapistFilterId,
            startDate = startDate,
            endDate = endDate
        ).collect { result ->
            binding.progressBar.visibility = View.GONE
            binding.fabGenerateReport.isEnabled = true

            result.fold(
                onSuccess = { report -> showSuccessDialog(report.filePath) },
                onFailure = { error -> showError("Error: ${error.message}") }
            )
        }
    }

    private fun getSelectedFormat(): ReportService.ReportFormat {
        return when (binding.chipGroupFormat.checkedChipId) {
            R.id.chipImage -> ReportService.ReportFormat.JPG
            else -> ReportService.ReportFormat.PDF
        }
    }

    private fun getSelectedFormatLabel(): String {
        return when (getSelectedFormat()) {
            ReportService.ReportFormat.JPG -> "Imagen"
            ReportService.ReportFormat.HTML -> "HTML"
            ReportService.ReportFormat.JSON -> "JSON"
            ReportService.ReportFormat.PDF -> "PDF"
        }
    }

    private fun showSuccessDialog(filePath: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reporte generado")
            .setMessage("El archivo está listo para abrir o compartir.")
            .setPositiveButton("Abrir") { _, _ -> filePath?.let(::openFile) }
            .setNeutralButton("Compartir") { _, _ -> filePath?.let(::shareFile) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun openFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)
        } catch (_: Exception) {
            showError("No se puede abrir el archivo")
        }
    }

    private fun shareFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Compartir reporte"))
        } catch (_: Exception) {
            showError("Error compartiendo archivo")
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.error))
            .show()
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, TherapyReportsActivity::class.java)
        }
    }
}
