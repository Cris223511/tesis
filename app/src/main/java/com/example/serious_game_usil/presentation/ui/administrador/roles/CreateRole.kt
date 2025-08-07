package com.example.serious_game_usil.presentation.ui.administrador.roles

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.databinding.ActivityCreatRolesBinding
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.repository.UserRepository
import com.google.android.material.snackbar.Snackbar
import com.seriousgame.app.navigation.RouteNavigator


class CreateRole : AppCompatActivity() {

    private lateinit var binding: ActivityCreatRolesBinding
    private lateinit var viewModel: CreateRoleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthManager.isAuthenticated() || !AuthManager.hasRole("administrador")) {
            RouteNavigator.navigateToUnauthorized(this)
            return
        }

        binding = ActivityCreatRolesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        setupValidation()
        observeViewModel()
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = CreateRoleViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[CreateRoleViewModel::class.java]
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

        binding.createButton.setOnClickListener {
            if (validateForm()) {
                createRole()
            }
        }
    }

    private fun setupValidation() {
        binding.roleNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateRoleName()
                updateCreateButtonState()
            }
        })
    }

    private fun observeViewModel() {
        viewModel.createRoleState.observe(this) { state ->
            when (state) {
                is CreateRoleViewModel.CreateRoleState.Loading -> {
                    showLoading(true)
                }
                is CreateRoleViewModel.CreateRoleState.Success -> {
                    showLoading(false)
                    Snackbar.make(binding.root, "Rol creado exitosamente", Snackbar.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is CreateRoleViewModel.CreateRoleState.Error -> {
                    showLoading(false)
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validateRoleName(): Boolean {
        val roleName = binding.roleNameEditText.text.toString().trim()

        return when {
            roleName.isEmpty() -> {
                binding.roleNameInputLayout.error = null
                false
            }
            roleName.length < 3 -> {
                binding.roleNameInputLayout.error = "Mínimo 3 caracteres"
                false
            }
            roleName.lowercase() in listOf("administrador", "admin", "estudiante", "docente") -> {
                binding.roleNameInputLayout.error = "Este nombre está reservado"
                false
            }
            roleName.contains("DELETED_") -> {
                binding.roleNameInputLayout.error = "Nombre no permitido"
                false
            }
            !roleName.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) -> {
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

    private fun updateCreateButtonState() {
        binding.createButton.isEnabled = validateRoleName()
    }

    private fun createRole() {
        val roleName = binding.roleNameEditText.text.toString().trim()
        viewModel.createRole(roleName)
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            createButton.isEnabled = !show
            cancelButton.isEnabled = !show
            roleNameEditText.isEnabled = !show
            if (show) {
                createButton.text = "Creando..."
            } else {
                createButton.text = "Crear Rol"
            }
        }
    }
}