package com.example.serious_game_usil.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.TherapySession
import com.example.serious_game_usil.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class MySessionsAdapter(
    private val onItemClick: (TherapySession) -> Unit
) : RecyclerView.Adapter<MySessionsAdapter.SessionViewHolder>() {

    private var sessions = listOf<TherapySession>()

    fun updateSessions(newSessions: List<TherapySession>) {
        sessions = newSessions
        notifyDataSetChanged()
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
        holder.bind(sessions[position])
    }

    override fun getItemCount() = sessions.size

    inner class SessionViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: TherapySession) {
            binding.textSessionName.text = session.nombreSesion
            binding.textTherapistName.text = "👨‍⚕️ ${session.terapeutaNombre}"
            binding.textPatientName.text = "👤 ${session.pacienteNombre}"

            // Formatear fecha y hora
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormatDate = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))
                val outputFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())

                val date = inputFormat.parse(session.fechaHora)
                date?.let {
                    binding.textSessionDate.text = "📅 ${outputFormatDate.format(it)}"
                    binding.textSessionTime.text = "🕐 ${outputFormatTime.format(it)}"
                }
            } catch (e: Exception) {
                binding.textSessionDate.text = "📅 ${session.fechaHora.split("T")[0]}"
                binding.textSessionTime.text = "🕐 ${session.fechaHora.split("T").getOrNull(1)?.substring(0, 5) ?: ""}"
            }

            // Mostrar ubicación si está disponible
            if (!session.ubicacion.isNullOrBlank()) {
                binding.textLocation.text = "📍 ${session.ubicacion}"
                binding.textLocation.visibility = android.view.View.VISIBLE
            } else {
                binding.textLocation.visibility = android.view.View.GONE
            }

            // Mostrar duración si está disponible
            if (session.duracionMinutos != null && session.duracionMinutos > 0) {
                val hours = session.duracionMinutos / 60
                val minutes = session.duracionMinutos % 60
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

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(session)
            }
        }
    }
}