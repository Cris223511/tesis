package com.example.serious_game_usil.presentation.ui.administrador.list

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ListUsersBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.presentation.ui.password.ResetPasswordActivity
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.ui.admin.adapter.UsersAdapter
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator




class ListUserActivity : AppCompatActivity(), UsersAdapter.OnUserActionListener {

    private lateinit var binding: ListUsersBinding
    private lateinit var viewModel: UsersListViewModel
    private lateinit var usersAdapter: UsersAdapter

    private var currentPage = 1
    private var totalPages = 1
    private var totalUsers = 0
    private var searchQuery: String? = null
    private var isLoading = false
    private var isSearching = false

    companion object {
        private const val PER_PAGE = 10
        private  const val REQUEST_CREATE_USER = 1001
        private const val REQUEST_EDIT_USER = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasAdminRole = AuthManager.hasRole("administrador") || AuthManager.hasRole("admin")
        if (!AuthManager.isAuthenticated() || !hasAdminRole) {
            RouteNavigator.navigateToUnauthorized(this)
            return
        }

        binding = ListUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        setupRecyclerView()
        setupSearch()
        setupPaginationButtons()
        observeViewModel()

        loadUsers()
    }


    private val createUserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentPage = 1
            searchQuery = null
            isSearching = false
            binding.searchEditText.setText("")

