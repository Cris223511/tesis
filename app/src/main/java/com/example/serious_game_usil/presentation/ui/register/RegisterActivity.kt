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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityRegisterBinding
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import java.util.Calendar
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var selectedDate: Calendar? = null

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MIN_AGE = 13
        private const val PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupValidations()
    }


    private fun setupUI() {
        // Configurar listeners
        binding.backButton.setOnClickListener { finish() }

        binding.birthDateEditText.setOnClickListener { showDatePicker() }

        binding.termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) showTermsDialog()
        }

        binding.registerButton.setOnClickListener { performRegistration() }
    }

    private fun setupValidations() {
        // Validación en tiempo real para nombres
        binding.namesEditText.doAfterTextChanged { text ->
            validateName(text.toString())
        }

        // Validación en tiempo real para usuario
        binding.usernameEditText.doAfterTextChanged { text ->
            validateUsername(text.toString())
        }

        // Validación en tiempo real para teléfono
        binding.phoneEditText.doAfterTextChanged { text ->
            validatePhone(text.toString())
        }

        // Validación en tiempo real para email
        binding.emailEditText.doAfterTextChanged { text ->
            validateEmail(text.toString())
        }

        // Validación en tiempo real para contraseña
        binding.passwordEditText.doAfterTextChanged { text ->
            validatePassword(text.toString())
            // También validar confirmación si ya hay texto
            if (binding.confirmPasswordEditText.text?.isNotEmpty() == true) {
                validatePasswordMatch()
            }
        }

        // Validación en tiempo real para confirmar contraseña
        binding.confirmPasswordEditText.doAfterTextChanged {
            validatePasswordMatch()
        }
    }

    private fun validateName(name: String): Boolean {
        return when {
            name.isEmpty() -> {
                binding.namesInputLayout.error = "Este campo es requerido"
                false
            }
            name.length < 3 -> {
                binding.namesInputLayout.error = "Ingresa al menos 3 caracteres"
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

    private fun validateUsername(username: String): Boolean {
        return when {
            username.isEmpty() -> {
                binding.usernameInputLayout.error = "Este campo es requerido"
                false
            }
            username.length < 4 -> {
                binding.usernameInputLayout.error = "Mínimo 4 caracteres"
                false
            }
            !username.matches(Regex("^[a-zA-Z0-9._]+$")) -> {
                binding.usernameInputLayout.error = "Solo letras, números, . y _"
                false
            }
            else -> {
                binding.usernameInputLayout.error = null
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
            phone.length < 9 -> {
                binding.phoneInputLayout.error = "Número inválido"
                false
            }
            !phone.matches(Regex("^[0-9]+$")) -> {
                binding.phoneInputLayout.error = "Solo números"
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
                binding.birthDateInputLayout.error = "Debes tener al menos $MIN_AGE años"
                false
            }
            else -> {
                binding.birthDateInputLayout.error = null
                true
            }
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

    private fun validatePassword(password: String): Boolean {
        return when {
            password.isEmpty() -> {
                binding.passwordInputLayout.error = "Este campo es requerido"
                false
            }
            password.length < MIN_PASSWORD_LENGTH -> {
                binding.passwordInputLayout.error = "Mínimo 8 caracteres"
                false
            }
            !password.matches(Regex(PASSWORD_PATTERN)) -> {
                binding.passwordInputLayout.error = "Debe contener mayúsculas, minúsculas, números y caracteres especiales"
                false
            }
            else -> {
                binding.passwordInputLayout.error = null
                true
            }
        }
    }

    private fun validatePasswordMatch(): Boolean {
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        return when {
            confirmPassword.isEmpty() -> {
                binding.confirmPasswordInputLayout.error = "Este campo es requerido"
                false
            }
            password != confirmPassword -> {
                binding.confirmPasswordInputLayout.error = "Las contraseñas no coinciden"
                false
            }
            else -> {
                binding.confirmPasswordInputLayout.error = null
                true
            }
        }
    }

    private fun validateTerms(): Boolean {
        return if (!binding.termsCheckBox.isChecked) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show()
            false
        } else {
            true
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
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Configurar locale español
        val locale = Locale("es", "ES")
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        createConfigurationContext(config)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                selectedDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }
                updateBirthDateField()
                validateBirthDate()
            },
            year,
            month,
            day
        )

        // Establecer fecha máxima (hoy)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

        // Establecer fecha mínima (100 años atrás)
        val minDate = Calendar.getInstance().apply {
            add(Calendar.YEAR, -100)
        }
        datePickerDialog.datePicker.minDate = minDate.timeInMillis

        datePickerDialog.show()
    }

    private fun updateBirthDateField() {
        selectedDate?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
            binding.birthDateEditText.setText(dateFormat.format(it.time))
        }
    }

    private fun showTermsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Términos y Condiciones")
            .setMessage("""
                1. Uso del Servicio
                Al registrarte, aceptas usar Serious Game de manera responsable y de acuerdo con todas las leyes aplicables.
                
                2. Privacidad
                Respetamos tu privacidad. Tus datos personales serán tratados conforme a nuestra Política de Privacidad.
                
                3. Contenido del Usuario
                Eres responsable del contenido que compartas en la plataforma.
                
                4. Seguridad
                Debes mantener tu contraseña segura y no compartirla con terceros.
                
                5. Edad Mínima
                Debes tener al menos 5 años para usar este servicio.
                
                6. Modificaciones
                Nos reservamos el derecho de modificar estos términos en cualquier momento.
                
                7. Terminación
                Podemos suspender o terminar tu cuenta si violas estos términos.
                
                Al hacer clic en "Aceptar", confirmas que has leído y aceptas estos términos y condiciones.
            """.trimIndent())
            .setPositiveButton("Aceptar") { dialog, _ ->
                binding.termsCheckBox.isChecked = true
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                binding.termsCheckBox.isChecked = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }


    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_succes, null))
            .setCancelable(false)
            .create()
            .apply {
                show()
                window?.setBackgroundDrawableResource(android.R.color.transparent)

                // Auto cerrar después de 2 segundos y navegar
                Handler(Looper.getMainLooper()).postDelayed({
                    dismiss()
                    val intent = Intent(this@RegisterActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("registered_email", binding.emailEditText.text.toString())
                    }
                    startActivity(intent)
                    finish()
                }, 2000)
            }
    }

    private fun showErrorDialog(message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_error, null)
        dialogView.findViewById<TextView>(R.id.errorMessage).text = message

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Entendido", null)
            .create()
            .apply {
                show()
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    private fun performRegistration() {
        // Validar todos los campos
        val isNameValid = validateName(binding.namesEditText.text.toString())
        val isUsernameValid = validateUsername(binding.usernameEditText.text.toString())
        val isPhoneValid = validatePhone(binding.phoneEditText.text.toString())
        val isBirthDateValid = validateBirthDate()
        val isEmailValid = validateEmail(binding.emailEditText.text.toString())
        val isPasswordValid = validatePassword(binding.passwordEditText.text.toString())
        val isPasswordMatchValid = validatePasswordMatch()
        val areTermsAccepted = validateTerms()

        if (isNameValid && isUsernameValid && isPhoneValid && isBirthDateValid &&
            isEmailValid && isPasswordValid && isPasswordMatchValid && areTermsAccepted) {

            // Deshabilitar botón durante el proceso
            binding.registerButton.isEnabled = false
            binding.registerButton.text = "Registrando..."

            // Simular registro
            binding.root.postDelayed({
                showSuccessDialog()

                // Navegar al login
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("registered_email", binding.emailEditText.text.toString())
                }
                startActivity(intent)
                finish()
            }, 2000)
        } else {
            // Hacer scroll al primer error
            when {
                !isNameValid -> binding.namesEditText.requestFocus()
                !isUsernameValid -> binding.usernameEditText.requestFocus()
                !isPhoneValid -> binding.phoneEditText.requestFocus()
                !isBirthDateValid -> binding.birthDateEditText.performClick()
                !isEmailValid -> binding.emailEditText.requestFocus()
                !isPasswordValid -> binding.passwordEditText.requestFocus()
                !isPasswordMatchValid -> binding.confirmPasswordEditText.requestFocus()
            }
        }
    }


}