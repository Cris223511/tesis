package com.example.serious_game_usil.presentation.ui.padres

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
import com.example.serious_game_usil.databinding.ActivityMyPatientsBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.patients.PatientDetailActivity
import com.example.serious_game_usil.utils.MyPatientsAdapter
import kotlinx.coroutines.launch

class MyPatientsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyPatientsBinding
    private val viewModel: PadresDashboardViewModel by viewModels()
    private lateinit var patientsAdapter: MyPatientsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyPatientsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()
        loadPatients()
    }

    private fun setupUI() {
        // Setup RecyclerView con menú de opciones
        patientsAdapter = MyPatientsAdapter(
            onItemClick = { patient ->
                // Click en la tarjeta ya no hace nada, el menú maneja las acciones
            },
            onViewDetailsClick = { patient ->
                val intent = PatientDetailActivity.newIntent(this, patient.id)
                startActivity(intent)
            },
            onViewSessionsClick = { patient ->
                // Navegar a vista de sesiones del paciente
                navigateToPatientSessions(patient)
            },
            onViewReportClick = { patient ->
                // Navegar a vista de reporte del paciente
                navigateToPatientReport(patient)
            }
        )

        binding.recyclerViewPatients.apply {
            layoutManager = LinearLayoutManager(this@MyPatientsActivity)
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

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Cambiar título para administradores
        val isAdmin = AuthManager.isAdmin()
        supportActionBar?.title = if (isAdmin) {
            "Todos los Pacientes del Sistema"
        } else {
            "Mis Pacientes Asignados"
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadPatients()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.myPatients.collect { patients ->
                val filteredPatients = if (getCurrentSearchQuery().isNotEmpty()) {
                    filterPatients(patients, getCurrentSearchQuery())
                } else {
                    patients
                }

                patientsAdapter.updatePatients(filteredPatients)
                updateEmptyState(filteredPatients, isSearching = getCurrentSearchQuery().isNotEmpty())
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadPatients() {
        binding.swipeRefresh.isRefreshing = true
        viewModel.loadMyPatients()
    }

    private fun searchPatients(query: String?) {
        val allPatients = viewModel.myPatients.value
        val filteredPatients = if (!query.isNullOrBlank()) {
            filterPatients(allPatients, query)
        } else {
            allPatients
        }

        patientsAdapter.updatePatients(filteredPatients)
        updateEmptyState(filteredPatients, isSearching = !query.isNullOrBlank())
    }

    private fun filterPatients(patients: List<PatientListItem>, query: String): List<PatientListItem> {
        return patients.filter { patient ->
            patient.nombresApellidos.contains(query, ignoreCase = true) ||
            patient.numDocumento.contains(query, ignoreCase = true) ||
            patient.tipoDocumento.contains(query, ignoreCase = true)
        }
    }

    private fun getCurrentSearchQuery(): String {
        return binding.searchView.query.toString()
    }

    private fun updateEmptyState(patients: List<PatientListItem>, isSearching: Boolean) {
        val isAdmin = AuthManager.isAdmin()

        if (patients.isEmpty() && !isAdmin) {
            // Solo mostrar empty state si NO es administrador
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewPatients.visibility = android.view.View.GONE

            if (isSearching) {
                binding.tvEmptyTitle.text = "Sin resultados"
                binding.tvEmptyMessage.text = "No se encontraron pacientes que coincidan con \"${getCurrentSearchQuery()}\""
            } else {
                binding.tvEmptyTitle.text = "Sin pacientes asignados"
                binding.tvEmptyMessage.text = "Aún no tienes pacientes bajo tu cuidado. Contacta a tu terapeuta."
            }
        } else if (patients.isEmpty() && isAdmin) {
            // Los administradores ven una vista diferente
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewPatients.visibility = android.view.View.GONE
            binding.tvEmptyTitle.text = "Vista de Administrador"
            binding.tvEmptyMessage.text = "Como administrador, tienes acceso completo a todos los pacientes del sistema"
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerViewPatients.visibility = android.view.View.VISIBLE
        }
    }

    private fun navigateToPatientSessions(patient: PatientListItem) {
        // Navegar a la actividad de sesiones filtrada por este paciente
        val intent = Intent(this, com.example.serious_game_usil.presentation.ui.therapy.SimpleTherapySessionsActivity::class.java)
        intent.putExtra("patient_id", patient.id)
        intent.putExtra("patient_name", patient.nombresApellidos)
        startActivity(intent)
    }

    private fun navigateToPatientReport(patient: PatientListItem) {
        // Navegar a la actividad de progreso/estadísticas del paciente
        val intent = Intent(this, com.example.serious_game_usil.presentation.ui.progress.ProgressDetailActivity::class.java)
        intent.putExtra("patient_id", patient.id)
        intent.putExtra("patient_name", patient.nombresApellidos)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadPatients() // Refresh when returning
    }
}