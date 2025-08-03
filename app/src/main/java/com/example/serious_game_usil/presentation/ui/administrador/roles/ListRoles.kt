package com.example.serious_game_usil.presentation.ui.administrador.roles

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.databinding.ActivityRolesBinding
import com.example.serious_game_usil.guards.AuthManager

import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.utils.RolesAdapter

import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator



class ListRoles : AppCompatActivity(), RolesAdapter.OnRoleActionListener {

    private lateinit var binding: ActivityRolesBinding
    private lateinit var viewModel: RolesViewModel
    private lateinit var rolesAdapter: RolesAdapter

    private var currentPage = 1
    private var totalPages = 1
    private var isSearching = false
    private var searchQuery: String? = null

    companion object {
        private const val REQUEST_CREATE_ROLE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isAuthenticated() || !AuthManager.hasRole("administrador")) {
            RouteNavigator.navigateToUnauthorized(this)
            return
        }

        binding = ActivityRolesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        setupRecyclerView()
        setupSearch()
        setupPagination()
        observeViewModel()

        viewModel.loadRoles()
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = RolesViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[RolesViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Gestión de Roles"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.fabAddRole.setOnClickListener {
            val intent = Intent(this, CreateRole::class.java)
            startActivityForResult(intent, REQUEST_CREATE_ROLE)
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (!isSearching) {
                currentPage = 1
                viewModel.loadRoles()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.setText("")
            searchQuery = null
            isSearching = false
            currentPage = 1
            viewModel.loadRoles()
        }
    }

    private fun setupRecyclerView() {
        rolesAdapter = RolesAdapter(this)
        binding.rolesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListRoles)
            adapter = rolesAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchRunnable: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { binding.searchEditText.removeCallbacks(it) }
            }

            override fun afterTextChanged(s: Editable?) {
                searchRunnable = Runnable {
                    val query = s?.toString()?.trim()

                    if (query != searchQuery) {
                        searchQuery = query
                        currentPage = 1

                        if (query.isNullOrEmpty()) {
                            isSearching = false
                            binding.clearSearchButton.visibility = View.GONE
                            viewModel.loadRoles()
                        } else {
                            isSearching = true
                            binding.clearSearchButton.visibility = View.VISIBLE
                            viewModel.searchRoles(query)
                        }
                    }
                }

                binding.searchEditText.postDelayed(searchRunnable, 500)
            }
        })
    }

    private fun setupPagination() {
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    viewModel.searchRoles(searchQuery!!)
                } else {
                    viewModel.loadRoles(currentPage)
                }
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    viewModel.searchRoles(searchQuery!!)
                } else {
                    viewModel.loadRoles(currentPage)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.rolesState.observe(this) { state ->
            when (state) {
                is RolesViewModel.RolesState.Loading -> {
                    showLoading(true)
                }
                is RolesViewModel.RolesState.Success -> {
                    showLoading(false)

                    totalPages = state.totalPages
                    currentPage = state.currentPage

                    rolesAdapter.submitList(state.roles)
                    updateResultInfo(state.roles.size, state.totalRoles)
                    updatePaginationUI()

                    if (state.roles.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyStates()
                    }
                }
                is RolesViewModel.RolesState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }

        viewModel.searchState.observe(this) { state ->
            when (state) {
                is RolesViewModel.SearchState.Loading -> {
                    binding.searchProgressBar.visibility = View.VISIBLE
                }
                is RolesViewModel.SearchState.Success -> {
                    binding.searchProgressBar.visibility = View.GONE

                    totalPages = state.totalPages
                    currentPage = state.currentPage

                    rolesAdapter.submitList(state.roles)
                    updateResultInfo(state.roles.size, state.totalResults)
                    updatePaginationUI()

                    if (state.roles.isEmpty()) {
                        showEmptySearch(state.query)
                    } else {
                        hideEmptyStates()
                    }
                }
                is RolesViewModel.SearchState.Error -> {
                    binding.searchProgressBar.visibility = View.GONE
                    showError("Error en la búsqueda: ${state.message}")
                }
            }
        }

        viewModel.actionState.observe(this) { state ->
            when (state) {
                is RolesViewModel.ActionState.Success -> {
                    showSnackbar(state.message)
                    viewModel.loadRoles(currentPage)
                }
                is RolesViewModel.ActionState.Error -> {
                    showError(state.message)
                }
            }
        }
    }

    override fun onEditClick(role: Role) {
        showSnackbar("Editar rol ${role.name} - En desarrollo")
    }

    override fun onDeleteClick(role: Role) {
        val isSystemRole = role.name.lowercase() in listOf("administrador", "estudiante", "docente", "admin")

        if (isSystemRole) {
            showError("No se pueden eliminar roles del sistema")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Eliminar rol")
            .setMessage("¿Estás seguro de eliminar el rol ${role.name}?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteRole(role.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onViewPermissionsClick(role: Role) {
        TODO("Not yet implemented")
    }


    override fun onViewUsersClick(role: Role) {
        showSnackbar("Ver usuarios con rol ${role.name} - En desarrollo")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CREATE_ROLE && resultCode == RESULT_OK) {
            currentPage = 1
            searchQuery = null
            isSearching = false
            binding.searchEditText.setText("")
            binding.clearSearchButton.visibility = View.GONE

            viewModel.loadRoles(1)
            showSnackbar("Rol creado exitosamente")
        }
    }

    private fun updateResultInfo(showing: Int, total: Int) {
        binding.resultInfoText.text = if (isSearching) {
            "Se encontraron $total roles"
        } else {
            val start = ((currentPage - 1) * 10) + 1
            val end = minOf(start + showing - 1, total)
            "Mostrando $start-$end de $total roles"
        }
    }

    private fun updatePaginationUI() {
        binding.pageInfoText.text = "Página $currentPage de $totalPages"
        binding.btnPrevious.isEnabled = currentPage > 1
        binding.btnNext.isEnabled = currentPage < totalPages

        binding.paginationLayout.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
    }

    private fun showLoading(show: Boolean) {
        binding.swipeRefresh.isRefreshing = show
        binding.progressBar.visibility = if (show && currentPage == 1) View.VISIBLE else View.GONE

        if (show) {
            binding.paginationLayout.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptySearchLayout.visibility = View.GONE
        binding.rolesRecyclerView.visibility = View.GONE
        binding.paginationLayout.visibility = View.GONE
    }

    private fun showEmptySearch(query: String) {
        binding.emptySearchLayout.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.rolesRecyclerView.visibility = View.GONE
        binding.paginationLayout.visibility = View.GONE
        binding.emptySearchText.text = "No se encontraron roles que coincidan con '$query'"
    }

    private fun hideEmptyStates() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptySearchLayout.visibility = View.GONE
        binding.rolesRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Reintentar") {
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    viewModel.searchRoles(searchQuery!!)
                } else {
                    viewModel.loadRoles(currentPage)
                }
            }
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()

        if (!AuthManager.isAuthenticated()) {
            RouteNavigator.navigateToLogin(this)
            return
        }

        if (currentPage == 1 && !isSearching) {
            viewModel.loadRoles(1)
        }
    }
}