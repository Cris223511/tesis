package com.example.serious_game_usil.presentation.ui.patients

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.Patient
import com.example.serious_game_usil.databinding.ActivityPatientDetailBinding
import com.example.serious_game_usil.data.ApiResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class PatientDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientDetailBinding
    private val viewModel: PatientsViewModel by viewModels { PatientsViewModelFactory() }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timestampFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    companion object {
        private const val EXTRA_PATIENT_ID = "patient_id"

        fun newIntent(context: Context, patientId: Int): Intent {
            return Intent(context, PatientDetailActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupObservers()
        loadPatientData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.selectedPatient.collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        populatePatientData(result.data)
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@PatientDetailActivity, result.message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@PatientDetailActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    null -> {
                    }
                }
            }
        }
    }

    private fun loadPatientData() {
        val patientId = intent.getIntExtra(EXTRA_PATIENT_ID, -1)
        if (patientId != -1) {
            viewModel.loadPatient(patientId)
        } else {
            Toast.makeText(this, "Error: ID de paciente inválido", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun populatePatientData(patient: Patient) {
        binding.apply {
            tvPatientName.text = patient.nombresApellidos
            tvPatientDocument.text = "${patient.tipoDocumento}: ${patient.numDocumento}"

            // Mostrar el Serial ID (solo lectura)
            tvSerialId.text = patient.serialId

            val statusText = if (patient.activo) "Activo" else "Inactivo"
            val statusColor = if (patient.activo) R.color.success else R.color.error
            tvPatientStatus.text = statusText
            tvPatientStatus.backgroundTintList = ContextCompat.getColorStateList(this@PatientDetailActivity, statusColor)

            try {
                val birthDate = dateFormatter.parse(patient.fechaNacimiento)
                birthDate?.let {
                    tvBirthDate.text = displayDateFormatter.format(it)
                    tvAge.text = "${calculateAge(it)} años"
                }
            } catch (e: Exception) {
                tvBirthDate.text = patient.fechaNacimiento
                tvAge.text = "N/A"
            }

            tvGender.text = when (patient.sexo.lowercase()) {
                "m", "masculino" -> "Masculino"
                "f", "femenino" -> "Femenino"
                else -> patient.sexo
            }

            patient.altura?.let { altura ->
                if (altura > 0) {
                    val alturaM = altura / 100
                    tvHeight.text = String.format("%.2f m", alturaM)
                } else {
                    tvHeight.text = "No registrado"
                }
            } ?: run {
                tvHeight.text = "No registrado"
            }

            patient.peso?.let { peso ->
                if (peso > 0) {
                    tvWeight.text = String.format("%.1f kg", peso)
                } else {
                    tvWeight.text = "No registrado"
                }
            } ?: run {
                tvWeight.text = "No registrado"
            }

            patient.imc?.let { imc ->
                if (imc > 0) {
                    tvBMI.text = String.format("%.1f", imc)
                    setupBMICategory(imc.toFloat())
                } else {
                    tvBMI.text = "N/A"
                    tvBMICategory.text = "No calculado"
                    tvBMICategory.backgroundTintList = ContextCompat.getColorStateList(this@PatientDetailActivity, R.color.text_secondary)
                }
            } ?: run {
                tvBMI.text = "N/A"
                tvBMICategory.text = "No calculado"
                tvBMICategory.backgroundTintList = ContextCompat.getColorStateList(this@PatientDetailActivity, R.color.text_secondary)
            }

            tvTherapist.text = patient.terapeutaNombre
            tvCaregiver.text = patient.cuidadorNombre ?: "No asignado"

            tvDiagnosis.text = if (patient.diagnosticoClinico.isNullOrBlank()) {
                "Sin diagnóstico registrado"
            } else {
                patient.diagnosticoClinico
            }

            try {
                val createdDate = timestampFormatter.parse(patient.createdAt.replace("T", " ").substring(0, 16))
                tvCreatedAt.text = createdDate?.let { timestampFormatter.format(it) } ?: patient.createdAt
            } catch (e: Exception) {
                tvCreatedAt.text = patient.createdAt.substring(0, 10)
            }

            try {
                val updatedDate = timestampFormatter.parse(patient.updatedAt.replace("T", " ").substring(0, 16))
                tvUpdatedAt.text = updatedDate?.let { timestampFormatter.format(it) } ?: patient.updatedAt
            } catch (e: Exception) {
                tvUpdatedAt.text = patient.updatedAt.substring(0, 10)
            }

            if (!patient.foto.isNullOrBlank()) {
                Glide.with(this@PatientDetailActivity)
                    .load(patient.foto)
                    .placeholder(R.drawable.ic_patient_placeholder)
                    .error(R.drawable.ic_patient_placeholder)
                    .circleCrop()
                    .into(ivPatientPhoto)
            }
        }
    }

    private fun setupBMICategory(bmi: Float) {
        val (category, colorRes) = when {
            bmi < 18.5f -> "Bajo peso" to R.color.warning
            bmi < 25f -> "Peso normal" to R.color.success
            bmi < 30f -> "Sobrepeso" to R.color.warning
            else -> "Obesidad" to R.color.error
        }

        binding.tvBMICategory.text = category
        binding.tvBMICategory.backgroundTintList = ContextCompat.getColorStateList(this, colorRes)
    }

    private fun calculateAge(birthDate: java.util.Date): Int {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        calendar.time = birthDate
        val birthYear = calendar.get(java.util.Calendar.YEAR)
        val birthDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        var age = currentYear - birthYear
        if (currentDayOfYear < birthDayOfYear) {
            age--
        }
        return age
    }
}