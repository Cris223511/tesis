package com.example.serious_game_usil.presentation.ui.caregivers

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.Caregiver
import com.example.serious_game_usil.data.CaregiverListResponse
import com.example.serious_game_usil.databinding.ActivityCaregiversListBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.utils.CaregiversAdapter
import kotlinx.coroutines.launch

class CaregiversListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaregiversListBinding
    private val viewModel: CaregiversViewModel by viewModels { CaregiversViewModelFactory() }
    private lateinit var caregiversAdapter: CaregiversAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaregiversListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()
        loadCaregivers()
    }

    private fun setupUI() {
        // Verificar permisos del usuario
        val userRoles = AuthManager.getUserRoles()
        val canManageCaregivers = userRoles.any {
            it.lowercase() in listOf("admin", "administrador", "terapeuta", "therapist")
        }

        // Setup RecyclerView
        caregiversAdapter = CaregiversAdapter(
            onEditClick = if (canManageCaregivers) { { caregiver ->
                val intent = Intent(this, CreateEditCaregiverActivity::class.java)
                intent.putExtra("caregiver_id", caregiver.id)
                startActivity(intent)
            } } else { { _ ->
                Toast.makeText(this, "No tienes permisos para editar cuidadores", Toast.LENGTH_SHORT).show()
            } },
            onDeleteClick = if (canManageCaregivers) { { caregiver ->
                showDeleteConfirmation(caregiver.id, caregiver.nombresApellidos)
            } } else { { _ ->
                Toast.makeText(this, "No tienes permisos para eliminar cuidadores", Toast.LENGTH_SHORT).show()
            } },
            onViewPatientsClick = { caregiver ->
                showCaregiverPatients(caregiver)
            },
            onItemClick = { caregiver ->
                val intent = CaregiverDetailActivity.newIntent(this, caregiver.id)
                startActivity(intent)
            },
            showEditDeleteButtons = canManageCaregivers
        )

        binding.recyclerViewCaregivers.apply {
            layoutManager = LinearLayoutManager(this@CaregiversListActivity)
            adapter = caregiversAdapter
        }

        // Setup search
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchCaregivers(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    searchCaregivers(null)
                } else if (newText.length >= 2) {
                    searchCaregivers(newText)
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

        // Setup FAB - Solo para usuarios con permisos
        if (canManageCaregivers) {
            binding.fabAddCaregiver.setOnClickListener {
                val intent = Intent(this, CreateEditCaregiverActivity::class.java)
                startActivity(intent)
            }
        } else {
            binding.fabAddCaregiver.visibility = android.view.View.GONE
        }

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lista de cuidadores"
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.caregivers.collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val caregivers = result.data.caregivers ?: emptyList()
                        caregiversAdapter.updateCaregivers(caregivers)
                        updatePaginationControls(result.data)
                        updateEmptyState(caregivers, isSearching = getCurrentSearchQuery().isNotEmpty())
                        binding.swipeRefresh.isRefreshing = false
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CaregiversListActivity, result.message, Toast.LENGTH_SHORT).show()
                        binding.swipeRefresh.isRefreshing = false
                        updateEmptyState(emptyList(), isSearching = false, isError = true)
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CaregiversListActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                        binding.swipeRefresh.isRefreshing = false
                        updateEmptyState(emptyList(), isSearching = false, isError = true)
                    }
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadCaregivers()
        }
    }

    private fun loadCaregivers() {
        binding.swipeRefresh.isRefreshing = true
        viewModel.loadCaregivers()
    }

    private fun searchCaregivers(query: String?) {
        viewModel.searchCaregivers(query)
    }

    private fun getCurrentSearchQuery(): String {
        return binding.searchView.query.toString()
    }

    private fun updatePaginationControls(response: CaregiverListResponse) {
        val hasPagination = (response.total ?: 0) > 10

        if (hasPagination) {
            binding.paginationControls.visibility = android.view.View.VISIBLE
            binding.btnPrevious.isEnabled = response.hasPrevious == true
            binding.btnNext.isEnabled = response.hasNext == true

            val currentPage = response.page ?: 1
            val totalPages = response.totalPages ?: 1
            binding.tvPageInfo.text = "Página $currentPage de $totalPages"
        } else {
            binding.paginationControls.visibility = android.view.View.GONE
        }
    }

    private fun updateEmptyState(caregivers: List<Caregiver>, isSearching: Boolean, isError: Boolean = false) {
        if (caregivers.isEmpty()) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerViewCaregivers.visibility = android.view.View.GONE

            when {
                isError -> {
                    binding.tvEmptyTitle.text = "Error al cargar cuidadores"
                    binding.tvEmptyMessage.text = "Verifica tu conexión e intenta nuevamente"
                }
                isSearching -> {
                    binding.tvEmptyTitle.text = "Sin resultados"
                    binding.tvEmptyMessage.text = "No se encontraron cuidadores que coincidan con \"${getCurrentSearchQuery()}\""
                }
                else -> {
                    binding.tvEmptyTitle.text = "No hay cuidadores registrados"
                    binding.tvEmptyMessage.text = "Toca el botón + para agregar tu primer cuidador"
                }
            }
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerViewCaregivers.visibility = android.view.View.VISIBLE
        }
    }

    private fun showCaregiverPatients(caregiver: Caregiver) {
        // Navegar a la pantalla completa de gestión de pacientes del cuidador
        val intent = CaregiverPatientsManagementActivity.newIntent(
            this,
            caregiver.id,
            caregiver.nombresApellidos
        )
        startActivity(intent)
    }


    private fun showDeleteConfirmation(caregiverId: Int, caregiverName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¿Estás seguro de eliminar el cuidador?")
            .setMessage("Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteCaregiver(caregiverId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteCaregiver(caregiverId: Int) {
        lifecycleScope.launch {
            viewModel.deleteCaregiver(caregiverId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this@CaregiversListActivity, "Cuidador eliminado con éxito", Toast.LENGTH_SHORT).show()
                        loadCaregivers() // Reload list
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this@CaregiversListActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.NetworkError -> {
                        Toast.makeText(this@CaregiversListActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadCaregivers() // Refresh when returning from create/edit
    }
}