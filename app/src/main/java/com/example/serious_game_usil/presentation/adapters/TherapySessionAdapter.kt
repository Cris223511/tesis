package com.example.serious_game_usil.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.`interface`.TherapySession
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class TherapySessionAdapter(
    private var sessions: List<TherapySession>,
    private val onEditClick: (TherapySession) -> Unit,
    private val onViewClick: (TherapySession) -> Unit,
    private val onDeleteClick: (TherapySession) -> Unit
) : RecyclerView.Adapter<TherapySessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPatientName: TextView = itemView.findViewById(R.id.tvPatientName)
        val tvSessionType: TextView = itemView.findViewById(R.id.tvSessionType)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val tvTherapist: TextView = itemView.findViewById(R.id.tvTherapist)
        val tvCaregiver: TextView = itemView.findViewById(R.id.tvCaregiver)
        val layoutCaregiver: LinearLayout = itemView.findViewById(R.id.layoutCaregiver)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEdit)
        val btnView: MaterialButton = itemView.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_therapy_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.tvPatientName.text = session.paciente.nombresApellidos

        val sessionTypeText = "${session.tipoSesion ?: "Terapia"} • ${session.modalidad ?: "Presencial"}"
        holder.tvSessionType.text = sessionTypeText

        holder.tvStatus.text = when(session.estado?.lowercase()) {
            "programada" -> "Programada"
            "completada" -> "Completada"
            "cancelada" -> "Cancelada"
            else -> "Programada"
        }

        val statusBackground = when(session.estado?.lowercase()) {
            "programada" -> R.drawable.status_active_background
            "completada" -> R.drawable.status_completed_background
            "cancelada" -> R.drawable.status_cancelled_background
            else -> R.drawable.status_active_background
        }
        holder.tvStatus.setBackgroundResource(statusBackground)

        val dateTime = formatDateTime(session.fechaSesion, session.horaInicio, session.horaFin)
        holder.tvDateTime.text = dateTime

        holder.tvDuration.text = "${session.duracion} min"

        val location = if (!session.direccion.isNullOrEmpty()) {
            "${session.ubicacion ?: "Sin ubicación"}, ${session.direccion}"
        } else {
            session.ubicacion ?: "Sin ubicación"
        }
        holder.tvLocation.text = location

        holder.tvTherapist.text = session.terapeuta.nombresApellidos

        // Caregiver info (show only if exists)
        android.util.Log.d("TherapySessionAdapter", "Session ${session.id}: Checking caregiver data")
        if (session.cuidador != null) {
            android.util.Log.d("TherapySessionAdapter", "Session ${session.id}: Caregiver found - ${session.cuidador.nombresApellidos}")
            holder.layoutCaregiver.visibility = View.VISIBLE
            holder.tvCaregiver.text = session.cuidador.nombresApellidos
        } else {
            android.util.Log.d("TherapySessionAdapter", "Session ${session.id}: No caregiver data found")
            holder.layoutCaregiver.visibility = View.GONE
        }

        if (!session.descripcion.isNullOrEmpty()) {
            holder.tvDescription.visibility = View.VISIBLE
            holder.tvDescription.text = session.descripcion
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        holder.btnDelete.setOnClickListener { onDeleteClick(session) }
        holder.btnEdit.setOnClickListener { onEditClick(session) }
        holder.btnView.setOnClickListener { onViewClick(session) }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateSessions(newSessions: List<TherapySession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    private fun formatDateTime(dateStr: String?, startTime: String?, endTime: String?): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateStr ?: "")
            val formattedDate = outputFormat.format(date ?: Date())

            if (!startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()) {
                "$formattedDate - $startTime a $endTime"
            } else {
                formattedDate
            }
        } catch (e: Exception) {
            dateStr ?: "Sin fecha"
        }
    }
}