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
        // Setup RecyclerView sin acciones de editar/eliminar
        patientsAdapter = MyPatientsAdapter(
            onItemClick = { patient ->
                val intent = PatientDetailActivity.newIntent(this, patient.id)
                startActivity(intent)
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
        supportActionBar?.title = "Mis Pacientes Asignados"

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
        if (patients.isEmpty()) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewPatients.visibility = android.view.View.GONE

            if (isSearching) {
                binding.tvEmptyTitle.text = "Sin resultados"
                binding.tvEmptyMessage.text = "No se encontraron pacientes que coincidan con \"${getCurrentSearchQuery()}\""
            } else {
                binding.tvEmptyTitle.text = "Sin pacientes asignados"
                binding.tvEmptyMessage.text = "Aún no tienes pacientes bajo tu cuidado. Contacta a tu terapeuta."
            }
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerViewPatients.visibility = android.view.View.VISIBLE
        }
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