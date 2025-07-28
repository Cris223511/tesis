package com.example.serious_game_usil.presentation.ui.administrador.list

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ListUsersBinding
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.ui.admin.adapter.UsersAdapter
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator


class ListUserActivity : AppCompatActivity(), UsersAdapter.OnUserActionListener {

    private lateinit var binding: ListUsersBinding
    private lateinit var viewModel: UsersListViewModel
    private lateinit var usersAdapter: UsersAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var currentPage = 1
    private var totalPages = 1
    private var searchQuery: String? = null
    private var isLoading = false
    private var isLastPage = false
    private var isSearching = false

    private val allUsers = mutableListOf<UserListItem>()

    companion object {
        private const val PER_PAGE = 10
        private const val REQUEST_ADD_USER = 1001
        private const val REQUEST_EDIT_USER = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!RouteNavigator.checkAuthAndNavigate(this, "admin")) {
            return
        }

        binding = ListUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        setupRecyclerView()
        setupSearch()
        setupPagination()
        observeViewModel()

        loadUsers(resetList = true)
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

        }

        binding.swipeRefresh.setOnRefreshListener {
            if (!isSearching) {
                resetAndLoadUsers()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Texto informativo de paginación
        updatePaginationInfo()
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(this)
        layoutManager = LinearLayoutManager(this)

        binding.usersRecyclerView.apply {
            this.layoutManager = this@ListUserActivity.layoutManager
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

                    if (query.isNullOrEmpty()) {
                        // Si el campo está vacío, mostrar todos los usuarios
                        isSearching = false
                        searchQuery = null
                        resetAndLoadUsers()
                    } else {
                        // Buscar mientras escribe
                        isSearching = true
                        searchQuery = query
                        performSearch(query)
                    }
                }

                // Esperar 300ms antes de buscar para evitar demasiadas llamadas
                binding.searchEditText.postDelayed(searchRunnable, 300)
            }
        })
    }

    private fun setupPagination() {
        binding.usersRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isSearching && !isLoading && !isLastPage) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= PER_PAGE) {
                        loadMoreUsers()
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.usersState.observe(this) { state ->
            when (state) {
                is UsersListState.Loading -> {
                    if (currentPage == 1) {
                        showLoading(true)
                    } else {
                        showPaginationLoading(true)
                    }
                }
                is UsersListState.Success -> {
                    showLoading(false)
                    showPaginationLoading(false)

                    if (currentPage == 1) {
                        allUsers.clear()
                    }

                    allUsers.addAll(state.users)
                    usersAdapter.submitList(allUsers.toList())

                    totalPages = state.totalPages
                    isLastPage = currentPage >= totalPages

                    updatePaginationInfo()

                    if (allUsers.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                    }
                }
                is UsersListState.Error -> {
                    showLoading(false)
                    showPaginationLoading(false)
                    showError(state.message)
                }
            }
        }

        viewModel.searchState.observe(this) { state ->
            when (state) {
                is SearchState.Loading -> {
                    binding.searchProgressBar.visibility = View.VISIBLE
                    binding.emptySearchLayout.visibility = View.GONE
                }
                is SearchState.Success -> {
                    binding.searchProgressBar.visibility = View.GONE

                    if (state.users.isEmpty()) {
                        // No hay coincidencias
                        binding.emptySearchLayout.visibility = View.VISIBLE
                        binding.emptySearchText.text = "No se encontraron usuarios que coincidan con '$searchQuery'"
                        binding.usersRecyclerView.visibility = View.GONE
                        updatePaginationInfo(0, 0)
                    } else {
                        binding.emptySearchLayout.visibility = View.GONE
                        binding.usersRecyclerView.visibility = View.VISIBLE
                        usersAdapter.submitList(state.users)
                        updatePaginationInfo(state.users.size, state.users.size)
                    }
                }
                is SearchState.Error -> {
                    binding.searchProgressBar.visibility = View.GONE
                    showError(state.message)
                }
            }
        }

        viewModel.actionState.observe(this) { state ->
            when (state) {
                is UserActionState.Success -> {
                    showSnackbar(state.message)
                    if (isSearching && !searchQuery.isNullOrEmpty()) {
                        performSearch(searchQuery!!)
                    } else {
                        resetAndLoadUsers()
                    }
                }
                is UserActionState.Error -> {
                    showError(state.message)
                }
                else -> {}
            }
        }
    }

    private fun loadUsers(resetList: Boolean = false) {
        if (!isLoading && !isSearching) {
            if (resetList) {
                currentPage = 1
                isLastPage = false
            }
            isLoading = true
            viewModel.loadUsers(currentPage, perPage = PER_PAGE)
        }
    }

    private fun loadMoreUsers() {
        if (currentPage < totalPages) {
            currentPage++
            loadUsers(resetList = false)
        }
    }

    private fun resetAndLoadUsers() {
        allUsers.clear()
        currentPage = 1
        isLastPage = false
        loadUsers(resetList = true)
    }

    private fun performSearch(query: String) {
        viewModel.searchUsers(query)
    }

    private fun updatePaginationInfo(showing: Int? = null, total: Int? = null) {
        val showingCount = showing ?: allUsers.size
        val totalCount = total ?: (totalPages * PER_PAGE)

        binding.paginationInfoText.text = if (isSearching) {
            if (showingCount > 0) {
                "Se encontraron $showingCount usuarios"
            } else {
                ""
            }
        } else {
            "Mostrando $showingCount de aproximadamente $totalCount usuarios"
        }
    }

    private fun showLoading(show: Boolean) {
        isLoading = show
        if (currentPage == 1) {
            binding.swipeRefresh.isRefreshing = show
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showPaginationLoading(show: Boolean) {
        binding.paginationProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.usersRecyclerView.visibility = View.GONE
        binding.emptyStateText.text = "No hay usuarios registrados"
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptySearchLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Reintentar") {
                if (isSearching && !searchQuery.isNullOrEmpty()) {
                    performSearch(searchQuery!!)
                } else {
                    loadUsers(resetList = true)
                }
            }
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    // Implementación de UsersAdapter.OnUserActionListener

    override fun onWhatsAppClick(user: UserListItem) {
        if (!user.telefono.isNullOrEmpty()) {
            val phoneNumber = user.telefono.replace(Regex("[^0-9]"), "")
            val countryCode = "51" // Código de Perú
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



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_ADD_USER, REQUEST_EDIT_USER -> {
                    if (isSearching && !searchQuery.isNullOrEmpty()) {
                        performSearch(searchQuery!!)
                    } else {
                        resetAndLoadUsers()
                    }
                }
            }
        }
    }

    override fun onEditClick(user: UserListItem) {
        // Por el momento no hace nada
        showSnackbar("Función en desarrollo")

        // Cuando esté listo, descomentar:
        /*
        val intent = Intent(this, EditUserActivity::class.java).apply {
            putExtra("user_id", user.id)
        }
        startActivityForResult(intent, REQUEST_EDIT_USER)
        */
    }




}