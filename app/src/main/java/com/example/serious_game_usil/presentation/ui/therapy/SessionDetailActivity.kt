package com.example.serious_game_usil.presentation.ui.therapy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.TherapySession
import com.seriousgame.app.navigation.RouteNavigator
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SessionDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: TherapySessionViewModel
    private lateinit var progressContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView

    // Views
    private lateinit var tvPatientName: TextView
    private lateinit var tvTherapistName: TextView
    private lateinit var tvCaregiverName: TextView
    private lateinit var tvSessionDate: TextView
    private lateinit var tvSessionTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvStatus: Chip
    private lateinit var tvDescription: TextView
    private lateinit var tvObjectives: TextView
    private lateinit var tvMaterials: TextView

    // Action buttons
    private lateinit var btnEditSession: MaterialButton
    private lateinit var btnDeleteSession: MaterialButton
    private lateinit var btnExportPdf: MaterialButton

    private var sessionId: Int = -1
    private var currentSession: TherapySession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar autenticación
        AuthManager.init(this)
        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        // Configurar token
        AuthManager.getAccessToken()?.let { token ->
            com.example.serious_game_usil.network.RetrofitClient.setAuthToken(token)
        } ?: run {
            RouteNavigator.navigateToLogin(this)
            return
        }

        sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (sessionId == -1) {
            Toast.makeText(this, "Error: ID de sesión no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_session_detail)
        setupViews()
        setupViewModel()
        loadSessionDetail()
    }

    private fun setupViews() {
        // Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Progress elements
        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)

        // Show loading initially
        progressContainer.visibility = View.VISIBLE

        // Patient info
        tvPatientName = findViewById(R.id.textPatientName)

        // Therapist info
        tvTherapistName = findViewById(R.id.textTherapistName)

        // Caregiver info
        tvCaregiverName = findViewById(R.id.textCaregiverNameDetail)

        // Schedule info
        tvSessionDate = findViewById(R.id.textSessionDate)
        tvSessionTime = findViewById(R.id.textSessionTime)
        tvDuration = findViewById(R.id.textDuration)

        // Session details
        tvStatus = findViewById(R.id.chipStatus)
        tvDescription = findViewById(R.id.textDescription)
        tvObjectives = findViewById(R.id.textObjectives)
        tvMaterials = findViewById(R.id.textMaterials)
        tvLocation = findViewById(R.id.textLocation)
        tvAddress = findViewById(R.id.textAddress)

        // Action buttons
        btnEditSession = findViewById(R.id.btnEditSession)
        btnDeleteSession = findViewById(R.id.btnDeleteSession)
        btnExportPdf = findViewById(R.id.btnExportPdf)

        // Set up button click listeners
        setupActionButtons()
    }

    private fun setupActionButtons() {
        btnEditSession.setOnClickListener {
            currentSession?.let { session ->
                showEditSessionDialog(session)
            }
        }

        btnDeleteSession.setOnClickListener {
            currentSession?.let { session ->
                showDeleteConfirmDialog(session)
            }
        }

        btnExportPdf.setOnClickListener {
            currentSession?.let { session ->
                exportSessionToPdf(session)
            }
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TherapySessionViewModel::class.java]

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // No hacemos nada aquí porque manejamos el loading en currentSession
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@SessionDetailActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                    // Hide loading on error
                    progressContainer.visibility = View.GONE
                    // Optionally show error state or finish activity
                    finish()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentSession.collect { session ->
                android.util.Log.d("SessionDetailActivity", "Session received: $session")
                session?.let {
                    currentSession = it // Store current session
                    displaySessionDetail(it)
                    // Hide loading after displaying data
                    progressContainer.visibility = View.GONE
                } ?: run {
                    android.util.Log.w("SessionDetailActivity", "Session is null, keeping loading visible")
                }
            }
        }
    }

    private fun loadSessionDetail() {
        android.util.Log.d("SessionDetailActivity", "Loading session with ID: $sessionId")
        viewModel.loadSession(sessionId)
    }

    private fun displaySessionDetail(session: TherapySession) {
        android.util.Log.d("SessionDetailActivity", "=== DISPLAYING SESSION DETAILS ===")
        android.util.Log.d("SessionDetailActivity", "Session ID: ${session.id}")
        android.util.Log.d("SessionDetailActivity", "Patient: ${session.paciente.nombresApellidos}")
        android.util.Log.d("SessionDetailActivity", "Therapist: ${session.terapeuta.nombresApellidos}")
        android.util.Log.d("SessionDetailActivity", "Date: ${session.fechaSesion}")
        android.util.Log.d("SessionDetailActivity", "Time: ${session.horaInicio} - ${session.horaFin}")
        android.util.Log.d("SessionDetailActivity", "Location: ${session.ubicacion}")

        try {
            // Patient info (sin emoji porque ya tenemos ícono)
            tvPatientName.text = session.paciente.nombresApellidos
            android.util.Log.d("SessionDetailActivity", "Updated patient name to: ${tvPatientName.text}")

            // Therapist info
            tvTherapistName.text = session.terapeuta.nombresApellidos
            android.util.Log.d("SessionDetailActivity", "Updated therapist name to: ${tvTherapistName.text}")

            // Also update therapist phone and email if available
            findViewById<TextView?>(R.id.textTherapistPhone)?.text = "📞 ${session.terapeuta.telefono ?: "+51 987654321"}"
            findViewById<TextView?>(R.id.textTherapistEmail)?.text = "📧 ${session.terapeuta.correo}"

            // Caregiver info (show only if exists)
            val caregiverCard = findViewById<MaterialCardView>(R.id.layoutCaregiverCard)
            android.util.Log.d("SessionDetailActivity", "=== CHECKING CAREGIVER DATA ===")
            android.util.Log.d("SessionDetailActivity", "Session ID: ${session.id}")
            android.util.Log.d("SessionDetailActivity", "Cuidador object: ${session.cuidador}")

            if (session.cuidador != null) {
                android.util.Log.d("SessionDetailActivity", "✅ Caregiver data found!")
                android.util.Log.d("SessionDetailActivity", "Caregiver ID: ${session.cuidador.id}")
                android.util.Log.d("SessionDetailActivity", "Caregiver Name: ${session.cuidador.nombresApellidos}")
                android.util.Log.d("SessionDetailActivity", "Caregiver Email: ${session.cuidador.correo}")
                android.util.Log.d("SessionDetailActivity", "Caregiver Phone: ${session.cuidador.telefono}")

                caregiverCard.visibility = View.VISIBLE
                tvCaregiverName.text = session.cuidador.nombresApellidos
                findViewById<TextView?>(R.id.textCaregiverNameDetail)?.text = session.cuidador.nombresApellidos
                findViewById<TextView?>(R.id.textCaregiverPhone)?.text = "📞 ${session.cuidador.telefono ?: "No disponible"}"
                findViewById<TextView?>(R.id.textCaregiverEmail)?.text = "📧 ${session.cuidador.correo}"
                android.util.Log.d("SessionDetailActivity", "Caregiver info updated: ${session.cuidador.nombresApellidos}")
            } else {
                android.util.Log.w("SessionDetailActivity", "❌ No caregiver data found in response")
                caregiverCard.visibility = View.GONE
            }

            // Schedule info
            tvSessionDate.text = formatDate(session.fechaSesion)
            tvSessionTime.text = "🕐 ${session.horaInicio}"
            tvDuration.text = "⏱️ ${session.duracion} min"
            android.util.Log.d("SessionDetailActivity", "Updated date/time: ${tvSessionDate.text}")

            // Location
            tvLocation.text = session.ubicacion ?: "Consultorio principal"
            tvAddress.text = session.direccion ?: "Dirección no especificada"
            android.util.Log.d("SessionDetailActivity", "Updated location: ${tvLocation.text}")

            // Status
            session.estado?.let { estado ->
                tvStatus.text = TherapySessionViewModel.getStatusDisplayName(estado)
                android.util.Log.d("SessionDetailActivity", "Updated status to: ${tvStatus.text}")
            }

            // Description
            if (!session.descripcion.isNullOrBlank()) {
                tvDescription.text = session.descripcion
                findViewById<MaterialCardView>(R.id.layoutDescription).visibility = View.VISIBLE
            } else {
                findViewById<MaterialCardView>(R.id.layoutDescription).visibility = View.GONE
            }

            // Objectives
            if (!session.objetivos.isNullOrEmpty()) {
                tvObjectives.text = session.objetivos.joinToString("\n• ", "• ")
                findViewById<MaterialCardView>(R.id.layoutObjectives).visibility = View.VISIBLE
            } else {
                findViewById<MaterialCardView>(R.id.layoutObjectives).visibility = View.GONE
            }

            // Materials
            if (!session.materiales.isNullOrEmpty()) {
                tvMaterials.text = session.materiales.joinToString("\n• ", "• ")
                findViewById<MaterialCardView>(R.id.layoutMaterials).visibility = View.VISIBLE
            } else {
                findViewById<MaterialCardView>(R.id.layoutMaterials).visibility = View.GONE
            }

            // Session name/type in header
            val sessionName = findViewById<TextView>(R.id.textSessionName)
            sessionName.text = session.tipoSesion ?: "Sesión Terapéutica"

            // Update toolbar title
            supportActionBar?.title = "Sesión #${session.id}"

            android.util.Log.d("SessionDetailActivity", "=== SESSION DETAILS UPDATED SUCCESSFULLY ===")

        } catch (e: Exception) {
            android.util.Log.e("SessionDetailActivity", "Error updating UI: ${e.message}", e)
            Toast.makeText(this, "Error mostrando detalles: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun showEditSessionDialog(session: TherapySession) {
        val intent = EditSessionActivity.newIntent(this, session.id)
        startActivityForResult(intent, REQUEST_CODE_EDIT_SESSION)
    }

    private fun showDeleteConfirmDialog(session: TherapySession) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Sesión")
            .setMessage("¿Estás seguro de que quieres eliminar esta sesión? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("Cancelar", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteSession(session: TherapySession) {
        viewModel.deleteSession(session.id)

        // Listen for deletion result
        lifecycleScope.launch {
            viewModel.successMessage.collect { message ->
                message?.let {
                    Toast.makeText(this@SessionDetailActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccessMessage()
                    setResult(RESULT_OK) // Indicate that session was deleted
                    finish()
                }
            }
        }
    }

    private fun exportSessionToPdf(session: TherapySession) {
        Toast.makeText(this, "Generando PDF...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                viewModel.exportSessionToPdf(session.id)
                // The success/error will be handled by the existing observers
            } catch (e: Exception) {
                Toast.makeText(this@SessionDetailActivity, "Error exportando PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EDIT_SESSION && resultCode == RESULT_OK) {
            // Reload session data after edit
            loadSessionDetail()
            Toast.makeText(this, "Sesión actualizada exitosamente", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val REQUEST_CODE_EDIT_SESSION = 1001

        fun newIntent(context: Context, sessionId: Int): Intent {
            return Intent(context, SessionDetailActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }
    }
}