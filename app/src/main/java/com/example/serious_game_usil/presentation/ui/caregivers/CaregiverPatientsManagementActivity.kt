package com.example.serious_game_usil.presentation.ui.caregivers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.Caregiver
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.databinding.ActivityCaregiverPatientsManagementBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.patients.CreateEditPatientActivity
import com.example.serious_game_usil.presentation.ui.patients.PatientDetailActivity
import com.example.serious_game_usil.repository.CaregiverRepository
import com.example.serious_game_usil.utils.PatientsAdapter
import kotlinx.coroutines.launch

class CaregiverPatientsManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaregiverPatientsManagementBinding
    private lateinit var patientsAdapter: PatientsAdapter
    private lateinit var caregiverRepository: CaregiverRepository

    private var caregiverId: Int = -1
    private var caregiverName: String = ""
    private var canManagePatients: Boolean = false

    companion object {
        private const val EXTRA_CAREGIVER_ID = "caregiver_id"
        private const val EXTRA_CAREGIVER_NAME = "caregiver_name"

        fun newIntent(context: Context, caregiverId: Int, caregiverName: String): Intent {
            return Intent(context, CaregiverPatientsManagementActivity::class.java).apply {
                putExtra(EXTRA_CAREGIVER_ID, caregiverId)
                putExtra(EXTRA_CAREGIVER_NAME, caregiverName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaregiverPatientsManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get intent extras
        caregiverId = intent.getIntExtra(EXTRA_CAREGIVER_ID, -1)
        caregiverName = intent.getStringExtra(EXTRA_CAREGIVER_NAME) ?: ""

        if (caregiverId == -1) {
            Toast.makeText(this, "Error: ID de cuidador inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Check permissions
        val userRoles = AuthManager.getUserRoles()
        canManagePatients = userRoles.any {
            it.lowercase() in listOf("admin", "administrador", "terapeuta", "therapist")
        }

        caregiverRepository = CaregiverRepository.getInstance()

        setupViews()
        setupRecyclerView()
        loadCaregiverInfo()
        loadPatients()
    }

    private fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Pacientes del Cuidador"

        // Setup caregiver info
        binding.tvCaregiverName.text = caregiverName

        // Setup FAB
        if (canManagePatients) {
            binding.fabAddPatient.setOnClickListener {
                showAddPatientOptions()
            }
        } else {
            binding.fabAddPatient.hide()
        }

        // Setup swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            loadPatients()
        }
    }

    private fun setupRecyclerView() {
        patientsAdapter = PatientsAdapter(
            onItemClick = { patient ->
                val intent = PatientDetailActivity.newIntent(this, patient.id)
                startActivity(intent)
            },
            onEditClick = if (canManagePatients) { { patient ->
                val intent = Intent(this, CreateEditPatientActivity::class.java)
                intent.putExtra("patient_id", patient.id)
                startActivity(intent)
            } } else { { _ ->
                Toast.makeText(this, "No tienes permisos para editar pacientes", Toast.LENGTH_SHORT).show()
            } },
            onDeleteClick = if (canManagePatients) { { patient ->
                showUnassignConfirmation(patient)
            } } else { { _ ->
                Toast.makeText(this, "No tienes permisos para desasignar pacientes", Toast.LENGTH_SHORT).show()
            } },
            showEditDeleteButtons = canManagePatients
        )

        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(this@CaregiverPatientsManagementActivity)
            adapter = patientsAdapter
        }
    }

    private fun loadCaregiverInfo() {
        lifecycleScope.launch {
            caregiverRepository.getCaregiverDetail(caregiverId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val caregiver = result.data.caregiver
                        binding.tvCaregiverName.text = caregiver.nombresApellidos
                        binding.tvCaregiverInfo.text = "${caregiver.tipoDocumento}: ${caregiver.numDocumento} • ${caregiver.correo}"
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error al cargar información del cuidador", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadPatients() {
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            caregiverRepository.getCaregiverPatients(caregiverId).collect { result ->
                binding.swipeRefresh.isRefreshing = false

                when (result) {
                    is ApiResult.Success -> {
                        val patients = result.data
                        updatePatientsUI(patients)
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error al cargar pacientes: ${result.message}", Toast.LENGTH_SHORT).show()
                        updatePatientsUI(emptyList())
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                        updatePatientsUI(emptyList())
                    }
                }
            }
        }
    }

    private fun updatePatientsUI(patients: List<PatientListItem>) {
        val patientsCount = patients.size
        val patientsText = if (patientsCount == 0) {
            "Sin pacientes asignados"
        } else {
            "$patientsCount paciente${if (patientsCount != 1) "s asignados" else " asignado"}"
        }

        binding.tvPatientsCountHeader.text = patientsText

        if (patients.isEmpty()) {
            binding.recyclerViewPatients.visibility = android.view.View.GONE
            binding.layoutEmptyState.visibility = android.view.View.VISIBLE
        } else {
            binding.recyclerViewPatients.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.visibility = android.view.View.GONE
            patientsAdapter.updatePatients(patients)
        }
    }

    private fun showAddPatientOptions() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Gestionar Pacientes")

        val options = arrayOf(
            "Crear nuevo paciente",
            "Asignar paciente existente"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> createNewPatient()
                1 -> assignExistingPatient()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun createNewPatient() {
        val intent = Intent(this, CreateEditPatientActivity::class.java)
        intent.putExtra("caregiver_id", caregiverId)
        intent.putExtra("caregiver_name", caregiverName)
        startActivity(intent)
    }

    private fun assignExistingPatient() {
        // TODO: Implementar selector de pacientes existentes sin cuidador
        Toast.makeText(this, "Funcionalidad de asignación en desarrollo", Toast.LENGTH_SHORT).show()
    }

    private fun showUnassignConfirmation(patient: PatientListItem) {
        AlertDialog.Builder(this)
            .setTitle("¿Desasignar paciente?")
            .setMessage("¿Estás seguro de que quieres desasignar a ${patient.nombresApellidos} de este cuidador?")
            .setPositiveButton("Desasignar") { _, _ ->
                unassignPatient(patient.id)
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun unassignPatient(patientId: Int) {
        lifecycleScope.launch {
            caregiverRepository.unassignPatientFromCaregiver(caregiverId, patientId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Paciente desasignado exitosamente", Toast.LENGTH_SHORT).show()
                        loadPatients() // Recargar lista
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error al desasignar paciente: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CaregiverPatientsManagementActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Recargar pacientes cuando se regrese a la pantalla
        loadPatients()
    }
}