package com.example.serious_game_usil.presentation.ui.terapeuta

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.service.ReportService
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GenerateSessionReportActivity : AppCompatActivity() {

    private lateinit var reportService: ReportService
    private lateinit var progressBar: ProgressBar
    private lateinit var contentLayout: LinearLayout
    private lateinit var previewCard: CardView
    private lateinit var chipGroupFormat: ChipGroup
    private lateinit var switchEmotionAnalysis: Switch
    private lateinit var btnGenerate: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var fabBack: FloatingActionButton

    private lateinit var tvSessionInfo: TextView
    private lateinit var tvPatientName: TextView
    private lateinit var tvTherapistName: TextView
    private lateinit var tvSessionDate: TextView
    private lateinit var tvSessionStatus: TextView
    private lateinit var tvReportPath: TextView

    private var sessionId: Int = -1
    private var selectedFormat = ReportService.ReportFormat.PDF
    private var generatedReportPath: String? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AuthManager.init(this)
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (sessionId == -1) {
            Toast.makeText(this, "Error: ID de sesión no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_generate_session_report)
        reportService = ReportService(this)
        setupViews()
        loadSessionInfo()
    }

    private fun setupViews() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Generar Reporte de Sesión"
        }
        toolbar.setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        contentLayout = findViewById(R.id.contentLayout)
        previewCard = findViewById(R.id.previewCard)
        chipGroupFormat = findViewById(R.id.chipGroupFormat)
        switchEmotionAnalysis = findViewById(R.id.switchEmotionAnalysis)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnShare = findViewById(R.id.btnShare)
        btnDownload = findViewById(R.id.btnDownload)
        fabBack = findViewById(R.id.fabBack)

        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        tvPatientName = findViewById(R.id.tvPatientName)
        tvTherapistName = findViewById(R.id.tvTherapistName)
        tvSessionDate = findViewById(R.id.tvSessionDate)
        tvSessionStatus = findViewById(R.id.tvSessionStatus)
        tvReportPath = findViewById(R.id.tvReportPath)

        setupFormatSelection()
        setupButtons()
    }

    private fun setupFormatSelection() {
        chipGroupFormat.removeAllViews()

        val formats = listOf(
            ReportService.ReportFormat.PDF to "PDF",
            ReportService.ReportFormat.JPG to "Imagen",
            ReportService.ReportFormat.HTML to "HTML",
            ReportService.ReportFormat.JSON to "JSON"
        )

        formats.forEach { (format, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = format == selectedFormat
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedFormat = format
                        updateFormatDescription()
                    }
                }
            }
            chipGroupFormat.addView(chip)
        }
    }

    private fun updateFormatDescription() {
        val description = when (selectedFormat) {
            ReportService.ReportFormat.PDF -> "Documento profesional con formato completo"
            ReportService.ReportFormat.JPG -> "Imagen para compartir rápidamente"
            ReportService.ReportFormat.HTML -> "Página web interactiva"
            ReportService.ReportFormat.JSON -> "Datos estructurados para integración"
        }
        findViewById<TextView>(R.id.tvFormatDescription)?.text = description
    }

    private fun setupButtons() {
        btnGenerate.setOnClickListener {
            generateReport()
        }

        btnShare.setOnClickListener {
            generatedReportPath?.let { path ->
                shareReport(path)
            } ?: run {
                Snackbar.make(contentLayout, "Primero genera un reporte", Snackbar.LENGTH_SHORT).show()
            }
        }

        btnDownload.setOnClickListener {
            generatedReportPath?.let { path ->
                showDownloadSuccess(path)
            } ?: run {
                Snackbar.make(contentLayout, "Primero genera un reporte", Snackbar.LENGTH_SHORT).show()
            }
        }

        fabBack.setOnClickListener {
            finish()
        }
    }

    private fun loadSessionInfo() {
        tvSessionInfo.text = "Cargando información de sesión #$sessionId..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            reportService.generateSessionReport(
                sessionId = sessionId,
                format = ReportService.ReportFormat.JSON,
                includeEmotionAnalysis = false
            ).collect { result ->
                progressBar.visibility = View.GONE
                result.fold(
                    onSuccess = { report ->
                        displaySessionPreview(report.session)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@GenerateSessionReportActivity,
                            "Error cargando sesión: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    private fun displaySessionPreview(session: com.example.serious_game_usil.`interface`.TherapySession) {
        tvSessionInfo.text = "Sesión #$sessionId"
        tvPatientName.text = "Paciente: ${session.paciente.nombresApellidos}"
        tvTherapistName.text = "Terapeuta: ${session.terapeuta.nombresApellidos}"
        tvSessionDate.text = "Fecha: ${session.fechaSesion} ${session.horaInicio}"
        tvSessionStatus.text = "Estado: ${getStatusText(session.estado)}"
        previewCard.visibility = View.VISIBLE
    }

    private fun generateReport() {
        progressBar.visibility = View.VISIBLE
        btnGenerate.isEnabled = false
        btnShare.visibility = View.GONE
        btnDownload.visibility = View.GONE

        val includeEmotions = switchEmotionAnalysis.isChecked

        lifecycleScope.launch {
            reportService.generateSessionReport(
                sessionId = sessionId,
                format = selectedFormat,
                includeEmotionAnalysis = includeEmotions
            ).collect { result ->
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true

                result.fold(
                    onSuccess = { report ->
                        generatedReportPath = report.filePath
                        tvReportPath.text = "Reporte generado: ${File(report.filePath).name}"
                        btnShare.visibility = View.VISIBLE
                        btnDownload.visibility = View.VISIBLE

                        Snackbar.make(
                            contentLayout,
                            "Reporte generado exitosamente",
                            Snackbar.LENGTH_LONG
                        ).setAction("Ver") {
                            openReport(report.filePath)
                        }.show()
                    },
                    onFailure = { error ->
                        MaterialAlertDialogBuilder(this@GenerateSessionReportActivity)
                            .setTitle("Error")
                            .setMessage("No se pudo generar el reporte: ${error.message}")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                )
            }
        }
    }

    private fun shareReport(path: String?) {
        path?.let {
            val file = File(it)
            val uri = Uri.fromFile(file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = when (selectedFormat) {
                    ReportService.ReportFormat.PDF -> "application/pdf"
                    ReportService.ReportFormat.JPG -> "image/jpeg"
                    ReportService.ReportFormat.HTML -> "text/html"
                    ReportService.ReportFormat.JSON -> "application/json"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reporte de Sesión #$sessionId")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el reporte de la sesión terapéutica #$sessionId")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Compartir reporte"))
        }
    }

    private fun openReport(path: String?) {
        path?.let {
            val file = File(it)
            val uri = Uri.fromFile(file)

            val openIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, getMimeType())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No hay aplicación para abrir este archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMimeType(): String {
        return when (selectedFormat) {
            ReportService.ReportFormat.PDF -> "application/pdf"
            ReportService.ReportFormat.JPG -> "image/jpeg"
            ReportService.ReportFormat.HTML -> "text/html"
            ReportService.ReportFormat.JSON -> "application/json"
        }
    }

    private fun showDownloadSuccess(path: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Descarga Completa")
            .setMessage("El reporte se ha guardado en:\n${File(path).name}")
            .setPositiveButton("Abrir") { _, _ ->
                openReport(path)
            }
            .setNeutralButton("Compartir") { _, _ ->
                shareReport(path)
            }
            .setNegativeButton("Cerrar", null)
            .show()
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

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"

        fun newIntent(context: Context, sessionId: Int): Intent {
            return Intent(context, GenerateSessionReportActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
    }
}