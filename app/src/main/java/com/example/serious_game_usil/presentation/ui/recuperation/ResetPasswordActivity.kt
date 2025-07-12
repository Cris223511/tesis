package com.example.serious_game_usil.presentation.ui.recuperation


import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.serious_game_usil.databinding.ActivityResetPasswordBinding

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupValidations()
    }

    private fun setupUI() {
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
                binding.confirmPasswordInputLayout.error = "Las credenciales no coinciden"
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

        binding.root.postDelayed({
            Toast.makeText(this, "Credenciales actualizada exitosamente", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PasswordResetSuccessActivity::class.java))
            finish()
        }, 1500)
    }
}