package com.example.serious_game_usil.presentation.ui.register

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.ProgressDialog.show
import android.content.Intent
import android.content.res.ColorStateList
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.TokenManager
import com.example.serious_game_usil.databinding.ActivityRegisterBinding
import com.example.serious_game_usil.`interface`.ApiService
import com.example.serious_game_usil.network.RetrofitClient
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale


class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var selectedDate: Calendar? = null
    private val apiService by lazy { RetrofitClient.getApiService() }

    companion object {
        private const val MIN_AGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupValidations()
        setupDropdowns()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.birthDateEditText.setOnClickListener { showDatePicker() }
        binding.termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.registerButton.isEnabled = isChecked && validateAllFields()
        }
        binding.registerButton.setOnClickListener { performRegistration() }

        // Configurar campo de teléfono para solo números y 9 dígitos
        binding.phoneEditText.filters = arrayOf(
            android.text.InputFilter.LengthFilter(9),
            android.text.InputFilter { source, _, _, _, _, _ ->
                if (source.toString().matches(Regex("[0-9]*"))) source else ""
            }
        )
        binding.phoneEditText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    }

    private fun setupDropdowns() {
        val documentTypes = arrayOf("DNI", "CE", "NINGUNO")
        val documentAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, documentTypes)
        binding.documentTypeDropdown.setAdapter(documentAdapter)

        // Configurar el input del número según el tipo de documento
        binding.documentTypeDropdown.doAfterTextChanged { text ->
            when (text.toString()) {
                "DNI" -> {
                    binding.documentNumberEditText.isEnabled = true
                    binding.documentNumberEditText.filters = arrayOf(
                        android.text.InputFilter.LengthFilter(8),
                        android.text.InputFilter { source, _, _, _, _, _ ->
                            if (source.toString().matches(Regex("[0-9]*"))) source else ""
                        }
                    )
                    binding.documentNumberEditText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    binding.documentNumberInputLayout.hint = "Número de DNI (8 dígitos)"
                }
                "CE" -> {
                    binding.documentNumberEditText.isEnabled = true
                    binding.documentNumberEditText.filters = arrayOf(
                        android.text.InputFilter.LengthFilter(12)
                    )
                    binding.documentNumberEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    binding.documentNumberInputLayout.hint = "Número de CE (9-12 caracteres)"
                }
                "NINGUNO" -> {
                    binding.documentNumberEditText.setText("")
                    binding.documentNumberEditText.isEnabled = false
                    binding.documentNumberInputLayout.hint = "No aplica"
                    binding.documentNumberInputLayout.error = null
                }
                else -> {
                    binding.documentNumberEditText.isEnabled = true
                    binding.documentNumberInputLayout.hint = "Número de documento"
                }
            }
        }

        val genderTypes = arrayOf("M", "F")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderTypes)
        binding.genderDropdown.setAdapter(genderAdapter)
    }

    private fun setupValidations() {
        binding.namesEditText.doAfterTextChanged { text ->
            validateName(text.toString())
        }

        binding.documentNumberEditText.doAfterTextChanged { text ->
            val documentType = binding.documentTypeDropdown.text.toString()
            validateDocument(documentType, text.toString())
        }

        binding.documentTypeDropdown.doAfterTextChanged {
            val documentNumber = binding.documentNumberEditText.text.toString()
            validateDocument(it.toString(), documentNumber)

            // Configurar el input según el tipo
            when (it.toString()) {
                "DNI" -> {
                    binding.documentNumberEditText.isEnabled = true
                    binding.documentNumberEditText.filters = arrayOf(
                        android.text.InputFilter.LengthFilter(8),
                        android.text.InputFilter { source, _, _, _, _, _ ->
                            if (source.toString().matches(Regex("[0-9]*"))) source else ""
                        }
                    )
                    binding.documentNumberEditText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    binding.documentNumberInputLayout.hint = "Número de DNI (8 dígitos)"
                }
                "CE" -> {
                    binding.documentNumberEditText.isEnabled = true
                    binding.documentNumberEditText.filters = arrayOf(
                        android.text.InputFilter.LengthFilter(12)
                    )
                    binding.documentNumberEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    binding.documentNumberInputLayout.hint = "Número de CE (9-12 caracteres)"
                }
                "NINGUNO" -> {
                    binding.documentNumberEditText.setText("")
                    binding.documentNumberEditText.isEnabled = false
                    binding.documentNumberInputLayout.hint = "No aplica"
                    binding.documentNumberInputLayout.error = null
                }
            }
        }

        binding.phoneEditText.doAfterTextChanged { text ->
            validatePhone(text.toString())
        }

        binding.emailEditText.doAfterTextChanged { text ->
            validateEmail(text.toString())
        }
    }

    private fun validateName(name: String): Boolean {
        return when {
            name.isEmpty() -> {
                binding.namesInputLayout.error = "Este campo es requerido"
                false
            }
            name.split(" ").size < 2 -> {
                binding.namesInputLayout.error = "Ingresa nombres y apellidos completos"
                false
            }
            !name.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$")) -> {
                binding.namesInputLayout.error = "Solo se permiten letras"
                false
            }
            else -> {
                binding.namesInputLayout.error = null
                true
            }
        }
    }

    private fun validateDocument(type: String, number: String): Boolean {
        return when {
            type.isEmpty() -> {
                binding.documentTypeInputLayout.error = "Selecciona un tipo"
                false
            }
            type == "DNI" -> {
                when {
                    number.isEmpty() -> {
                        binding.documentNumberInputLayout.error = "DNI es requerido"
                        false
                    }
                    number.length != 8 -> {
                        binding.documentNumberInputLayout.error = "DNI debe tener exactamente 8 dígitos"
                        false
                    }
                    !number.all { it.isDigit() } -> {
                        binding.documentNumberInputLayout.error = "DNI solo debe contener números"
                        false
                    }
                    else -> {
                        binding.documentNumberInputLayout.error = null
                        true
                    }
                }
            }
            type == "CE" -> {
                when {
                    number.isEmpty() -> {
                        binding.documentNumberInputLayout.error = "CE es requerido"
                        false
                    }
                    number.length < 9 || number.length > 12 -> {
                        binding.documentNumberInputLayout.error = "CE debe tener entre 9 y 12 caracteres"
                        false
                    }
                    else -> {
                        binding.documentNumberInputLayout.error = null
                        true
                    }
                }
            }
            type == "NINGUNO" -> {
                binding.documentNumberInputLayout.error = null
                true
            }
            else -> {
                binding.documentTypeInputLayout.error = null
                binding.documentNumberInputLayout.error = null
                true
            }
        }
    }

    private fun validatePhone(phone: String): Boolean {
        return when {
            phone.isEmpty() -> {
                binding.phoneInputLayout.error = "Este campo es requerido"
                false
            }
            phone.length != 9 -> {
                binding.phoneInputLayout.error = "Debe tener 9 dígitos"
                false
            }
            !phone.startsWith("9") -> {
                binding.phoneInputLayout.error = "Debe empezar con 9"
                false
            }
            else -> {
                binding.phoneInputLayout.error = null
                true
            }
        }
    }

    private fun validateBirthDate(): Boolean {
        return when {
            selectedDate == null -> {
                binding.birthDateInputLayout.error = "Selecciona tu fecha de nacimiento"
                false
            }
            !isOldEnough() -> {
                binding.birthDateInputLayout.error = "Debes tener al menos $MIN_AGE año"
                false
            }
            else -> {
                binding.birthDateInputLayout.error = null
                true
            }
        }
    }

    private fun validateGender(): Boolean {
        return if (binding.genderDropdown.text.toString().isEmpty()) {
            binding.genderInputLayout.error = "Selecciona tu sexo"
            false
        } else {
            binding.genderInputLayout.error = null
            true
        }
    }

    private fun validateEmail(email: String): Boolean {
        return when {
            email.isEmpty() -> {
                binding.emailInputLayout.error = "Este campo es requerido"
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

    private fun isOldEnough(): Boolean {
        selectedDate?.let { birthDate ->
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            return age >= MIN_AGE
        }
        return false
    }

    private fun showDatePicker() {
        val calendar = selectedDate ?: Calendar.getInstance()

        // Configurar locale en español
        val locale = Locale("es", "ES")
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                updateBirthDateField()
                validateBirthDate()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateBirthDateField() {
        selectedDate?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
            binding.birthDateEditText.setText(dateFormat.format(it.time))
        }
    }

    private fun performRegistration() {
        if (!validateAllFields() || !binding.termsCheckBox.isChecked) {
            if (!binding.termsCheckBox.isChecked) {
                Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show()
            }
            return
        }

        binding.registerButton.isEnabled = false
        binding.registerButton.text = "Registrando..."

        val registerRequest = RegisterRequest(
            nombresApellidos = binding.namesEditText.text.toString().trim(),
            fechaNacimiento = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(selectedDate?.time ?: Date()),
            tipoDocumento = binding.documentTypeDropdown.text.toString(),
            numeroDocumento = if (binding.documentTypeDropdown.text.toString() == "NINGUNO") ""
            else binding.documentNumberEditText.text.toString().trim(),
            sexo = binding.genderDropdown.text.toString(),
            telefono = binding.phoneEditText.text.toString().trim(),
            correo = binding.emailEditText.text.toString().trim(),
            roleIds = listOf(2)
        )

        lifecycleScope.launch {
            try {
                val token = TokenManager.ensureToken()
                if (token == null) {
                    showErrorDialog("No se pudo obtener el token de autenticación")
                    resetButton()
                    return@launch
                }



                val response = apiService.register(registerRequest)
                if (response.isSuccessful) {
                    response.body()?.let { registerResponse ->
                        showSuccessDialog(registerResponse.user.passwordTemporal)
                    } ?: run {
                        showErrorDialog("Respuesta vacía del servidor")
                        resetButton()
                    }
                } else {
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string()

                    val errorMsg = when (errorCode) {
                        400 -> parseError(errorBody)
                        401 -> "No autorizado. Token inválido."
                        403 -> "Acceso denegado."
                        404 -> "Servicio no encontrado."
                        409 -> "El correo ya está registrado."
                        500 -> "Error del servidor."
                        else -> parseError(errorBody)
                    }
                    showErrorDialog(errorMsg)
                    resetButton()
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is java.net.UnknownHostException -> "Sin conexión a internet"
                    is java.net.SocketTimeoutException -> "Tiempo de espera agotado"
                    is java.net.ConnectException -> "No se pudo conectar al servidor"
                    else -> "Error: ${e.localizedMessage}"
                }
                showErrorDialog(errorMsg)
                resetButton()
            }
        }
    }

    private fun parseError(errorBody: String?): String {
        return try {
            val regex = "\"error\":\"(.+?)\"".toRegex()
            regex.find(errorBody ?: "")?.groupValues?.get(1) ?: "Error al registrar"
        } catch (e: Exception) {
            "Error al registrar"
        }
    }

    private fun validateAllFields(): Boolean {
        val isNameValid = validateName(binding.namesEditText.text.toString())
        val documentType = binding.documentTypeDropdown.text.toString()
        val documentNumber = binding.documentNumberEditText.text.toString()
        val isDocumentValid = validateDocument(documentType, documentNumber)
        val isBirthDateValid = validateBirthDate()
        val isGenderValid = validateGender()
        val isPhoneValid = validatePhone(binding.phoneEditText.text.toString())
        val isEmailValid = validateEmail(binding.emailEditText.text.toString())

        return isNameValid && isDocumentValid && isBirthDateValid &&
                isGenderValid && isPhoneValid && isEmailValid
    }

    private fun resetButton() {
        binding.registerButton.isEnabled = true
        binding.registerButton.text = "Registrarse"
    }

    private fun showSuccessDialog(tempPassword: String) {
        AlertDialog.Builder(this)
            .setTitle("¡Registro exitoso!")
            .setMessage("Revisa tu bandeja de entrada.")
            .setPositiveButton("Ir a Login") { _, _ ->
                val intent = Intent(this, LoginActivity::class.java).apply {
                    putExtra("registered_email", binding.emailEditText.text.toString())
                }
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Entendido", null)
            .show()
    }
}