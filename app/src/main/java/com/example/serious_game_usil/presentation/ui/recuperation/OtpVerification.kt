package com.example.serious_game_usil.presentation.ui.recuperation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.data.OTPRequest
import com.example.serious_game_usil.data.ResendOTPRequest
import com.example.serious_game_usil.databinding.ActivityOtpVerificationBinding
import com.example.serious_game_usil.guards.AuthManager
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var otpFields: List<EditText>
    private var userId: Int = 0
    private var userEmail: String = ""
    private var resendAttempts = 0
    private var countDownTimer: CountDownTimer? = null
    private var lastResendTime: Long = 0

    companion object {
        private const val PREFS_NAME = "OtpPrefs"
        private const val KEY_RESEND_ATTEMPTS = "resend_attempts_"
        private const val KEY_BLOCK_TIME = "block_time_"
        private const val KEY_LAST_RESEND = "last_resend_"
        private const val MAX_RESEND_ATTEMPTS = 3
        private const val BLOCK_DURATION = 24 * 60 * 60 * 1000L // 24 horas
        private const val RESEND_COOLDOWN = 60 * 1000L // 1 minuto
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("user_id", 0)
        userEmail = intent.getStringExtra("email") ?: ""

        checkResendStatus()
        setupUI()
        setupOtpFields()
        checkAndStartTimer()
    }

    private fun setupUI() {
        binding.emailText.text = userEmail

        binding.verifyButton.setOnClickListener {
            verifyOtp()
        }

        binding.resendText.setOnClickListener {
            if (canResendCode()) {
                resendCode()
            }
        }
    }

    private fun setupOtpFields() {
        otpFields = listOf(
            binding.otp1,
            binding.otp2,
            binding.otp3,
            binding.otp4,
            binding.otp5,
            binding.otp6
        )

        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.let { text ->
                        if (text.isNotEmpty()) {
                            // Convertir a mayúsculas
                            if (text != text.uppercase()) {
                                editText.setText(text.uppercase())
                                editText.setSelection(text.length)
                            }
                            // Mover al siguiente campo
                            if (index < otpFields.size - 1) {
                                otpFields[index + 1].requestFocus()
                            }
                        }
                    }
                }
            })

            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        otpFields[index - 1].requestFocus()
                        otpFields[index - 1].setText("")
                    }
                }
                false
            }
        }
    }

    private fun checkAndStartTimer() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastResendTime = prefs.getLong(KEY_LAST_RESEND + userEmail, 0)

        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastResendTime

        if (timePassed < RESEND_COOLDOWN && lastResendTime > 0) {
            val remainingTime = RESEND_COOLDOWN - timePassed
            startCountDownTimer(remainingTime)
        }
    }

    private fun startCountDownTimer(millisUntilFinished: Long) {
        countDownTimer?.cancel()

        binding.resendText.isEnabled = false
        binding.resendText.alpha = 0.5f
        binding.timerText.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(millisUntilFinished, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60

                binding.timerText.text = String.format("%02d:%02d", minutes, remainingSeconds)
                binding.resendText.text = "Reenviar código"
            }

            override fun onFinish() {
                binding.timerText.visibility = View.GONE
                binding.resendText.isEnabled = true
                binding.resendText.alpha = 1.0f
                binding.resendText.text = "Reenviar código"
            }
        }.start()
    }

    private fun getOtpCode(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }

    private fun verifyOtp() {
        val otp = getOtpCode()

        when {
            otp.length < 6 -> {
                Toast.makeText(this, "Ingresa el código completo", Toast.LENGTH_SHORT).show()
                return
            }
        }

        binding.verifyButton.isEnabled = false
        binding.verifyButton.text = "Verificando..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apiService = RetrofitClient.getApiService()

                val otpResponse = withContext(Dispatchers.IO) {
                    apiService.verifyOtp(OTPRequest(userId, otp))
                }

                if (otpResponse.isSuccessful) {
                    otpResponse.body()?.let { otpData ->
                        val tempPrefs = getSharedPreferences("TempUserData", Context.MODE_PRIVATE)
                        val protectedRoute = tempPrefs.getString("protected_route", "/dashboard") ?: "/dashboard"

                        val userDetailResponse = withContext(Dispatchers.IO) {
                            apiService.getUserDetail(otpData.userId)
                        }

                        if (userDetailResponse.isSuccessful) {
                            userDetailResponse.body()?.let { userDetail ->
                                AuthManager.saveAuthData(
                                    accessToken = otpData.bearerToken,
                                    refreshToken = otpData.refreshToken,
                                    userId = otpData.userId,
                                    nombresApellidos = userDetail.nombresApellidos,
                                    correo = userDetail.correo,
                                    telefono = userDetail.telefono,
                                    tipoDocumento = userDetail.tipoDocumento,
                                    numDocumento = userDetail.numeroDocumento,
                                    sexo = userDetail.sexo,
                                    foto = null,
                                    roles = otpData.roles,
                                    roleIds = userDetail.roles.map { it.id },
                                    protectedRoute = protectedRoute
                                )

                                tempPrefs.edit().clear().apply()
                                resetResendAttempts()

                                RouteNavigator.navigateToUserHome(this@OtpVerificationActivity)
                            }
                        } else {
                            showErrorDialog(
                                "Error",
                                "No se pudieron obtener los datos del usuario"
                            )
                            binding.verifyButton.isEnabled = true
                            binding.verifyButton.text = "Verificar código"
                        }
                    }
                } else {
                    when (otpResponse.code()) {
                        400, 401 -> {
                            clearOtpFields()
                            showErrorDialog(
                                "Código incorrecto",
                                "El código ingresado no es válido. Por favor, intenta nuevamente."
                            )
                            // Iniciar timer de 1 minuto después de código incorrecto
                            saveLastResendTime()
                            startCountDownTimer(RESEND_COOLDOWN)
                        }
                        403 -> {
                            showErrorDialog(
                                "Demasiados intentos",
                                "Has excedido el número de intentos. Intenta más tarde."
                            )
                        }
                        else -> {
                            showErrorDialog(
                                "Error",
                                "Ocurrió un error al verificar el código. Intenta nuevamente."
                            )
                        }
                    }
                    binding.verifyButton.isEnabled = true
                    binding.verifyButton.text = "Verificar código"
                }
            } catch (e: Exception) {
                showErrorDialog(
                    "Error de conexión",
                    "No se pudo conectar con el servidor. Verifica tu conexión."
                )
                binding.verifyButton.isEnabled = true
                binding.verifyButton.text = "Verificar código"
            }
        }
    }

    private fun checkResendStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        resendAttempts = prefs.getInt(KEY_RESEND_ATTEMPTS + userEmail, 0)
        val blockTime = prefs.getLong(KEY_BLOCK_TIME + userEmail, 0)

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - blockTime < BLOCK_DURATION) {
                val remainingHours = ((BLOCK_DURATION - (currentTime - blockTime)) / 1000 / 60 / 60).toInt()
                binding.resendText.apply {
                    isEnabled = false
                    text = "Bloqueado por $remainingHours horas"
                }
            } else {
                resetResendAttempts()
            }
        }
    }

    private fun canResendCode(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockTime = prefs.getLong(KEY_BLOCK_TIME + userEmail, 0)

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - blockTime < BLOCK_DURATION) {
                val remainingHours = ((BLOCK_DURATION - (currentTime - blockTime)) / 1000 / 60 / 60).toInt()
                Toast.makeText(
                    this,
                    "Reenvío bloqueado. Intenta en $remainingHours horas",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }
        return true
    }

    private fun resendCode() {
        if (!canResendCode()) {
            return
        }

        binding.resendText.isEnabled = false

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiServiceNoAuth()
                val response = apiService.resendOtp(ResendOTPRequest(userId))

                if (response.isSuccessful) {
                    resendAttempts++
                    saveResendAttempt()
                    saveLastResendTime()

                    response.body()?.let { baseResponse ->
                        val message = if (baseResponse.reenviosRestantes != null) {
                            "Código enviado. Reenvíos restantes: ${baseResponse.reenviosRestantes}"
                        } else {
                            "Código reenviado a $userEmail"
                        }
                        Toast.makeText(this@OtpVerificationActivity, message, Toast.LENGTH_LONG).show()
                    }

                    if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
                        binding.resendText.text = "Bloqueado por 24 horas"
                        binding.resendText.isEnabled = false
                    } else {
                        // Iniciar countdown de 1 minuto
                        startCountDownTimer(RESEND_COOLDOWN)
                    }
                } else {
                    when (response.code()) {
                        403 -> {
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                "Has alcanzado el límite de reenvíos",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.text = "Bloqueado por 24 horas"
                            binding.resendText.isEnabled = false
                        }
                        else -> {
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                "Error al reenviar el código",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.isEnabled = true
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OtpVerificationActivity,
                    "Error de conexión",
                    Toast.LENGTH_SHORT
                ).show()
                binding.resendText.isEnabled = true
            }
        }
    }

    private fun clearOtpFields() {
        otpFields.forEach { it.setText("") }
        otpFields[0].requestFocus()
    }

    private fun saveResendAttempt() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_RESEND_ATTEMPTS + userEmail, resendAttempts)
            if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
                putLong(KEY_BLOCK_TIME + userEmail, System.currentTimeMillis())
            }
            apply()
        }
    }

    private fun saveLastResendTime() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_RESEND + userEmail, System.currentTimeMillis()).apply()
    }

    private fun resetResendAttempts() {
        resendAttempts = 0
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_RESEND_ATTEMPTS + userEmail)
            remove(KEY_BLOCK_TIME + userEmail)
            apply()
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}