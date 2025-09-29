package com.example.serious_game_usil.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.`interface`.TherapySession
import com.example.serious_game_usil.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class MySessionsAdapter(
    private val onItemClick: (TherapySession) -> Unit,
    private val onAnalyzeEmotionsClick: (TherapySession) -> Unit,
    private val onGenerateReportClick: (TherapySession) -> Unit,
    private val onRateTherapistClick: (TherapySession) -> Unit
) : RecyclerView.Adapter<MySessionsAdapter.SessionViewHolder>() {

    private var sessions = listOf<TherapySession>()
    private var sessionsWithNumbers = listOf<Pair<TherapySession, Int>>()

    fun updateSessions(newSessions: List<TherapySession>) {
        sessions = newSessions
        // Group sessions by patient and number them
        sessionsWithNumbers = groupAndNumberSessions(newSessions)
        notifyDataSetChanged()
    }

    private fun groupAndNumberSessions(sessions: List<TherapySession>): List<Pair<TherapySession, Int>> {
        return sessions
            .groupBy { it.pacienteId }
            .flatMap { (_, patientSessions) ->
                patientSessions
                    .sortedBy { it.createdAt }
                    .mapIndexed { index, session ->
                        session to (index + 1)
                    }
            }
            .sortedByDescending { it.first.createdAt }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val (session, sessionNumber) = sessionsWithNumbers[position]
        holder.bind(session, sessionNumber)
    }

    override fun getItemCount() = sessionsWithNumbers.size

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: TherapySession, sessionNumber: Int) {
            // Session name/type with numbering
            binding.textSessionName.text = "Sesión $sessionNumber - ${session.tipoSesion ?: "Terapéutica"}"

            // Therapist and patient info
            binding.textTherapistName.text = "👨‍⚕️ ${session.terapeuta.nombresApellidos}"
            binding.textPatientName.text = "👤 ${session.paciente.nombresApellidos}"

            // Format date and time
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormatDate = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))

                val date = inputFormat.parse(session.fechaSesion)
                date?.let {
                    binding.textSessionDate.text = "📅 ${outputFormatDate.format(it)}"
                }
                binding.textSessionTime.text = "🕐 ${session.horaInicio}"
            } catch (e: Exception) {
                binding.textSessionDate.text = "📅 ${session.fechaSesion}"
                binding.textSessionTime.text = "🕐 ${session.horaInicio}"
            }

            // Mostrar ubicación si está disponible
            if (!session.ubicacion.isNullOrBlank()) {
                binding.textLocation.text = "📍 ${session.ubicacion}"
                binding.textLocation.visibility = android.view.View.VISIBLE
            } else {
                binding.textLocation.visibility = android.view.View.GONE
            }

            // Show duration if available
            if (session.duracion > 0) {
                val hours = session.duracion / 60
                val minutes = session.duracion % 60
                val durationText = when {
                    hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
                    hours > 0 -> "${hours}h"
                    else -> "${minutes}min"
                }
                binding.textDuration.text = "⏱️ $durationText"
                binding.textDuration.visibility = android.view.View.VISIBLE
            } else {
                binding.textDuration.visibility = android.view.View.GONE
            }

            // Estado de la sesión
            val (statusText, statusColor) = when (session.estado.lowercase()) {
                "completada" -> "Completada" to ContextCompat.getColor(binding.root.context, R.color.activity_green)
                "programada" -> "Programada" to ContextCompat.getColor(binding.root.context, R.color.primary)
                "cancelada" -> "Cancelada" to ContextCompat.getColor(binding.root.context, R.color.stat_pink)
                "pendiente" -> "Pendiente" to ContextCompat.getColor(binding.root.context, R.color.orange)
                else -> session.estado.capitalize() to ContextCompat.getColor(binding.root.context, R.color.on_surface_variant)
            }

            binding.chipStatus.text = statusText
            binding.chipStatus.setChipBackgroundColorResource(
                when (session.estado.lowercase()) {
                    "completada" -> R.color.activity_green
                    "programada" -> R.color.primary
                    "cancelada" -> R.color.stat_pink
                    "pendiente" -> R.color.orange
                    else -> R.color.on_surface_variant
                }
            )

            // Click listeners
            binding.root.setOnClickListener {
                onItemClick(session)
            }

            // Show/hide rating button based on session status
            val isCompleted = session.estado.lowercase() == "completada"
            binding.btnRateTherapist.visibility = if (isCompleted) android.view.View.VISIBLE else android.view.View.GONE

            // Action button listeners
            binding.btnRateTherapist.setOnClickListener {
                onRateTherapistClick(session)
            }

            binding.btnAnalyzeEmotions.setOnClickListener {
                onAnalyzeEmotionsClick(session)
            }

            binding.btnGenerateReport.setOnClickListener {
                onGenerateReportClick(session)
            }
        }
    }
}