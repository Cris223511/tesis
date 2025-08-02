package com.example.serious_game_usil.presentation.ui.administrador.roles



import android.os.Bundle
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
            showSnackbar("Crear rol - En desarrollo")
        }

        binding.swipeRefresh.setOnRefreshListener {
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

    private fun observeViewModel() {
        viewModel.rolesState.observe(this) { state ->
            when (state) {
                is RolesViewModel.RolesState.Loading -> {
                    showLoading(true)
                    hideEmptyState()
                }
                is RolesViewModel.RolesState.Success -> {
                    showLoading(false)
                    if (state.roles.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        rolesAdapter.submitList(state.roles)
                    }
                }
                is RolesViewModel.RolesState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }

        viewModel.actionState.observe(this) { state ->
            when (state) {
                is RolesViewModel.ActionState.Success -> {
                    showSnackbar(state.message)
                    viewModel.loadRoles()
                }
                is RolesViewModel.ActionState.Error -> {
                    showError(state.message)
                }
            }
        }
    }

    // Implementación de OnRoleActionListener con Role en lugar de RoleItem
    override fun onEditClick(role: Role) {
        showSnackbar("Editar rol ${role.name} - En desarrollo")
    }

    override fun onDeleteClick(role: Role) {
        // Verificar si es rol del sistema
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
        // Mostrar permisos basados en el nombre del rol
        val permissions = when (role.name.lowercase()) {
            "administrador", "admin" -> listOf(
                "Gestionar usuarios",
                "Gestionar roles",
                "Ver reportes completos",
                "Configurar sistema",
                "Acceso total"
            )
            "docente" -> listOf(
                "Gestionar cursos",
                "Calificar estudiantes",
                "Ver reportes de curso",
                "Subir material educativo"
            )
            "estudiante" -> listOf(
                "Ver cursos",
                "Realizar evaluaciones",
                "Ver calificaciones",
                "Descargar material"
            )
            else -> listOf("Permisos personalizados")
        }

        val permissionsText = permissions.joinToString("\n• ", "• ")

        AlertDialog.Builder(this)
            .setTitle("Permisos de ${role.name}")
            .setMessage(permissionsText)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onViewUsersClick(role: Role) {
        showSnackbar("Ver usuarios con rol ${role.name} - En desarrollo")
    }

    private fun showLoading(show: Boolean) {
        binding.swipeRefresh.isRefreshing = show
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.rolesRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.rolesRecyclerView.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Reintentar") {
                viewModel.loadRoles()
            }
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}