package com.example.serious_game_usil.utils

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.Caregiver
import com.example.serious_game_usil.databinding.ItemCaregiverBinding
import java.text.SimpleDateFormat
import java.util.*

class CaregiversAdapter(
    private val onEditClick: (Caregiver) -> Unit,
    private val onDeleteClick: (Caregiver) -> Unit,
    private val onViewPatientsClick: (Caregiver) -> Unit,
    private val onItemClick: (Caregiver) -> Unit = {},
    private val showEditDeleteButtons: Boolean = true
) : RecyclerView.Adapter<CaregiversAdapter.CaregiverViewHolder>() {

    private var originalCaregivers = listOf<Caregiver>()
    private var filteredCaregivers = listOf<Caregiver>()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    inner class CaregiverViewHolder(private val binding: ItemCaregiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(caregiver: Caregiver) {
            with(binding) {
                tvCaregiverName.text = caregiver.nombresApellidos
                tvCaregiverDocument.text = "${getDocumentTypeAbbreviation(caregiver.tipoDocumento)}: ${caregiver.numDocumento}"
                tvCaregiverEmail.text = caregiver.correo
                tvCaregiverPhone.text = caregiver.telefono ?: "No especificado"
                tvCaregiverGender.text = when(caregiver.sexo) {
                    "Masculino" -> "M"
                    "Femenino" -> "F"
                    else -> caregiver.sexo
                }

                // Mostrar número de pacientes asignados
                val patientsText = if (caregiver.pacientesAsignados == 0) {
                    "Sin pacientes"
                } else if (caregiver.pacientesAsignados == 1) {
                    "1 paciente"
                } else {
                    "${caregiver.pacientesAsignados} pacientes"
                }
                btnPatientsCount.text = patientsText
                tvPatientsCount.text = patientsText

                // Status badge
                if (caregiver.activo) {
                    tvCaregiverStatus.text = "Activo"
                    tvCaregiverStatus.setBackgroundResource(R.drawable.status_active_background)
                } else {
                    tvCaregiverStatus.text = "Inactivo"
                    tvCaregiverStatus.setBackgroundResource(R.drawable.status_inactive_background)
                }

                // Último acceso
                if (!caregiver.fechaUltimoAcceso.isNullOrBlank()) {
                    try {
                        // Try parsing ISO 8601 format first (from API)
                        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        val date = isoFormat.parse(caregiver.fechaUltimoAcceso.substringBefore("."))

                        if (date != null) {
                            val displayFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                            tvLastAccess.text = displayFormat.format(date)
                        } else {
                            // Fallback to just show date portion
                            tvLastAccess.text = caregiver.fechaUltimoAcceso.substringBefore("T").replace("-", "/")
                        }
                    } catch (e: Exception) {
                        // If parsing fails, just show the date portion
                        tvLastAccess.text = caregiver.fechaUltimoAcceso.substringBefore("T").replace("-", "/")
                    }
                } else {
                    tvLastAccess.text = "Sin acceso"
                }

                // Load caregiver photo
                loadCaregiverPhoto(caregiver.fotoMovil)

                // Set click listeners
                rippleOverlay.setOnClickListener { onItemClick(caregiver) }

                // Show/hide action buttons based on permissions
                if (showEditDeleteButtons) {
                    btnEditCaregiver.visibility = android.view.View.VISIBLE
                    btnDeleteCaregiver.visibility = android.view.View.VISIBLE
                    btnEditCaregiver.setOnClickListener { onEditClick(caregiver) }
                    btnDeleteCaregiver.setOnClickListener { onDeleteClick(caregiver) }
                } else {
                    btnEditCaregiver.visibility = android.view.View.GONE
                    btnDeleteCaregiver.visibility = android.view.View.GONE
                }

                // Botones ver pacientes siempre visibles
                btnViewPatients.setOnClickListener { onViewPatientsClick(caregiver) }
                btnPatientsCount.setOnClickListener { onViewPatientsClick(caregiver) }
            }
        }

        private fun loadCaregiverPhoto(photoBase64: String?) {
            // Validar que no esté vacío Y que tenga longitud válida
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
                        binding.ivCaregiverPhoto.setImageBitmap(bitmap)
                    } else {
                        binding.ivCaregiverPhoto.setImageResource(R.drawable.ic_person_placeholder)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CaregiversAdapter", "Error decoding photo: ${e.message}")
                    binding.ivCaregiverPhoto.setImageResource(R.drawable.ic_person_placeholder)
                }
            } else {
                binding.ivCaregiverPhoto.setImageResource(R.drawable.ic_person_placeholder)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaregiverViewHolder {
        val binding = ItemCaregiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CaregiverViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CaregiverViewHolder, position: Int) {
        holder.bind(filteredCaregivers[position])
    }

    override fun getItemCount(): Int = filteredCaregivers.size

    fun updateCaregivers(newCaregivers: List<Caregiver>) {
        val diffCallback = CaregiverDiffCallback(filteredCaregivers, newCaregivers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        originalCaregivers = newCaregivers
        filteredCaregivers = newCaregivers

        diffResult.dispatchUpdatesTo(this)
    }

    fun filter(query: String) {
        filteredCaregivers = if (query.isBlank()) {
            originalCaregivers
        } else {
            originalCaregivers.filter { caregiver ->
                caregiver.nombresApellidos.contains(query, ignoreCase = true) ||
                caregiver.numDocumento.contains(query, ignoreCase = true) ||
                caregiver.correo.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<Caregiver> = filteredCaregivers

    private class CaregiverDiffCallback(
        private val oldList: List<Caregiver>,
        private val newList: List<Caregiver>
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