package com.example.serious_game_usil.presentation.ui.padres

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.SessionDetail
import com.example.serious_game_usil.databinding.ActivitySessionDetailBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.therapy.TherapySessionViewModel
import com.example.serious_game_usil.presentation.ui.therapy.RateTherapistActivity
import com.example.serious_game_usil.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SessionDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var viewModel: TherapySessionViewModel
    private var sessionId: Int = -1
    private var currentSession: SessionDetail? = null
    private var isSessionRated = false
    private var existingRating: Any? = null

    companion object {
        fun newIntent(context: Context, sessionId: Int): Intent {
            return Intent(context, SessionDetailActivity::class.java).apply {
                putExtra("session_id", sessionId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getIntExtra("session_id", -1)

        setupUI()
        setupViewModel()
        setupClickListeners()
        loadSessionDetail()
        checkExistingRating()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle de Sesión"
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[TherapySessionViewModel::class.java]

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@SessionDetailActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sessionDetail.collect { sessionDetail ->
                sessionDetail?.let {
                    currentSession = it
                    updateUI(it)
                }
            }
        }
    }

    private fun loadSessionDetail() {
        if (sessionId != -1) {
            viewModel.loadSessionDetail(sessionId)
        } else {
            Toast.makeText(this, "Error: ID de sesión inválido", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateUI(session: SessionDetail) {
        // Información básica
        binding.textSessionName.text = session.nombreSesion
        binding.textPatientName.text = session.pacienteNombre

        // Mostrar cuidador si está disponible
        if (session.cuidadorNombre != null && session.cuidadorNombre.isNotBlank()) {
            // Actualizar ambos TextViews del cuidador
            binding.textCaregiverName.text = session.cuidadorNombre
            binding.textCaregiverName.visibility = View.VISIBLE
            binding.textCaregiverNameDetail.text = session.cuidadorNombre
            binding.textCaregiverNameDetail.visibility = View.VISIBLE
        } else {
            binding.textCaregiverName.visibility = View.GONE
            binding.textCaregiverNameDetail.visibility = View.GONE
        }

        // Patient age removed from layout
        // if (session.pacienteEdad != null && session.pacienteEdad > 0) {
        //     binding.textPatientAge.text = "${session.pacienteEdad} años"
        //     binding.textPatientAge.visibility = View.VISIBLE
        // } else {
        //     binding.textPatientAge.visibility = View.GONE
        // }

        // Fecha y hora
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormatDate = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            val outputFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())

            val date = inputFormat.parse(session.fechaHora)
            date?.let {
                binding.textSessionDate.text = outputFormatDate.format(it)
                binding.textSessionTime.text = outputFormatTime.format(it)

                // Mostrar si es hoy, mañana, etc.
                val today = Calendar.getInstance()
                val sessionDate = Calendar.getInstance().apply { time = it }
                val diffDays = (sessionDate.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)

                val dateLabel = when {
                    diffDays == 0L -> "Hoy"
                    diffDays == 1L -> "Mañana"
                    diffDays == -1L -> "Ayer"
                    diffDays < -1 -> "Hace ${-diffDays} días"
                    diffDays > 1 -> "En $diffDays días"
                    else -> ""
                }

                if (dateLabel.isNotEmpty()) {
                    binding.textDateLabel.text = dateLabel
                    binding.textDateLabel.visibility = View.VISIBLE
                } else {
                    binding.textDateLabel.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            binding.textSessionDate.text = session.fechaHora.split("T")[0]
            binding.textSessionTime.text = session.fechaHora.split("T").getOrNull(1)?.substring(0, 5) ?: ""
        }

        // Estado
        val (statusText, statusColor) = when (session.estado.lowercase()) {
            "completada" -> "Completada" to R.color.activity_green
            "programada" -> "Programada" to R.color.primary
            "cancelada" -> "Cancelada" to R.color.stat_pink
            "pendiente" -> "Pendiente" to R.color.orange
            else -> session.estado.capitalize() to R.color.on_surface_variant
        }

        binding.chipStatus.text = statusText
        binding.chipStatus.setChipBackgroundColorResource(statusColor)

        // Duración
        if (session.duracionMinutos != null && session.duracionMinutos > 0) {
            val hours = session.duracionMinutos / 60
            val minutes = session.duracionMinutos % 60
            val durationText = when {
                hours > 0 && minutes > 0 -> "${hours} hora${if(hours > 1) "s" else ""} y ${minutes} minuto${if(minutes > 1) "s" else ""}"
                hours > 0 -> "${hours} hora${if(hours > 1) "s" else ""}"
                else -> "${minutes} minuto${if(minutes > 1) "s" else ""}"
            }
            binding.textDuration.text = durationText
            binding.layoutDuration.visibility = View.VISIBLE
        } else {
            binding.layoutDuration.visibility = View.GONE
        }

        // Terapeuta
        binding.textTherapistName.text = session.terapeutaNombre

        if (!session.terapeutaTelefono.isNullOrBlank()) {
            binding.textTherapistPhone.text = session.terapeutaTelefono
            binding.layoutTherapistPhone.visibility = View.VISIBLE
        } else {
            binding.layoutTherapistPhone.visibility = View.GONE
        }

        if (!session.terapeutaCorreo.isNullOrBlank()) {
            binding.textTherapistEmail.text = session.terapeutaCorreo
            binding.layoutTherapistEmail.visibility = View.VISIBLE
        } else {
            binding.layoutTherapistEmail.visibility = View.GONE
        }

        // Ubicación
        if (!session.ubicacion.isNullOrBlank()) {
            binding.textLocation.text = session.ubicacion
            binding.layoutLocation.visibility = View.VISIBLE

            if (!session.direccion.isNullOrBlank()) {
                binding.textAddress.text = session.direccion
                binding.textAddress.visibility = View.VISIBLE
            } else {
                binding.textAddress.visibility = View.GONE
            }
        } else {
            binding.layoutLocation.visibility = View.GONE
        }

        // Descripción y objetivos
        if (!session.descripcion.isNullOrBlank()) {
            binding.textDescription.text = session.descripcion
            binding.layoutDescription.visibility = View.VISIBLE
        } else {
            binding.layoutDescription.visibility = View.GONE
        }

        if (!session.objetivos.isNullOrBlank()) {
            binding.textObjectives.text = session.objetivos
            binding.layoutObjectives.visibility = View.VISIBLE
        } else {
            binding.layoutObjectives.visibility = View.GONE
        }

        // Materiales necesarios
        if (!session.materialesNecesarios.isNullOrBlank()) {
            binding.textMaterials.text = session.materialesNecesarios
            binding.layoutMaterials.visibility = View.VISIBLE
        } else {
            binding.layoutMaterials.visibility = View.GONE
        }

        // Notas
        if (!session.notasTerapeuta.isNullOrBlank()) {
            binding.textTherapistNotes.text = session.notasTerapeuta
            binding.layoutTherapistNotes.visibility = View.VISIBLE
        } else {
            binding.layoutTherapistNotes.visibility = View.GONE
        }

        if (!session.notasCuidador.isNullOrBlank()) {
            binding.textCaregiverNotes.text = session.notasCuidador
            binding.layoutCaregiverNotes.visibility = View.VISIBLE
        } else {
            binding.layoutCaregiverNotes.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        // Click listener para el botón "Calificar Sesión"
        binding.btnRateTherapist.setOnClickListener {
            currentSession?.let { session ->
                // Usar los IDs si están disponibles, sino usar valores de fallback
                val therapistId = session.terapeutaId ?: sessionId // Fallback temporal
                val patientId = session.pacienteId ?: sessionId // Fallback temporal

                // Para el cuidador ID: si es admin, usar el ID del admin, si no, usar el cuidador de la sesión
                val userRoles = AuthManager.getUserRoles()
                val isAdmin = userRoles.any { it.equals("AD", ignoreCase = true) || it.equals("admin", ignoreCase = true) }
                val caregiverId = if (isAdmin) {
                    // Si es admin, usar su propio ID (él puede calificar en nombre de cualquier cuidador)
                    AuthManager.getUserId()
                } else {
                    // Si es cuidador normal, usar el ID del cuidador de la sesión
                    session.cuidadorId ?: AuthManager.getUserId()
                }

                val intent = RateTherapistActivity.newIntent(
                    context = this,
                    sessionId = sessionId,
                    therapistId = therapistId,
                    therapistName = session.terapeutaNombre ?: "Terapeuta",
                    patientName = session.pacienteNombre ?: "Paciente",
                    patientId = patientId,
                    caregiverId = caregiverId
                )
                startActivity(intent)
            } ?: run {
                Toast.makeText(
                    this,
                    "Cargando datos de la sesión...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkExistingRating() {
        // Configurar token de autenticación
        AuthManager.getAccessToken()?.let { token ->
            RetrofitClient.setAuthToken(token)
        }

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService()
                val response = apiService.getSessionRating(sessionId)

                if (response.isSuccessful) {
                    // La sesión ya está calificada
                    val ratingData = response.body()?.data
                    ratingData?.let {
                        isSessionRated = true
                        existingRating = it
                        updateUIForRatedSession(it)
                    }
                } else if (response.code() == 404) {
                    // No hay calificación para esta sesión
                    isSessionRated = false
                    updateUIForUnratedSession()
                }
            } catch (e: Exception) {
                // Error al verificar, asumir que no está calificada
                isSessionRated = false
                updateUIForUnratedSession()
            }
        }
    }

    private fun updateUIForRatedSession(ratingData: Any) {
        // Extraer rating del objeto (asumiendo que es un Map o similar)
        val rating = when (ratingData) {
            is Map<*, *> -> ratingData["rating"]?.toString()?.toIntOrNull() ?: 0
            else -> 0
        }

        val comment = when (ratingData) {
            is Map<*, *> -> ratingData["comment"]?.toString() ?: ""
            else -> ""
        }

        // Generar estrellas
        val stars = "⭐".repeat(rating)

        // Actualizar el botón para mostrar la calificación
        binding.btnRateTherapist.text = "$stars $rating/5"
        binding.btnRateTherapist.isEnabled = false
        binding.btnRateTherapist.alpha = 0.6f

        if (comment.isNotEmpty()) {
            binding.btnRateTherapist.text = "${binding.btnRateTherapist.text}\n\"$comment\""
        }

        // Cambiar color del botón
        binding.btnRateTherapist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
    }

    private fun updateUIForUnratedSession() {
        // Mantener el botón activo para calificar
        binding.btnRateTherapist.text = "⭐ Calificar Sesión"
        binding.btnRateTherapist.isEnabled = true
        binding.btnRateTherapist.alpha = 1.0f
        binding.btnRateTherapist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Verificar de nuevo cuando regresamos de la actividad de calificación
        checkExistingRating()
    }
}