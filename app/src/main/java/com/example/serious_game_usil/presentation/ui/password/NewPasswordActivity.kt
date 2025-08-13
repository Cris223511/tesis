package com.example.serious_game_usil.presentation.ui.password

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.serious_game_usil.R
import com.example.serious_game_usil.databinding.ActivityNewPasswordBinding
import com.example.serious_game_usil.presentation.ui.login.LoginActivity
import com.example.serious_game_usil.repository.UserRepository

class NewPasswordActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityNewPasswordBinding
    private lateinit var viewModel: PasswordChangeViewModel
    
    private var email: String = ""
    private var otpCode: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel = PasswordChangeViewModelFactory(UserRepository.getInstance(this))
            .create(PasswordChangeViewModel::class.java)
        
        email = intent.getStringExtra("email") ?: ""
        otpCode = intent.getStringExtra("otp_code") ?: ""
        if (email.isEmpty() || otpCode.isEmpty()) {
            finish()
            return
        }
        
        setupUI()
        setupObservers()
    }
    
    private fun setupUI() {
        binding.toolBar.setNavigationOnClickListener { finish() }
        
        binding.passwordToggle.setOnClickListener {
            togglePasswordVisibility(binding.passwordEditText, binding.passwordToggle)
        }
        
        binding.confirmPasswordToggle.setOnClickListener {
            togglePasswordVisibility(binding.confirmPasswordEditText, binding.confirmPasswordToggle)
        }
        
        binding.changePasswordButton.setOnClickListener {
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()
            
            if (validatePasswords(password, confirmPassword)) {
                viewModel.changePasswordWithOTP(email, otpCode, password, confirmPassword)
            }
        }
    }
    
    private fun setupObservers() {
        viewModel.isLoading.observe(this, Observer { isLoading ->
            binding.changePasswordButton.isEnabled = !isLoading
            binding.changePasswordButton.text = if (isLoading) "Cambiando..." else "Cambiar Contraseña"
            binding.passwordEditText.isEnabled = !isLoading
            binding.confirmPasswordEditText.isEnabled = !isLoading
        })
        
        viewModel.passwordChanged.observe(this, Observer { changed ->
            if (changed) {
                val intent = Intent(this, PasswordResetSuccessActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        })
        
        viewModel.error.observe(this, Observer { error ->
            error?.let {
                when {
                    it.contains("no coinciden", ignoreCase = true) -> {
                        binding.confirmPasswordInputLayout.error = "Las contraseñas no coinciden"
                    }
                    it.contains("últimas 5", ignoreCase = true) -> {
                        binding.passwordInputLayout.error = "No puedes usar una de tus últimas 5 contraseñas"
                    }
                    it.contains("caracteres", ignoreCase = true) -> {
                        binding.passwordInputLayout.error = "La contraseña debe cumplir con los requisitos de seguridad"
                    }
                    else -> {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun validatePasswords(password: String, confirmPassword: String): Boolean {
        binding.passwordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null
        
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Ingresa tu nueva contraseña"
            return false
        }
        
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Confirma tu contraseña"
            return false
        }
        
        if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Las contraseñas no coinciden"
            return false
        }
        
        if (!isValidPassword(password)) {
            binding.passwordInputLayout.error = "La contraseña debe tener al menos 8 caracteres, incluir mayúsculas, minúsculas, números y caracteres especiales"
            return false
        }
        
        return true
    }
    
    private fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { "!@#\$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
        
        return hasUpper && hasLower && hasDigit && hasSpecial
    }
    
    private fun togglePasswordVisibility(editText: com.google.android.material.textfield.TextInputEditText, 
                                       toggleButton: com.google.android.material.button.MaterialButton) {
        if (editText.transformationMethod == null) {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
            toggleButton.setIconResource(R.drawable.ic_visibility)
        } else {
            editText.transformationMethod = null
            toggleButton.setIconResource(R.drawable.ic_visibility_off)
        }
        editText.setSelection(editText.text?.length ?: 0)
    }
}