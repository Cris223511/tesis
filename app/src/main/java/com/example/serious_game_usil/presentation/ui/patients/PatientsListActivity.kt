package com.example.serious_game_usil.presentation.ui.patients

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.data.PatientsListResponse
import com.example.serious_game_usil.databinding.ActivityPatientsListBinding
import com.example.serious_game_usil.utils.PatientsAdapter
import kotlinx.coroutines.launch

class PatientsListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPatientsListBinding
    private val viewModel: PatientsViewModel by viewModels { PatientsViewModelFactory() }
    private lateinit var patientsAdapter: PatientsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()
        loadPatients()
    }

    private fun setupUI() {
        // Setup RecyclerView
        patientsAdapter = PatientsAdapter(
            onEditClick = { patient ->
                android.util.Log.d("PatientsListActivity", "Edit button clicked for patient: ${patient.id}")
                try {
                    val intent = Intent(this, CreateEditPatientActivity::class.java)
                    intent.putExtra("patient_id", patient.id)
                    android.util.Log.d("PatientsListActivity", "Starting CreateEditPatientActivity with patient_id: ${patient.id}")
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("PatientsListActivity", "Error starting CreateEditPatientActivity", e)
                    Toast.makeText(this, "Error al abrir editor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            onDeleteClick = { patient ->
                showDeleteConfirmation(patient.id, patient.nombresApellidos)
            },
            onItemClick = { patient ->
                val intent = PatientDetailActivity.newIntent(this, patient.id)
                startActivity(intent)
            }
        )

        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(this@PatientsListActivity)
            adapter = patientsAdapter
        }

        // Setup search
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchPatients(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    searchPatients(null)
                } else if (newText.length >= 2) {
                    searchPatients(newText)
                }
                return true
            }
        })

        // Setup pagination controls
        binding.btnPrevious.setOnClickListener {
            viewModel.loadPreviousPage()
        }

        binding.btnNext.setOnClickListener {
            viewModel.loadNextPage()
        }

        // Setup FAB
        binding.fabAddPatient.setOnClickListener {
            val intent = Intent(this, CreateEditPatientActivity::class.java)
            startActivity(intent)
        }

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lista de pacientes"
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.patients.collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val patients = result.data.patients ?: emptyList()
                        patientsAdapter.updatePatients(patients)
                        updatePaginationControls(result.data)
                        updateEmptyState(patients, isSearching = getCurrentSearchQuery().isNotEmpty())
                        binding.swipeRefresh.isRefreshing = false
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@PatientsListActivity, result.message, Toast.LENGTH_SHORT).show()
                        binding.swipeRefresh.isRefreshing = false
                        updateEmptyState(emptyList(), isSearching = false, isError = true)
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@PatientsListActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                        binding.swipeRefresh.isRefreshing = false
                        updateEmptyState(emptyList(), isSearching = false, isError = true)
                    }
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadPatients()
        }
    }

    private fun loadPatients() {
        binding.swipeRefresh.isRefreshing = true
        viewModel.loadPatients()
    }

    private fun searchPatients(query: String?) {
        viewModel.searchPatients(query)
    }

    private fun getCurrentSearchQuery(): String {
        return binding.searchView.query.toString()
    }

    private fun updatePaginationControls(response: PatientsListResponse) {
        val hasPagination = (response.total ?: 0) > 10

        if (hasPagination) {
            binding.paginationControls.visibility = android.view.View.VISIBLE
            binding.btnPrevious.isEnabled = response.has_previous == true
            binding.btnNext.isEnabled = response.has_next == true

            val currentPage = response.page ?: 1
            val totalPages = response.total_pages ?: 1
            binding.tvPageInfo.text = "Página $currentPage de $totalPages"
        } else {
            binding.paginationControls.visibility = android.view.View.GONE
        }
    }

    private fun updateEmptyState(patients: List<PatientListItem>, isSearching: Boolean, isError: Boolean = false) {
        if (patients.isEmpty()) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewPatients.visibility = android.view.View.GONE

            when {
                isError -> {
                    binding.tvEmptyTitle.text = "Error al cargar pacientes"
                    binding.tvEmptyMessage.text = "Verifica tu conexión e intenta nuevamente"
                }
                isSearching -> {
                    binding.tvEmptyTitle.text = "Sin resultados"
                    binding.tvEmptyMessage.text = "No se encontraron pacientes que coincidan con \"${getCurrentSearchQuery()}\""
                }
                else -> {
                    binding.tvEmptyTitle.text = "No hay pacientes registrados"
                    binding.tvEmptyMessage.text = "Toca el botón + para agregar tu primer paciente"
                }
            }
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerViewPatients.visibility = android.view.View.VISIBLE
        }
    }

    private fun showDeleteConfirmation(patientId: Int, patientName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¿Estás seguro de eliminar al paciente?")
            .setMessage("Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deletePatient(patientId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePatient(patientId: Int) {
        lifecycleScope.launch {
            viewModel.deletePatient(patientId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@PatientsListActivity, "Paciente eliminado con éxito", Toast.LENGTH_SHORT).show()
                        loadPatients() // Reload list
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@PatientsListActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@PatientsListActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun exportPatientData(patientId: Int) {
        Toast.makeText(this, "Exportando datos del paciente...", Toast.LENGTH_SHORT).show()
        // TODO: Implementar exportación de datos del paciente
        lifecycleScope.launch {
            // Aquí podrías llamar a un endpoint de exportación cuando esté disponible
            // Por ahora solo mostramos un mensaje
            Toast.makeText(this@PatientsListActivity, "Función de exportación en desarrollo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadPatients() // Refresh when returning from create/edit
    }
}