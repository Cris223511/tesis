package com.example.serious_game_usil.utils

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.databinding.ItemPatientBinding
import java.text.SimpleDateFormat
import java.util.*

class PatientsAdapter(
    private val onEditClick: (PatientListItem) -> Unit,
    private val onDeleteClick: (PatientListItem) -> Unit,
    private val onItemClick: (PatientListItem) -> Unit = {},
    private val onExportClick: (PatientListItem) -> Unit = {},
    private val isAdmin: Boolean = false,
    private val showEditDeleteButtons: Boolean = true
) : RecyclerView.Adapter<PatientsAdapter.PatientViewHolder>() {

    private var originalPatients = listOf<PatientListItem>()
    private var filteredPatients = listOf<PatientListItem>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    inner class PatientViewHolder(private val binding: ItemPatientBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(patient: PatientListItem) {
            with(binding) {
                tvPatientName.text = patient.nombresApellidos
                tvPatientDocument.text = "${getDocumentTypeAbbreviation(patient.tipoDocumento)}: ${patient.numDocumento}"
                tvPatientSerialId.text = "Serial: ${patient.serialId}"
                tvPatientAge.text = "${patient.edad} años"
                tvPatientGender.text = when(patient.sexo) {
                    "Masculino" -> "M"
                    "Femenino" -> "F"
                    else -> patient.sexo
                }

                rippleOverlay.setOnClickListener {
                    onItemClick(patient)
                }

                // Therapist info
                val therapistText = if (patient.terapeutaNombre.isNotBlank()) {
                    "Terapeuta: ${patient.terapeutaNombre}"
                } else {
                    "Sin terapeuta asignado"
                }
                tvTherapistName.text = therapistText

                // Caregiver info (solo visible para administradores)
                if (isAdmin) {
                    tvCaregiverName.visibility = android.view.View.VISIBLE
                    tvCaregiverName.text = if (!patient.cuidadorNombre.isNullOrBlank()) {
                        "Cuidador: ${patient.cuidadorNombre}"
                    } else {
                        "Cuidador: Sin cuidador asignado"
                    }
                } else {
                    tvCaregiverName.visibility = android.view.View.GONE
                }

                // Status badge
                if (patient.activo) {
                    tvPatientStatus.text = "Activo"
                    tvPatientStatus.setBackgroundResource(R.drawable.status_active_background)
                } else {
                    tvPatientStatus.text = "Inactivo"
                    tvPatientStatus.setBackgroundResource(R.drawable.status_inactive_background)
                }

                // Load patient photo
                loadPatientPhoto(patient.fotoMovil)

                // Set click listeners
                rippleOverlay.setOnClickListener { onItemClick(patient) }

                // Show/hide action buttons based on permissions
                if (showEditDeleteButtons) {
                    btnEditPatient.visibility = android.view.View.VISIBLE
                    btnDeletePatient.visibility = android.view.View.VISIBLE
                    btnEditPatient.setOnClickListener { onEditClick(patient) }
                    btnDeletePatient.setOnClickListener { onDeleteClick(patient) }
                } else {
                    btnEditPatient.visibility = android.view.View.GONE
                    btnDeletePatient.visibility = android.view.View.GONE
                }

                // Export button always visible for now
                btnExportPatient.setOnClickListener { onExportClick(patient) }
            }
        }

        private fun loadPatientPhoto(photoBase64: String?) {
            // Validar que el string no esté vacío Y que sea válido
            if (!photoBase64.isNullOrBlank() && photoBase64.length > 20) {
                try {
                    // Remover el prefijo "data:image/...;base64," si existe
                    val cleanBase64 = if (photoBase64.contains("base64,")) {
                        photoBase64.substring(photoBase64.indexOf("base64,") + 7)
                    } else {
                        photoBase64
                    }

                    val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                    if (bitmap != null) {
                        binding.ivPatientPhoto.setImageBitmap(bitmap)
                    } else {
                        // El decode fue exitoso pero el bitmap es null
                        binding.ivPatientPhoto.setImageResource(R.drawable.ic_patient_placeholder)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PatientsAdapter", "Error decoding patient photo: ${e.message}")
                    binding.ivPatientPhoto.setImageResource(R.drawable.ic_patient_placeholder)
                }
            } else {
                binding.ivPatientPhoto.setImageResource(R.drawable.ic_patient_placeholder)
            }
        }

        private fun getDocumentTypeAbbreviation(documentType: String): String {
            return when (documentType.lowercase()) {
                "dni" -> "DNI"
                "pasaporte" -> "PAS"
                "carnet de extranjería" -> "CE"
                else -> documentType.take(3).uppercase()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PatientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(filteredPatients[position])
    }

    override fun getItemCount(): Int = filteredPatients.size

    fun updatePatients(newPatients: List<PatientListItem>) {
        val diffCallback = PatientDiffCallback(filteredPatients, newPatients)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        originalPatients = newPatients
        filteredPatients = newPatients

        diffResult.dispatchUpdatesTo(this)
    }

    fun filter(query: String) {
        filteredPatients = if (query.isBlank()) {
            originalPatients
        } else {
            originalPatients.filter { patient ->
                patient.nombresApellidos.contains(query, ignoreCase = true) ||
                patient.numDocumento.contains(query, ignoreCase = true) ||
                patient.terapeutaNombre.contains(query, ignoreCase = true) ||
                (patient.cuidadorNombre?.contains(query, ignoreCase = true) == true)
            }
        }
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<PatientListItem> = filteredPatients

    private class PatientDiffCallback(
        private val oldList: List<PatientListItem>,
        private val newList: List<PatientListItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
