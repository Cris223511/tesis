package com.example.serious_game_usil.presentation.ui.register

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.ProgressDialog.show
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.databinding.ActivityRegisterBinding
import com.example.serious_game_usil.`interface`.ApiService
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
            binding.registerButton.isEnabled = isChecked
        }
        binding.registerButton.setOnClickListener { performRegistration() }
    }

    private fun setupDropdowns() {
        val documentTypes = arrayOf("DNI", "CE", "NINGUNO")
        val documentAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, documentTypes)
        binding.documentTypeDropdown.setAdapter(documentAdapter)

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
            type == "DNI" && number.length != 8 -> {
                binding.documentNumberInputLayout.error = "DNI debe tener 8 dígitos"
                false
            }
            type == "CE" && number.length < 9 -> {
                binding.documentNumberInputLayout.error = "CE debe tener al menos 9 caracteres"
                false
            }
            type != "NINGUNO" && number.isEmpty() -> {
                binding.documentNumberInputLayout.error = "Este campo es requerido"
                false
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
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.birthDateEditText.setText(dateFormat.format(it.time))
        }
    }

    private fun performRegistration() {
        val isValid = validateAllFields()

        if (isValid && binding.termsCheckBox.isChecked) {
            binding.registerButton.isEnabled = false
            binding.registerButton.text = "Registrando..."

            val registerRequest = RegisterRequest(
                nombreApellidos = binding.namesEditText.text.toString(),
                fechaNacimiento = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(selectedDate?.time ?: Date()),
                tipoDocumento = binding.documentTypeDropdown.text.toString(),
                numeroDocumento = binding.documentNumberEditText.text.toString(),
                sexo = binding.genderDropdown.text.toString(),
                celular = binding.phoneEditText.text.toString(),
                correo = binding.emailEditText.text.toString(),
                roleIds = listOf(2)
            )

            lifecycleScope.launch {
                try {
                    val response = apiService.register(registerRequest)
                    if (response.isSuccessful) {
                        response.body()?.let { registerResponse: RegisterResponse ->
                            showSuccessDialog(registerResponse.user.passwordTemporal)
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        showErrorDialog(parseError(errorBody))
                        resetButton()
                    }
                } catch (e: Exception) {
                    showErrorDialog("Error de conexión: ${e.message}")
                    resetButton()
                }
            }
        } else if (!binding.termsCheckBox.isChecked) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show()
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
            .setMessage("Se ha enviado una contraseña temporal a tu correo: $tempPassword\n\nRevisa tu bandeja de entrada.")
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