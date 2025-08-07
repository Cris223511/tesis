package com.example.serious_game_usil.presentation.ui.password


import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.serious_game_usil.databinding.ActivityPasswordResetSuccessBinding
import com.example.serious_game_usil.databinding.ActivityResetPasswordBinding


class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var isAdminReset = false
    private var userId: Int = 0
    private var userEmail: String? = null
    private var userName: String? = null

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener datos del intent
        isAdminReset = intent.getBooleanExtra("isAdminReset", false)
        userId = intent.getIntExtra("userId", 0)
        userEmail = intent.getStringExtra("userEmail")
        userName = intent.getStringExtra("userName")

        setupUI()
        setupValidations()
    }

    private fun setupUI() {
        // Personalizar UI según el contexto
        if (isAdminReset && userName != null) {
            supportActionBar?.title = "Cambiar contraseña"
            supportActionBar?.subtitle = "Usuario: $userName"

            // Agregar información visual sobre qué usuario se está modificando
            binding.userInfoCard?.visibility = View.VISIBLE
            binding.userNameText?.text = userName
            binding.userEmailText?.text = userEmail
        }

        binding.resetButton.setOnClickListener {
            if (validatePasswords()) {
                resetPassword()
            }
        }
    }

    private fun setupValidations() {
        binding.passwordEditText.doAfterTextChanged { text ->
            validatePassword(text.toString())
            if (binding.confirmPasswordEditText.text?.isNotEmpty() == true) {
                validatePasswordMatch()
            }
        }

        binding.confirmPasswordEditText.doAfterTextChanged {
            validatePasswordMatch()
        }
    }

    private fun validatePassword(password: String): Boolean {
        return when {
            password.isEmpty() -> {
                binding.passwordInputLayout.error = null
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
                binding.confirmPasswordInputLayout.error = null
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

    private fun validatePasswords(): Boolean {
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        val isPasswordValid = validatePassword(password)
        val isPasswordMatchValid = validatePasswordMatch()

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Este campo es requerido"
        }
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Este campo es requerido"
        }

        return isPasswordValid && isPasswordMatchValid && password.isNotEmpty() && confirmPassword.isNotEmpty()
    }

    private fun resetPassword() {
        binding.resetButton.isEnabled = false
        binding.resetButton.text = "Actualizando..."

        val newPassword = binding.passwordEditText.text.toString()

        // TODO: Implementar llamada al API para cambiar contraseña
        // Si es admin reset, usar userId
        // Si no, usar el token del usuario actual

        binding.root.postDelayed({
            val successMessage = if (isAdminReset) {
                "Contraseña actualizada para $userName"
            } else {
                "Contraseña actualizada exitosamente"
            }

            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()

            if (isAdminReset) {
                // Si es admin, volver a la lista
                finish()
            } else {
                // Si es usuario normal, ir a success
                startActivity(Intent(this, ActivityPasswordResetSuccessBinding::class.java))
                finish()
            }
        }, 1500)
    }
}