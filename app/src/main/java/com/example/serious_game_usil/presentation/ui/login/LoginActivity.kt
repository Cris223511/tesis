package com.example.serious_game_usil.presentation.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.R
import com.example.serious_game_usil.data.LoginRequest
import com.example.serious_game_usil.presentation.ui.main.MainActivity
import com.example.serious_game_usil.presentation.ui.recuperation.ForgotPasswordActivity
import com.example.serious_game_usil.presentation.ui.recuperation.OtpVerificationActivity
import com.example.serious_game_usil.presentation.ui.register.RegisterActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch




class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var passwordToggle: ImageView
    private lateinit var loginButton: MaterialButton
    private lateinit var registerText: TextView
    private lateinit var rememberMeCheckBox: MaterialCheckBox

    private var isPasswordVisible = false
    private var loginAttempts = 0
    private val MAX_LOGIN_ATTEMPTS = 3

    companion object {
        private const val PREFS_NAME = "LoginPrefs"
        private const val KEY_LOGIN_ATTEMPTS = "login_attempts"
        private const val KEY_LAST_ATTEMPT_TIME = "last_attempt_time"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SAVED_USERNAME = "saved_username"
        private const val BLOCK_DURATION = 30 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupUI()
        loadSavedUsername()
        checkIntentExtras()
        checkLoginAttempts()
    }

    private fun initViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        passwordToggle = findViewById(R.id.passwordToggle)
        loginButton = findViewById(R.id.loginButton)
        registerText = findViewById(R.id.registerText)
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox)
    }

    private fun setupUI() {
        loginButton.setOnClickListener {
            performLogin()
        }

        passwordToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        registerText.setOnClickListener {
            handleRegister()
        }
    }

    private fun loadSavedUsername() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)

        if (rememberMe) {
            val savedUsername = prefs.getString(KEY_SAVED_USERNAME, "")
            if (!savedUsername.isNullOrEmpty()) {
                emailEditText.setText(savedUsername)
                rememberMeCheckBox.isChecked = true
                passwordEditText.requestFocus()
            }
        }
    }

    private fun saveUsername(username: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_REMEMBER_ME, rememberMeCheckBox.isChecked)
            if (rememberMeCheckBox.isChecked) {
                putString(KEY_SAVED_USERNAME, username)
            } else {
                remove(KEY_SAVED_USERNAME)
            }
            apply()
        }
    }

    private fun checkIntentExtras() {
        intent.getStringExtra("registered_email")?.let {
            emailEditText.setText(it)
        }
    }

    private fun checkLoginAttempts() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loginAttempts = prefs.getInt(KEY_LOGIN_ATTEMPTS, 0)
        val lastAttemptTime = prefs.getLong(KEY_LAST_ATTEMPT_TIME, 0)

        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            val timePassed = currentTime - lastAttemptTime

            if (timePassed < BLOCK_DURATION) {
                val remainingTime = (BLOCK_DURATION - timePassed) / 1000 / 60
                showErrorDialog(
                    "Cuenta bloqueada temporalmente",
                    "Has excedido el número de intentos. Intenta nuevamente en $remainingTime minutos.",
                    ErrorType.ACCOUNT_BLOCKED
                )
                loginButton.isEnabled = false
            } else {
                resetLoginAttempts()
            }
        }
    }

    private fun performLogin() {
        val usuario = emailEditText.text.toString().trim()
        val contrasena = passwordEditText.text.toString().trim()

        when {
            usuario.isEmpty() -> {
                emailEditText.error = "Ingresa su usuario"
                emailEditText.requestFocus()
                return
            }
            contrasena.isEmpty() -> {
                passwordEditText.error = "Ingrese su contraseña"
                passwordEditText.requestFocus()
                return
            }
        }

        saveUsername(usuario)

        loginButton.isEnabled = false
        loginButton.text = "Iniciando sesión..."

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiServiceNoAuth()
                val response = apiService.login(
                    LoginRequest(
                        usuario = usuario,
                        contrasena = contrasena
                    )
                )

                if (response.isSuccessful) {
                    response.body()?.let { loginResponse ->
                        Log.d("LOGIN", "Respuesta completa: $loginResponse")
                        Log.d("LOGIN", "User ID recibido: ${loginResponse.user.userId}")
                        Log.d("LOGIN", "User object: ${loginResponse.user}")

                        val prefs = getSharedPreferences("TempUserData", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putInt("user_id", loginResponse.user.userId)
                            putString("email", loginResponse.user.correo)
                            putString("nombres", loginResponse.user.nombresApellidos)
                            putString("protected_route", "/dashboard")
                            apply()
                        }

                        navigateToOTP(
                            userId = loginResponse.user.userId,
                            correo = loginResponse.user.correo
                        )
                    }
                } else {
                    when (response.code()) {
                        401 -> handleLoginError(ErrorType.INCORRECT_PASSWORD)
                        403 -> handleLoginError(ErrorType.ACCOUNT_DISABLED)
                        429 -> handleLoginError(ErrorType.TOO_MANY_ATTEMPTS)
                        else -> handleLoginError(ErrorType.SERVER_ERROR)
                    }
                }
            } catch (e: Exception) {
                handleLoginError(ErrorType.SERVER_ERROR)
            }
        }
    }

    private fun navigateToOTP(userId: Int, correo: String) {
        startActivity(Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("user_id", userId)
            putExtra("email", correo)
        })

        loginButton.apply {
            isEnabled = true
            text = "Iniciar sesión"
        }
    }

    private fun handleLoginError(errorType: ErrorType) {
        loginAttempts++
        saveLoginAttempt()

        loginButton.isEnabled = true
        loginButton.text = "Iniciar sesión"

        when (errorType) {
            ErrorType.USER_NOT_FOUND -> {
                showErrorDialog(
                    "Usuario no encontrado",
                    "El usuario ingresado no existe en nuestro sistema.",
                    errorType
                )
            }
            ErrorType.INCORRECT_PASSWORD -> {
                val remainingAttempts = MAX_LOGIN_ATTEMPTS - loginAttempts
                if (remainingAttempts > 0) {
                    showErrorDialog(
                        "Contraseña incorrecta",
                        "Contraseña y/o usuario incorrectos.\nIntentos restantes: $remainingAttempts",
                        errorType
                    )
                } else {
                    showErrorDialog(
                        "Cuenta bloqueada temporalmente",
                        "Has excedido el número de intentos permitidos. Tu cuenta ha sido bloqueada por 30 minutos.",
                        ErrorType.ACCOUNT_BLOCKED
                    )
                    loginButton.isEnabled = false
                }
            }
            ErrorType.ACCOUNT_DISABLED -> {
                showErrorDialog(
                    "Cuenta inhabilitada",
                    "Su cuenta está inhabilitada, comuníquese con el administrador.",
                    errorType
                )
            }
            ErrorType.TOO_MANY_ATTEMPTS -> {
                showErrorDialog(
                    "Demasiados intentos",
                    "Has realizado demasiados intentos. Intenta más tarde.",
                    errorType
                )
            }
            ErrorType.SERVER_ERROR -> {
                showErrorDialog(
                    "Error de conexión",
                    "No se pudo conectar con el servidor. Verifica tu conexión a internet.",
                    errorType
                )
            }
            ErrorType.ACCOUNT_BLOCKED -> {
            }
        }
    }

    private fun showErrorDialog(title: String, message: String, errorType: ErrorType) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login_error, null)

        val errorTitle = dialogView.findViewById<TextView>(R.id.errorTitle)
        val errorMessage = dialogView.findViewById<TextView>(R.id.errorMessage)
        val errorIcon = dialogView.findViewById<ImageView>(R.id.errorIcon)
        val acceptButton = dialogView.findViewById<MaterialButton>(R.id.acceptButton)

        errorTitle.text = title
        errorMessage.text = message

        when (errorType) {
            ErrorType.ACCOUNT_BLOCKED -> errorIcon.setImageResource(R.drawable.ic_lock)
            ErrorType.ACCOUNT_DISABLED -> errorIcon.setImageResource(R.drawable.ic_block)
            else -> errorIcon.setImageResource(R.drawable.ic_sad_face)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        acceptButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun saveLoginAttempt() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_LOGIN_ATTEMPTS, loginAttempts)
            putLong(KEY_LAST_ATTEMPT_TIME, System.currentTimeMillis())
            apply()
        }
    }

    private fun resetLoginAttempts() {
        loginAttempts = 0
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_LOGIN_ATTEMPTS, 0)
            putLong(KEY_LAST_ATTEMPT_TIME, 0)
            apply()
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        if (isPasswordVisible) {
            passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordToggle.setImageResource(R.drawable.ic_eye_closed)
        }

        passwordEditText.setSelection(passwordEditText.text?.length ?: 0)
    }

    private fun handleRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    enum class ErrorType {
        USER_NOT_FOUND,
        INCORRECT_PASSWORD,
        ACCOUNT_DISABLED,
        ACCOUNT_BLOCKED,
        TOO_MANY_ATTEMPTS,
        SERVER_ERROR
    }
}