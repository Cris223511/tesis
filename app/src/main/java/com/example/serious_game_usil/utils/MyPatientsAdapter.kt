package com.example.serious_game_usil.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.databinding.ItemMyPatientBinding

class MyPatientsAdapter(
    private val onItemClick: (PatientListItem) -> Unit
) : RecyclerView.Adapter<MyPatientsAdapter.MyPatientViewHolder>() {

    private var patients = listOf<PatientListItem>()

    fun updatePatients(newPatients: List<PatientListItem>) {
        patients = newPatients
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPatientViewHolder {
        val binding = ItemMyPatientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyPatientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyPatientViewHolder, position: Int) {
        holder.bind(patients[position])
    }

    override fun getItemCount() = patients.size

    inner class MyPatientViewHolder(
        private val binding: ItemMyPatientBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(patient: PatientListItem) {
            binding.textPatientName.text = patient.nombresApellidos
            binding.textPatientAge.text = "${patient.edad} años"
            binding.textPatientGender.text = patient.sexo
            binding.textPatientDocument.text = "${patient.tipoDocumento}: ${patient.numDocumento}"

            // Cargar foto del paciente
            ImageUtils.loadUserPhoto(binding.root.context, patient.foto, binding.ivPatientPhoto)

            // Click en toda la tarjeta para ver detalles
            binding.root.setOnClickListener {
                onItemClick(patient)
            }
        }
    }
}