            loadUsers()
            showSnackbar("Usuario creado exitosamente")
        }
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = UsersListViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[UsersListViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Gestión de Usuarios"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.fabAddUser.setOnClickListener {
            val intent = Intent(this, CreateUserActivity::class.java)
            startActivityForResult(intent, REQUEST_CREATE_USER)
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (!isSearching) {
                currentPage = 1
                loadUsers()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.setText("")
            searchQuery = null
            isSearching = false
            currentPage = 1
            loadUsers()
        }
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(this)
        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListUserActivity)
            adapter = usersAdapter
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
                            loadUsers()
                        } else {
                            isSearching = true
                            searchUsers(query)
                        }
                    }
                }

                binding.searchEditText.postDelayed(searchRunnable, 500)
            }
        })
    }

    private fun setupPaginationButtons() {
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    searchUsers(searchQuery!!)
                } else {
                    loadUsers()
                }
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    searchUsers(searchQuery!!)
                } else {
                    loadUsers()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.usersState.observe(this) { state ->
            when (state) {
                is UsersListState.Loading -> {
                    showLoading(true)
                }
                is UsersListState.Success -> {
                    showLoading(false)

                    totalPages = state.totalPages
                    totalUsers = state.total.toInt()

                    usersAdapter.submitList(state.users)

                    updatePaginationUI(state.users.size)

                    if (state.users.isEmpty()) {
                        if (isSearching) {
                            showEmptySearch()
                        } else {
                            showEmptyState()
                        }
                    } else {
                        hideEmptyStates()
                    }
                }
                is UsersListState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }

        viewModel.searchState.observe(this) { state ->
            when (state) {
                is SearchState.Loading -> {
                    binding.searchProgressBar.visibility = View.VISIBLE
                }
                is SearchState.Success -> {
                    binding.searchProgressBar.visibility = View.GONE

                    usersAdapter.submitList(state.users)

                    totalPages = 1
                    totalUsers = state.users.size

                    updatePaginationUI(state.users.size)

                    if (state.users.isEmpty()) {
                        showEmptySearch()
                    } else {
                        hideEmptyStates()
                    }
                }
                is SearchState.Error -> {
                    binding.searchProgressBar.visibility = View.GONE
                    showError("Error en la búsqueda: ${state.message}")
                }
            }
        }

        viewModel.actionState.observe(this) { state ->
            when (state) {
                is UserActionState.Success -> {
                    showSnackbar(state.message)
                    loadUsers()
                }
                is UserActionState.Error -> {
                    showError(state.message)
                }
                else -> {}
            }
        }
    }

    private fun loadUsers() {
        if (!isLoading) {
            isLoading = true
            viewModel.loadUsers(currentPage, perPage = PER_PAGE)
        }
    }

    private fun searchUsers(query: String) {
        viewModel.searchUsers(query)
    }

    private fun updatePaginationUI(currentCount: Int) {
        val startItem = ((currentPage - 1) * PER_PAGE) + 1
        val endItem = startItem + currentCount - 1

        binding.paginationInfoText.text = if (isSearching) {
            "Se encontraron $totalUsers usuarios"
        } else {
            "Mostrando $startItem-$endItem de $totalUsers usuarios"
        }

        binding.pageInfoText.text = "Página $currentPage de $totalPages"

        binding.btnPrevious.isEnabled = currentPage > 1
        binding.btnNext.isEnabled = currentPage < totalPages

        binding.paginationLayout.visibility = if (totalPages > 1) View.VISIBLE else View.GONE
    }

    private fun showLoading(show: Boolean) {
        isLoading = show
        binding.swipeRefresh.isRefreshing = show
        binding.progressBar.visibility = if (show && currentPage == 1) View.VISIBLE else View.GONE

        if (show) {
            binding.paginationLayout.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptySearchLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.GONE
        binding.paginationLayout.visibility = View.GONE
    }

    private fun showEmptySearch() {
        binding.emptySearchLayout.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.GONE
        binding.paginationLayout.visibility = View.GONE
        binding.emptySearchText.text = "No se encontraron usuarios que coincidan con '$searchQuery'"
    }

    private fun hideEmptyStates() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptySearchLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Reintentar") {
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    searchUsers(searchQuery!!)
                } else {
                    loadUsers()
                }
            }
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    // OnUserActionListener implementation

    override fun onEditClick(user: UserListItem) {
        val intent = Intent(this, EditUserActivity::class.java).apply {
            putExtra("userId", user.id)
        }
        startActivityForResult(intent, REQUEST_EDIT_USER)
    }


    override fun onDeleteClick(user: UserListItem) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar usuario")
            .setMessage("¿Estás seguro de eliminar al usuario ${user.nombresApellidos}?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteUser(user.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onToggleStatusClick(user: UserListItem) {
        val action = if (user.activo) "desactivar" else "activar"

        AlertDialog.Builder(this)
            .setTitle("Confirmar acción")
            .setMessage("¿Estás seguro de $action al usuario ${user.nombresApellidos}?")
            .setPositiveButton("Sí") { _, _ ->
                viewModel.toggleUserStatus(user)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onChangePasswordClick(user: UserListItem) {
        AlertDialog.Builder(this)
            .setTitle("Cambiar contraseña")
            .setMessage("¿Deseas cambiar la contraseña del usuario ${user.nombresApellidos}?")
            .setPositiveButton("Sí") { _, _ ->
                val intent = Intent(this, ResetPasswordActivity::class.java).apply {
                    putExtra("userId", user.id)
                    putExtra("userEmail", user.correo)
                    putExtra("userName", user.nombresApellidos)
                    putExtra("isAdminReset", true)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onWhatsAppClick(user: UserListItem) {
        if (!user.telefono.isNullOrEmpty()) {
            val phoneNumber = user.telefono.replace(Regex("[^0-9]"), "")
            val countryCode = "51"
            val fullNumber = if (phoneNumber.startsWith(countryCode)) phoneNumber else "$countryCode$phoneNumber"
            val url = "https://wa.me/$fullNumber"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                showSnackbar("No se pudo abrir WhatsApp")
            }
        } else {
            showSnackbar("El usuario no tiene número de teléfono")
        }
    }
    
    override fun onViewProfileClick(user: UserListItem) {
        val intent = Intent(this, com.example.serious_game_usil.presentation.ui.administrador.profile.ProfileActivity::class.java).apply {
            putExtra("USER_ID", user.id)
        }
        startActivity(intent)
    }
}