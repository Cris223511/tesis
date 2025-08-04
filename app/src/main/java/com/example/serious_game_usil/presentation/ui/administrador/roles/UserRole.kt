package com.example.serious_game_usil.presentation.ui.administrador.roles

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.databinding.ActivityUserRoleBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.utils.UserRoleAdapter
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator

class UserRole : AppCompatActivity(), UserRoleAdapter.OnUserClickListener {

    private lateinit var binding: ActivityUserRoleBinding
    private lateinit var viewModel: UserRoleViewModel
    private lateinit var userRoleAdapter: UserRoleAdapter

    private var roleId: Int = -1
    private var roleName: String = ""
    private var searchQuery: String? = null

    companion object {
        const val EXTRA_ROLE_ID = "role_id"
        const val EXTRA_ROLE_NAME = "role_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isAuthenticated() || !AuthManager.hasRole("administrador")) {
            RouteNavigator.navigateToUnauthorized(this)
            return
        }

        binding = ActivityUserRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roleId = intent.getIntExtra(EXTRA_ROLE_ID, -1)
        roleName = intent.getStringExtra(EXTRA_ROLE_NAME) ?: ""

        if (roleId == -1 || roleName.isEmpty()) {
            showError("Datos del rol inválidos")
            finish()
            return
        }

        setupViewModel()
        setupUI()
        setupRecyclerView()
        setupSearch()
        observeViewModel()

        viewModel.loadUsersWithRole(roleName)
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = UserRoleViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[UserRoleViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Usuarios: $roleName"
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.roleNameText.text = roleName
        binding.roleDescriptionText.text = when (roleName.lowercase()) {
            "administrador", "admin" -> "Acceso completo al sistema"
            "hijos" -> "Acceso a  evaluaciones de estimulación"
            "padres" -> "Gestión de pruebas de estimulación de sus hijos"
            else -> "Rol personalizado"
        }

        binding.swipeRefresh.setOnRefreshListener {
            searchQuery = null
            binding.searchEditText.setText("")
            viewModel.loadUsersWithRole(roleName)
        }

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.setText("")
            searchQuery = null
            viewModel.loadUsersWithRole(roleName)
        }
    }

    private fun setupRecyclerView() {
        userRoleAdapter = UserRoleAdapter(this)
        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@UserRole)
            adapter = userRoleAdapter
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

                        if (query.isNullOrEmpty()) {
                            binding.clearSearchButton.visibility = View.GONE
                            viewModel.loadUsersWithRole(roleName)
                        } else {
                            binding.clearSearchButton.visibility = View.VISIBLE
                            viewModel.searchUsersInRole(roleName, query)
                        }
                    }
                }

                binding.searchEditText.postDelayed(searchRunnable, 500)
            }
        })
    }


    private fun observeViewModel() {
        viewModel.usersState.observe(this) { state ->
            when (state) {
                is UserRoleViewModel.UsersState.Loading -> {
                    showLoading(true)
                }
                is UserRoleViewModel.UsersState.Success -> {
                    showLoading(false)


                    userRoleAdapter.notifyDataSetChanged()
                    userRoleAdapter.submitList(state.users)

                    binding.userCountText.text = state.users.size.toString()
                    updateResultInfo(state.users.size, searchQuery != null)

                    if (state.users.isEmpty()) {
                        if (searchQuery != null) {
                            showEmptySearch(searchQuery!!)
                        } else {
                            showEmptyState()
                        }
                    } else {
                        hideEmptyStates()
                    }
                }
                is UserRoleViewModel.UsersState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }
    }

    override fun onUserClick(user: UserListItem) {
        showUserDetails(user)
    }

    private fun showUserDetails(user: UserListItem) {
        val details = """
            Usuario: ${user.nombreUsuario}
            Nombre: ${user.nombresApellidos}
            Correo: ${user.correo}
            Teléfono: ${user.telefono ?: "No registrado"}
            Documento: ${user.tipoDocumento} ${user.numeroDocumento}
            Estado: ${if (user.activo) "Activo" else "Inactivo"}
            Roles: ${user.roles.joinToString(", ") { it.name }}
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Detalles del Usuario")
            .setMessage(details)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun updateResultInfo(count: Int, isSearching: Boolean) {
        binding.resultInfoText.text = if (isSearching) {
            "Se encontraron $count usuarios"
        } else {
            "Total: $count usuarios con rol $roleName"
        }
    }

    private fun showLoading(show: Boolean) {
        binding.swipeRefresh.isRefreshing = show
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptySearchLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.GONE
    }

    private fun showEmptySearch(query: String) {
        binding.emptySearchLayout.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.GONE
        binding.emptySearchText.text = "No se encontraron usuarios que coincidan con '$query'"
    }

    private fun hideEmptyStates() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.emptySearchLayout.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}