package com.example.serious_game_usil.presentation.ui.administrador.list

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.repository.UserRepository
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.databinding.ActivityAdminAddUserBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class CreateUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAddUserBinding
    private lateinit var viewModel: CreateUserViewModel

    private val documentTypes = listOf("DNI", "CE", "Pasaporte")
    private val genders = listOf("Masculino", "Femenino", "Otro")
    private var selectedRoles = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAddUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
        setupDropdowns()
        setupValidation()
        observeViewModel()

        viewModel.loadRoles()
    }

    private fun setupViewModel() {
        val repository = UserRepository.getInstance(this)
        val factory = CreateUserViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[CreateUserViewModel::class.java]
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

        binding.cancelButton.setOnClickListener {
            finish()
        }

        binding.registerButton.setOnClickListener {
            if (validateForm()) {
                createUser()
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
                updateRegisterButtonState()
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
        }
    }

    private fun observeViewModel() {
        viewModel.rolesState.observe(this) { roles ->
            binding.rolesContainer.removeAllViews()

            roles.forEach { role ->
                val checkBox = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
                    text = role.name
                    textSize = 16f
                    setPadding(8, 8, 8, 8)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedRoles.add(role.id)
                        } else {
                            selectedRoles.remove(role.id)
                        }
                        updateRegisterButtonState()
                    }
                }

                binding.rolesContainer.addView(checkBox)
            }
        }

        viewModel.createUserState.observe(this) { state ->
            when (state) {
                is CreateUserState.Loading -> {
                    showLoading(true)
                }
                is CreateUserState.Success -> {
                    showLoading(false)

                    // Mostrar diálogo con la contraseña temporal
                    AlertDialog.Builder(this)
                        .setTitle("Usuario creado exitosamente")
                        .setMessage(
                            """
                            Usuario creado correctamente.
                            
                            Contraseña temporal: ${state.temporalPassword}
                            
                            El usuario deberá cambiar esta contraseña en su primer inicio de sesión.
                            """.trimIndent()
                        )
                        .setPositiveButton("Copiar contraseña") { _, _ ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("password", state.temporalPassword)
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(this, "Contraseña copiada al portapapeles", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setNeutralButton("Cerrar") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
                is CreateUserState.Error -> {
                    showLoading(false)
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
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

            if (selectedRoles.isEmpty()) {
                Snackbar.make(root, "Selecciona al menos un rol", Snackbar.LENGTH_SHORT).show()
                isValid = false
            }
        }

        return isValid
    }

    private fun updateRegisterButtonState() {
        binding.registerButton.isEnabled =
            !binding.namesEditText.text.isNullOrBlank() &&
                    !binding.documentNumberEditText.text.isNullOrBlank() &&
                    !binding.birthDateEditText.text.isNullOrBlank() &&
                    !binding.genderDropdown.text.isNullOrBlank() &&
                    validateEmail() &&
                    selectedRoles.isNotEmpty()
    }

    private fun createUser() {
        val userData = RegisterRequest(
            nombresApellidos = binding.namesEditText.text.toString().trim(),
            tipoDocumento = binding.documentTypeDropdown.text.toString(),
            numeroDocumento = binding.documentNumberEditText.text.toString().trim(),
            fechaNacimiento = convertDateFormat(binding.birthDateEditText.text.toString()),
            sexo = when (binding.genderDropdown.text.toString()) {
                "Masculino" -> "M"
                "Femenino" -> "F"
                else -> "O"
            },
            telefono = binding.phoneEditText.text?.toString()?.trim() ?: "",
            correo = binding.emailEditText.text.toString().trim(),
            roleIds = selectedRoles.toList()
        )

        viewModel.createUser(userData)
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

    private fun showLoading(show: Boolean) {
        binding.apply {
            registerButton.isEnabled = !show
            cancelButton.isEnabled = !show
            if (show) {
                registerButton.text = "Registrando..."
            } else {
                registerButton.text = "Registrar Usuario"
            }
        }
    }
}