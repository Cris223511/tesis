package com.example.serious_game_usil.presentation.ui.padres

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.SessionDetail
import com.example.serious_game_usil.databinding.ActivitySessionDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class SessionDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionDetailBinding
    private var sessionId: Int = -1

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
        loadSessionDetail()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detalle de Sesión"
    }

    private fun loadSessionDetail() {
        // Simular datos de sesión detallada hasta integrar con backend real
        // TODO: Integrar con el servicio real de sesiones
        val sessionDetail = generateSimulatedSessionDetail(sessionId)
        updateUI(sessionDetail)
    }

    private fun updateUI(session: SessionDetail) {
        // Información básica
        binding.textSessionName.text = session.nombreSesion
        binding.textPatientName.text = session.pacienteNombre

        if (session.pacienteEdad != null && session.pacienteEdad > 0) {
            binding.textPatientAge.text = "${session.pacienteEdad} años"
            binding.textPatientAge.visibility = View.VISIBLE
        } else {
            binding.textPatientAge.visibility = View.GONE
        }

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

    private fun generateSimulatedSessionDetail(sessionId: Int): SessionDetail {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, sessionId)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

        val sessionNames = listOf(
            "Terapia de Comunicación Social",
            "Sesión de Habilidades Motoras",
            "Terapia Sensorial",
            "Desarrollo del Lenguaje",
            "Terapia Ocupacional"
        )

        val therapists = listOf(
            Triple("Dra. María González", "+51 987654321", "maria.gonzalez@clinica.com"),
            Triple("Dr. Carlos Mendoza", "+51 976543210", "carlos.mendoza@clinica.com"),
            Triple("Lic. Ana Rojas", "+51 965432109", "ana.rojas@clinica.com")
        )

        val therapist = therapists[sessionId % therapists.size]

        return SessionDetail(
            id = sessionId,
            nombreSesion = sessionNames[sessionId % sessionNames.size],
            fechaHora = dateFormat.format(calendar.time),
            terapeutaNombre = therapist.first,
            terapeutaTelefono = therapist.second,
            terapeutaCorreo = therapist.third,
            pacienteNombre = "Diego Martínez",
            pacienteEdad = 8,
            ubicacion = "Consultorio 101 - Centro Terapéutico USIL",
            direccion = "Av. La Fontana 550, La Molina, Lima",
            descripcion = "Sesión enfocada en el desarrollo de habilidades específicas mediante actividades lúdicas y ejercicios terapéuticos personalizados.",
            objetivos = "• Mejorar la comunicación verbal\n• Desarrollar habilidades sociales\n• Fortalecer la coordinación motora\n• Reducir comportamientos repetitivos",
            estado = if (sessionId <= 2) "completada" else "programada",
            duracionMinutos = 60,
            notasTerapeuta = if (sessionId <= 2) "El paciente mostró excelente progreso en la sesión. Respondió positivamente a los ejercicios de comunicación." else null,
            notasCuidador = if (sessionId <= 2) "Mi hijo llegó muy contento después de la sesión. Noté mejoras en su comunicación en casa." else null,
            materialesNecesarios = "• Juegos de construcción\n• Material sensorial\n• Tarjetas pictográficas\n• Ropa cómoda",
            createdAt = dateFormat.format(Date()),
            updatedAt = dateFormat.format(Date())
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}