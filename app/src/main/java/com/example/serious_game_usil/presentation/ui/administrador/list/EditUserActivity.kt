package com.example.serious_game_usil.presentation.ui.administrador.list



import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.databinding.ActivityEditUserBinding

import com.example.serious_game_usil.repository.UserRepository

import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class EditUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditUserBinding
    private lateinit var viewModel: EditUserViewModel

    private val documentTypes = listOf("DNI", "CE", "Pasaporte")
    private val genders = listOf("Masculino", "Femenino", "Otro")
    private var selectedRoles = mutableSetOf<Int>()
    private var userId: Int = 0
    private var changePassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("userId", 0)
        if (userId == 0) {
            finish()
            return
        }

        setupViewModel()
        setupUI()
        setupDropdowns()
        setupValidation()
        observeViewModel()

        viewModel.loadUserData(userId)
        viewModel.loadRoles()
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = EditUserViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[EditUserViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.birthDateEditText.setOnClickListener {
            showDatePicker()
        }

        binding.changePasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
            changePassword = isChecked
            binding.passwordSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSaveButtonState()
        }

        binding.generatePasswordButton.setOnClickListener {
            generateRandomPassword()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            if (validateForm()) {
                updateUser()
            }
        }
    }

    private fun setupDropdowns() {
        val documentAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            documentTypes
        )
        binding.documentTypeDropdown.setAdapter(documentAdapter)
        binding.documentTypeDropdown.setText(documentTypes[0], false)

        val genderAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            genders
        )
        binding.genderDropdown.setAdapter(genderAdapter)
    }

    private fun setupValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButtonState()
            }
        }

        binding.apply {
            namesEditText.addTextChangedListener(textWatcher)
            documentNumberEditText.addTextChangedListener(textWatcher)
            birthDateEditText.addTextChangedListener(textWatcher)
            emailEditText.addTextChangedListener(textWatcher)

            emailEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    validateEmail()
                }
            })

            passwordEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (changePassword) validatePassword()
                }
            })
        }
    }

    private fun observeViewModel() {
        viewModel.userState.observe(this) { user ->
            user?.let {
                binding.apply {
                    namesEditText.setText(it.nombresApellidos)
                    documentTypeDropdown.setText(it.tipoDocumento, false)
                    documentNumberEditText.setText(it.numeroDocumento)
                    birthDateEditText.setText(formatDate(it.fechaNacimiento))

                    val gender = when (it.sexo) {
                        "M" -> "Masculino"
                        "F" -> "Femenino"
                        else -> "Otro"
                    }
                    genderDropdown.setText(gender, false)

                    phoneEditText.setText(it.telefono)
                    emailEditText.setText(it.correo)

                    selectedRoles.clear()
                    it.roles.forEach { role ->
                        selectedRoles.add(role.id)
                    }
                }
            }
        }

        viewModel.rolesState.observe(this) { roles ->
            val currentUser = viewModel.userState.value
            binding.rolesContainer.removeAllViews()

            // Obtener roles de administrador del usuario actual
            val adminRolesFromUser = currentUser?.roles?.filter {
                it.name.equals("AD", ignoreCase = true) ||
                it.name.equals("admin", ignoreCase = true) ||
                it.name.equals("administrador", ignoreCase = true)
            } ?: emptyList()

            val userHasAdminRole = adminRolesFromUser.isNotEmpty()

            // Crear set de IDs de roles admin del usuario
            val userAdminRoleIds = adminRolesFromUser.map { it.id }.toSet()

            roles.forEach { role ->
                val isAdminRole = role.name.equals("AD", ignoreCase = true) ||
                                  role.name.equals("admin", ignoreCase = true) ||
                                  role.name.equals("administrador", ignoreCase = true)

                // Determinar si se debe mostrar este rol
                val shouldShow = when {
                    // Si el usuario YA tiene este rol admin, mostrarlo deshabilitado
                    isAdminRole && userAdminRoleIds.contains(role.id) -> true
                    // Si es rol admin pero el usuario NO lo tiene, NO mostrarlo
                    isAdminRole && !userAdminRoleIds.contains(role.id) -> false
                    // Cualquier otro rol, mostrarlo normalmente
                    else -> true
                }

                if (shouldShow) {
                    val checkBox = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
                        // Si el usuario tiene este rol admin, mostrarlo deshabilitado
                        val isUserAdminRole = isAdminRole && userAdminRoleIds.contains(role.id)

                        text = if (isUserAdminRole) "${role.name} (No modificable)" else role.name
                        textSize = 16f
                        setPadding(8, 8, 8, 8)
                        isChecked = selectedRoles.contains(role.id)
                        isEnabled = !isUserAdminRole
                        alpha = if (isUserAdminRole) 0.6f else 1.0f

                        // Asegurar que el rol admin del usuario siempre esté seleccionado
                        if (isUserAdminRole && !selectedRoles.contains(role.id)) {
                            selectedRoles.add(role.id)
                        }

                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                selectedRoles.add(role.id)
                            } else {
                                selectedRoles.remove(role.id)
                            }
                            updateSaveButtonState()
                        }
                    }

                    binding.rolesContainer.addView(checkBox)
                }
            }
        }

        viewModel.updateState.observe(this) { state ->
            when (state) {
                is EditUserState.Loading -> {
                    showLoading(true)
                }
                is EditUserState.Success -> {
                    showLoading(false)

                    if (state.newPassword != null) {
                        // Si se cambió la contraseña, mostrar diálogo
                        AlertDialog.Builder(this)
                            .setTitle("Usuario actualizado")
                            .setMessage(
                                """
                                Los datos se actualizaron correctamente.
                                
                                Nueva contraseña: ${state.newPassword}
                                
                                El usuario deberá usar esta nueva contraseña.
                                """.trimIndent()
                            )
                            .setPositiveButton("Copiar contraseña") { _, _ ->
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("password", state.newPassword)
                                clipboard.setPrimaryClip(clip)

                                Toast.makeText(this, "Contraseña copiada", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            }
                            .setNeutralButton("Cerrar") { _, _ ->
                                setResult(RESULT_OK)
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        Snackbar.make(binding.root, "Usuario actualizado exitosamente", Snackbar.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                }
                is EditUserState.Error -> {
                    showLoading(false)

                    val errorMessage = when {
                        state.message.contains("no puede remover su propio rol", ignoreCase = true) -> {
                            "No puede remover su propio rol de administrador"
                        }
                        state.message.contains("roles no encontrados", ignoreCase = true) -> {
                            "Roles inválidos seleccionados"
                        }
                        else -> state.message
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Error al actualizar")
                        .setMessage(errorMessage)
                        .setPositiveButton("Entendido", null)
                        .show()
                }
            }
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Selecciona fecha de nacimiento")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.birthDateEditText.setText(format.format(calendar.time))
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun generateRandomPassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val password = (1..12)
            .map { chars.random() }
            .joinToString("")

        binding.passwordEditText.setText(password)
    }

    private fun validateEmail(): Boolean {
        val email = binding.emailEditText.text.toString()
        return when {
            email.isEmpty() -> {
                binding.emailInputLayout.error = null
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.emailInputLayout.error = "Email inválido"
                false
            }
            else -> {
                binding.emailInputLayout.error = null
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        if (!changePassword) return true

        val password = binding.passwordEditText.text.toString()
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"

        return when {
            password.isEmpty() -> {
                binding.passwordInputLayout.error = "Campo requerido si desea cambiar contraseña"
                false
            }
            password.length < 8 -> {
                binding.passwordInputLayout.error = "Mínimo 8 caracteres"
                false
            }
            !password.matches(Regex(passwordPattern)) -> {
                binding.passwordInputLayout.error = "Debe incluir mayúsculas, números y símbolos"
                false
            }
            else -> {
                binding.passwordInputLayout.error = null
                true
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        binding.apply {
            if (namesEditText.text.isNullOrBlank()) {
                namesInputLayout.error = "Campo requerido"
                isValid = false
            }

            if (documentNumberEditText.text.isNullOrBlank()) {
                documentNumberInputLayout.error = "Campo requerido"
                isValid = false
            }

            if (birthDateEditText.text.isNullOrBlank()) {
                birthDateInputLayout.error = "Campo requerido"
                isValid = false
            }

            if (genderDropdown.text.isNullOrBlank()) {
                genderInputLayout.error = "Campo requerido"
                isValid = false
            }

            if (!validateEmail()) {
                isValid = false
            }

            if (changePassword && !validatePassword()) {
                isValid = false
            }

            if (selectedRoles.isEmpty()) {
                Snackbar.make(root, "Selecciona al menos un rol", Snackbar.LENGTH_SHORT).show()
                isValid = false
            }
        }

        return isValid
    }

    private fun updateSaveButtonState() {
        binding.saveButton.isEnabled =
            !binding.namesEditText.text.isNullOrBlank() &&
                    !binding.documentNumberEditText.text.isNullOrBlank() &&
                    !binding.birthDateEditText.text.isNullOrBlank() &&
                    !binding.genderDropdown.text.isNullOrBlank() &&
                    validateEmail() &&
                    (!changePassword || validatePassword()) &&
                    selectedRoles.isNotEmpty()
    }

    private fun updateUser() {
        val userData = UpdateUserRequest(
            nombresApellidos = binding.namesEditText.text.toString().trim(),
            tipoDocumento = binding.documentTypeDropdown.text.toString(),
            numeroDocumento = binding.documentNumberEditText.text.toString().trim(),
            fechaNacimiento = convertDateFormat(binding.birthDateEditText.text.toString()), // ← "yyyy-MM-dd"
            sexo = when (binding.genderDropdown.text.toString()) {
                "Masculino" -> "M"
                "Femenino" -> "F"
                else -> "O"
            },
            telefono = binding.phoneEditText.text?.toString()?.trim() ?: "",
            correo = binding.emailEditText.text.toString().trim(),
            roleIds = selectedRoles.toList()
        )

        val newPassword = if (changePassword) {
            binding.passwordEditText.text.toString()
        } else null

        viewModel.updateUser(userId, userData, newPassword)
    }
    private fun convertDateFormat(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            date
        }
    }

    private fun formatDate(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            date
        }
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            saveButton.isEnabled = !show
            cancelButton.isEnabled = !show
            if (show) {
                saveButton.text = "Guardando..."
            } else {
                saveButton.text = "Guardar Cambios"
            }
        }
    }
}