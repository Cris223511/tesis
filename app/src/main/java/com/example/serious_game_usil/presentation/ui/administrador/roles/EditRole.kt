package com.example.serious_game_usil.presentation.ui.administrador.roles

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.databinding.ActivityEditRolesBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.repository.UserRepository
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator


class EditRole : AppCompatActivity() {

    private lateinit var binding: ActivityEditRolesBinding
    private lateinit var viewModel: EditRoleViewModel

    private var roleId: Int = -1
    private var currentRoleName: String = ""

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

        binding = ActivityEditRolesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roleId = intent.getIntExtra(EXTRA_ROLE_ID, -1)
        currentRoleName = intent.getStringExtra(EXTRA_ROLE_NAME) ?: ""

        if (roleId == -1 || currentRoleName.isEmpty()) {
            showError("Datos del rol inválidos")
            finish()
            return
        }

        setupViewModel()
        setupUI()
        setupValidation()
        observeViewModel()

        displayRoleInfo()
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = EditRoleViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[EditRoleViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }

        binding.updateButton.setOnClickListener {
            if (validateForm()) {
                updateRole()
            }
        }
    }

    private fun setupValidation() {
        binding.roleNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateRoleName()
                updateButtonState()
            }
        })
    }

    private fun displayRoleInfo() {
        binding.currentRoleNameText.text = currentRoleName

        val isSystemRole = currentRoleName.lowercase() in listOf("administrador", "estudiante", "docente", "admin")

        if (isSystemRole) {
            binding.roleNameEditText.isEnabled = false
            binding.updateButton.isEnabled = false
            binding.warningText.text = "Los roles del sistema no pueden ser modificados"
            binding.warningCard.setCardBackgroundColor(getColor(android.R.color.holo_red_light))
        }
    }

    private fun observeViewModel() {
        viewModel.updateRoleState.observe(this) { state ->
            when (state) {
                is EditRoleViewModel.UpdateRoleState.Loading -> {
                    showLoading(true)
                }
                is EditRoleViewModel.UpdateRoleState.Success -> {
                    showLoading(false)
                    Snackbar.make(binding.root, "Rol actualizado exitosamente", Snackbar.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is EditRoleViewModel.UpdateRoleState.Error -> {
                    showLoading(false)
                    showError(state.message)
                }
            }
        }
    }

    private fun validateRoleName(): Boolean {
        val newRoleName = binding.roleNameEditText.text.toString().trim()

        return when {
            newRoleName.isEmpty() -> {
                binding.roleNameInputLayout.error = null
                false
            }
            newRoleName == currentRoleName -> {
                binding.roleNameInputLayout.error = "El nombre debe ser diferente al actual"
                false
            }
            newRoleName.length < 3 -> {
                binding.roleNameInputLayout.error = "Mínimo 3 caracteres"
                false
            }
            newRoleName.lowercase() in listOf("administrador", "admin", "estudiante", "docente") -> {
                binding.roleNameInputLayout.error = "Este nombre está reservado"
                false
            }
            newRoleName.contains("DELETED_") -> {
                binding.roleNameInputLayout.error = "Nombre no permitido"
                false
            }
            !newRoleName.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) -> {
                binding.roleNameInputLayout.error = "Solo se permiten letras y espacios"
                false
            }
            else -> {
                binding.roleNameInputLayout.error = null
                true
            }
        }
    }

    private fun validateForm(): Boolean {
        return validateRoleName()
    }

    private fun updateButtonState() {
        binding.updateButton.isEnabled = validateRoleName()
    }

    private fun updateRole() {
        val newRoleName = binding.roleNameEditText.text.toString().trim()
        viewModel.updateRole(roleId, newRoleName)
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            updateButton.isEnabled = !show
            cancelButton.isEnabled = !show
            roleNameEditText.isEnabled = !show
            if (show) {
                updateButton.text = "Actualizando..."
            } else {
                updateButton.text = "Actualizar Rol"
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}