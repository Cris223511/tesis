package com.example.serious_game_usil.presentation.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.presentation.ui.main.MainActivity
import com.example.serious_game_usil.presentation.ui.recuperation.ForgotPasswordActivity
import com.example.serious_game_usil.presentation.ui.register.RegisterActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    // Views
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var passwordToggle: ImageView
    private lateinit var loginButton: MaterialButton
    private lateinit var forgotPasswordText: TextView
    private lateinit var registerText: TextView

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupUI()
    }

    private fun initViews() {
        // Inicializar todas las vistas
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        passwordToggle = findViewById(R.id.passwordToggle)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        registerText = findViewById(R.id.registerText)
    }

    private fun setupUI() {
        // Configurar listeners
        loginButton.setOnClickListener {
            performLogin()
        }

        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        forgotPasswordText.setOnClickListener {
            handleForgotPassword()
        }

        registerText.setOnClickListener {
            handleRegister()
        }
    }

    private fun performLogin() {
        val username = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Validaciones
        when {
            username.isEmpty() -> {
                emailEditText.error = "Ingresa tu usuario"
                emailEditText.requestFocus()
                return
            }
            password.isEmpty() -> {
                passwordEditText.error = "Ingresa tu contraseña"
                passwordEditText.requestFocus()
                return
            }
        }

        // Deshabilitar botón durante el proceso
        loginButton.isEnabled = false
        loginButton.text = "Iniciando sesión..."

        // Simular proceso de login
        lifecycleScope.launch {
            try {
                // Aquí iría la lógica real de autenticación
                delay(2000) // Simular llamada a servidor

                // Login exitoso
                navigateToMain()

            } catch (e: Exception) {
                // Error en login
                Toast.makeText(
                    this@LoginActivity,
                    "Error al iniciar sesión",
                    Toast.LENGTH_SHORT
                ).show()

                loginButton.isEnabled = true
                loginButton.text = "Iniciar sesión"
            }
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        if (isPasswordVisible) {
            // Mostrar contraseña
            passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            // Ocultar contraseña
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_eye_closed)
        }

        // Mantener el cursor al final del texto
        passwordEditText.setSelection(passwordEditText.text?.length ?: 0)
    }

    private fun handleForgotPassword() {
        startActivity(Intent(this, ForgotPasswordActivity::class.java))
    }

    private fun handleRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}