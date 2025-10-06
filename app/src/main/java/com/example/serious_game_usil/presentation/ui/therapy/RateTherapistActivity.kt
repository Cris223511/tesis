package com.example.serious_game_usil.presentation.ui.therapy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityRateTherapistBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.network.RetrofitClient
import com.example.serious_game_usil.`interface`.TherapistRatingRequest
import com.example.serious_game_usil.`interface`.TherapistRatingResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RateTherapistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRateTherapistBinding
    private var sessionId: Int = -1
    private var therapistId: Int = -1
    private var therapistName: String = ""
    private var patientName: String = ""
    private var caregiverId: Int = -1
    private var patientId: Int = -1

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_THERAPIST_ID = "therapist_id"
        private const val EXTRA_THERAPIST_NAME = "therapist_name"
        private const val EXTRA_PATIENT_NAME = "patient_name"
        private const val EXTRA_PATIENT_ID = "patient_id"
        private const val EXTRA_CAREGIVER_ID = "caregiver_id"

        fun newIntent(
            context: Context,
            sessionId: Int,
            therapistId: Int,
            therapistName: String,
            patientName: String,
            patientId: Int,
            caregiverId: Int
        ): Intent {
            return Intent(context, RateTherapistActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_THERAPIST_ID, therapistId)
                putExtra(EXTRA_THERAPIST_NAME, therapistName)
                putExtra(EXTRA_PATIENT_NAME, patientName)
                putExtra(EXTRA_PATIENT_ID, patientId)
                putExtra(EXTRA_CAREGIVER_ID, caregiverId)
            }
        }
    }

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
            RetrofitClient.setAuthToken(token)
        }

        binding = ActivityRateTherapistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener datos del intent
        sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        therapistId = intent.getIntExtra(EXTRA_THERAPIST_ID, -1)
        therapistName = intent.getStringExtra(EXTRA_THERAPIST_NAME) ?: ""
        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: ""
        patientId = intent.getIntExtra(EXTRA_PATIENT_ID, -1)
        caregiverId = intent.getIntExtra(EXTRA_CAREGIVER_ID, -1)

        if (sessionId == -1 || therapistId == -1) {
            Toast.makeText(this, "Error: Datos de sesión inválidos", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        // Configurar toolbar
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Configurar información de la sesión
        binding.tvTherapistName.text = therapistName
        binding.tvPatientName.text = patientName

        // Obtener y mostrar el nombre del cuidador actual
        val caregiverName = AuthManager.getNombresApellidos()
        binding.tvCaregiverName.text = caregiverName

        binding.tvSessionDate.text = "Sesión completada"

        // Configurar la barra de calificación
        binding.ratingBar.rating = 0f
        binding.ratingBar.stepSize = 1f

        // Deshabilitar el botón enviar inicialmente
        binding.btnSubmitRating.isEnabled = false
        binding.btnSubmitRating.alpha = 0.5f
    }

    private fun setupListeners() {
        // Listener para la barra de calificación
        binding.ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                updateRatingText(rating.toInt())
                binding.btnSubmitRating.isEnabled = rating > 0
                binding.btnSubmitRating.alpha = if (rating > 0) 1f else 0.5f

                // Mostrar advertencia si la calificación es baja
                if (rating <= 3) {
                    binding.warningCard.visibility = View.VISIBLE
                    binding.tvWarningMessage.text =
                        "⚠️ Una calificación de $rating estrellas resultará en la reasignación de un nuevo terapeuta"
                } else {
                    binding.warningCard.visibility = View.GONE
                }
            }
        }

        // Botón de enviar calificación
        binding.btnSubmitRating.setOnClickListener {
            val rating = binding.ratingBar.rating.toInt()
            if (rating > 0) {
                showConfirmationDialog(rating)
            } else {
                Toast.makeText(this, "Por favor selecciona una calificación", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón de omitir
        binding.btnSkip.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Omitir calificación?")
                .setMessage("Podrás calificar esta sesión más tarde desde tu historial")
                .setPositiveButton("Omitir") { _, _ ->
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun updateRatingText(rating: Int) {
        val text = when (rating) {
            1 -> "Muy insatisfecho 😞"
            2 -> "Insatisfecho 😕"
            3 -> "Regular 😐"
            4 -> "Satisfecho 😊"
            5 -> "Muy satisfecho 😄"
            else -> ""
        }
        binding.tvRatingText.text = text
        binding.tvRatingText.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showConfirmationDialog(rating: Int) {
        val message = if (rating <= 3) {
            """
            Has calificado esta sesión con $rating ${if (rating == 1) "estrella" else "estrellas"}.

            Esta acción resultará en:
            • La reasignación de un nuevo terapeuta para ${patientName}
            • Se notificará al terapeuta actual sobre la calificación

            ¿Estás seguro de enviar esta calificación?
            """.trimIndent()
        } else {
            """
            Has calificado esta sesión con $rating estrellas.

            Tu feedback ayudará a mejorar la calidad del servicio.

            ¿Deseas enviar esta calificación?
            """.trimIndent()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar calificación")
            .setMessage(message)
            .setPositiveButton("Enviar") { _, _ ->
                submitRating(rating)
            }
            .setNegativeButton("Revisar", null)
            .show()
    }

    private fun submitRating(rating: Int) {
        // Mostrar loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmitRating.isEnabled = false
        binding.btnSkip.isEnabled = false

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService()

                // Crear el request de calificación
                val ratingRequest = TherapistRatingRequest(
                    sessionId = sessionId,
                    therapistId = therapistId,
                    caregiverId = caregiverId,
                    patientId = patientId,
                    rating = rating,
                    feedback = binding.etFeedback.text.toString().trim(),
                    reassignTherapist = rating <= 3
                )

                val response = withContext(Dispatchers.IO) {
                    apiService.rateTherapist(ratingRequest)
                }

                if (response.isSuccessful) {
                    handleRatingSuccess(rating, response.body())
                } else {
                    showError("Error al enviar la calificación. Por favor intenta nuevamente.")
                }
            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSubmitRating.isEnabled = true
                binding.btnSkip.isEnabled = true
            }
        }
    }

    private fun handleRatingSuccess(rating: Int, response: TherapistRatingResponse?) {
        val title = if (rating <= 3) {
            "Calificación enviada - Nuevo terapeuta asignado"
        } else {
            "¡Gracias por tu calificación!"
        }

        val message = if (rating <= 3) {
            """
            Tu calificación ha sido registrada exitosamente.

            ${response?.data?.newTherapistName?.let { "Nuevo terapeuta asignado: $it" } ?: "Se asignará un nuevo terapeuta pronto"}

            Se te notificará sobre la próxima sesión.
            """.trimIndent()
        } else {
            """
            Tu calificación de $rating estrellas ha sido registrada.

            Gracias por tu feedback positivo sobre $therapistName.
            """.trimIndent()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar") { _, _ ->
                setResult(RESULT_OK)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}