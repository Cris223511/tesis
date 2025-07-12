package com.example.serious_game_usil.presentation.ui.recuperation

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity


import androidx.core.widget.doAfterTextChanged
import com.example.serious_game_usil.databinding.ActivityForgotPasswordBinding
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupValidation()
    }

    private fun setupUI() {
        binding.sendButton.setOnClickListener {
            if (validateEmail()) {
                sendRecoveryCode()
            }
        }
    }

    private fun setupValidation() {
        binding.emailEditText.doAfterTextChanged { text ->
            validateEmailRealTime(text.toString())
        }
    }

    private fun validateEmailRealTime(email: String) {
        when {
            email.isEmpty() -> binding.emailInputLayout.error = null
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.emailInputLayout.error = "Email inválido"
            }
            else -> binding.emailInputLayout.error = null
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.emailEditText.text.toString().trim()
        return when {
            email.isEmpty() -> {
                binding.emailInputLayout.error = "Ingresa tu correo electrónico"
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

    private fun sendRecoveryCode() {
        binding.sendButton.isEnabled = false
        binding.sendButton.text = "Enviando..."

        // Simular envío
        binding.root.postDelayed({
            val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                putExtra("email", binding.emailEditText.text.toString())
            }
            startActivity(intent)
            binding.sendButton.isEnabled = true
            binding.sendButton.text = "Enviar"
        }, 1500)
    }
}