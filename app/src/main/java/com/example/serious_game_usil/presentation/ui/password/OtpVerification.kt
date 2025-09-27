package com.example.serious_game_usil.presentation.ui.password

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.serious_game_usil.data.OTPRequest
import com.example.serious_game_usil.data.ResendOTPRequest
import com.example.serious_game_usil.databinding.ActivityOtpVerificationBinding
import com.example.serious_game_usil.guards.AuthManager
import com.seriousgame.app.navigation.RouteNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.serious_game_usil.R
import com.example.serious_game_usil.network.RetrofitClient
import kotlinx.coroutines.delay


class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var otpFields: List<EditText>
    private var userId: Int = 0
    private var userEmail: String = ""
    private var resendAttempts = 0
    private var failedOtpAttempts = 0
    private var countDownTimer: CountDownTimer? = null
    private var lastResendTime: Long = 0

    companion object {
        private const val PREFS_NAME = "OtpPrefs"
        private const val KEY_RESEND_ATTEMPTS = "resend_attempts_"
        private const val KEY_BLOCK_TIME = "block_time_"
        private const val KEY_LAST_RESEND = "last_resend_"
        private const val KEY_FAILED_OTP_ATTEMPTS = "failed_otp_attempts_"
        private const val KEY_OTP_BLOCK_TIME = "otp_block_time_"
        private const val MAX_RESEND_ATTEMPTS = 3
        private const val MAX_OTP_ATTEMPTS = 3
        private const val BLOCK_DURATION = 24 * 60 * 60 * 1000L
        private const val OTP_BLOCK_DURATION = 30 * 60 * 1000L
        private const val RESEND_COOLDOWN = 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("user_id", 0)
        userEmail = intent.getStringExtra("email") ?: ""
        if (userId == 0) finish()

        checkResendStatus()
        checkOtpAttemptsStatus()
        setupUI()
        setupOtpFields()
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

        startInitialTimer()
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
                            if (text != text.uppercase()) {
                                editText.setText(text.uppercase())
                                editText.setSelection(text.length)
                            }
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

    private fun checkOtpAttemptsStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        failedOtpAttempts = prefs.getInt(KEY_FAILED_OTP_ATTEMPTS + userEmail, 0)
        val otpBlockTime = prefs.getLong(KEY_OTP_BLOCK_TIME + userEmail, 0)

        if (failedOtpAttempts >= MAX_OTP_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - otpBlockTime < OTP_BLOCK_DURATION) {
                val remainingMillis = OTP_BLOCK_DURATION - (currentTime - otpBlockTime)
                val remainingMinutes = (remainingMillis / 1000 / 60).toInt()

                val blockText = when {
                    remainingMinutes > 1 -> "Bloqueado por $remainingMinutes minutos"
                    remainingMinutes == 1 -> "Bloqueado por 1 minuto"
                    else -> "Bloqueado por menos de 1 minuto"
                }

                binding.verifyButton.apply {
                    isEnabled = false
                    text = blockText
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    resetOtpAttempts()
                    binding.verifyButton.apply {
                        isEnabled = true
                        text = "Verificar código"
                    }
                }, remainingMillis)
            } else {
                resetOtpAttempts()
            }
        }
    }

    private fun checkResendStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        resendAttempts = prefs.getInt(KEY_RESEND_ATTEMPTS + userEmail, 0)
        val blockTime = prefs.getLong(KEY_BLOCK_TIME + userEmail, 0)

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - blockTime < BLOCK_DURATION) {
                val remainingMillis = BLOCK_DURATION - (currentTime - blockTime)
                val remainingMinutes = (remainingMillis / 1000 / 60).toInt()
                val remainingHours = remainingMinutes / 60

                val blockText = when {
                    remainingHours >= 1 -> "Bloqueado por $remainingHours hora${if (remainingHours > 1) "s" else ""}"
                    remainingMinutes >= 1 -> "Bloqueado por $remainingMinutes minuto${if (remainingMinutes > 1) "s" else ""}"
                    else -> "Bloqueado por menos de 1 minuto"
                }

                binding.resendText.apply {
                    isEnabled = false
                    text = blockText
                }
                binding.timerText.visibility = View.GONE
            } else {
                resetResendAttempts()
            }
        }
    }

    private fun canShowTimer(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val blockTime = prefs.getLong(KEY_BLOCK_TIME + userEmail, 0)

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - blockTime < BLOCK_DURATION) {
                return false
            }
        }
        return true
    }



    private fun startInitialTimer() {
        // Deshabilita y atenúa el botón de reenvío
        binding.resendText.isEnabled = false
        binding.resendText.alpha = 0.5f


        binding.timerText.visibility = View.VISIBLE


        saveLastResendTime()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_RESEND + userEmail, 0L)
        val elapsed = System.currentTimeMillis() - last
        val millisToCount = if (elapsed in 1 until RESEND_COOLDOWN) {
            RESEND_COOLDOWN - elapsed
        } else {
            RESEND_COOLDOWN
        }

        startCountDownTimer(millisToCount)
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

                val color = when {
                    seconds <= 10 -> ContextCompat.getColor(this@OtpVerificationActivity, R.color.timer_danger)
                    seconds <= 30 -> ContextCompat.getColor(this@OtpVerificationActivity, R.color.timer_warning)
                    else -> ContextCompat.getColor(this@OtpVerificationActivity, R.color.timer_normal)
                }
                binding.timerText.setTextColor(color)
            }

            override fun onFinish() {
                binding.timerText.visibility = View.GONE
                binding.resendText.isEnabled = true
                binding.resendText.alpha = 1.0f
                binding.resendText.text = "Reenviar código"
                binding.resendText.setTextColor(
                    ContextCompat.getColor(this@OtpVerificationActivity, R.color.resend_enabled)
                )

                try {
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(100)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OTP", "Error al vibrar: ${e.message}")
                }
            }
        }.start()
    }

    private fun getOtpCode(): String {
        return otpFields.joinToString("") { it.text.toString() }
    }


    private fun verifyOtp() {
        val otp = getOtpCode()

        if (!canAttemptOtp()) {
            return
        }

        when {
            otp.length < 6 -> {
                Toast.makeText(this, "Ingresa el código completo", Toast.LENGTH_SHORT).show()
                return
            }
        }

        binding.verifyButton.isEnabled = false
        binding.verifyButton.text = "Verificando..."

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiServiceNoAuth()

                val otpResponse = withContext(Dispatchers.IO) {
                    apiService.verifyOtp(OTPRequest(userId, otp))
                }

                if (otpResponse.isSuccessful) {
                    resetOtpAttempts()
                    otpResponse.body()?.let { otpData ->

                        Log.d("OTP", "Response completo: $otpData")
                        Log.d("OTP", "Token recibido completo: ${otpData.bearerToken}")
                        Log.d("OTP", "Longitud del token: ${otpData.bearerToken.length}")
                        Log.d("OTP", "Token empieza con: ${otpData.bearerToken.take(50)}")
                        Log.d("OTP", "Token termina con: ${otpData.bearerToken.takeLast(20)}")

                        val userIdToUse = if (otpData.userId > 0) otpData.userId else userId

                        RetrofitClient.setAuthToken(otpData.bearerToken)
                        Log.d("OTP", "Token establecido en RetrofitClient")

                        delay(100)

                        val authenticatedApiService = RetrofitClient.getApiService()

                        val tempPrefs = getSharedPreferences("TempUserData", MODE_PRIVATE)
                        val savedProtectedRoute = tempPrefs.getString("protected_route", null)

                        Log.d("OTP", "Llamando a getUserDetail con userId: $userIdToUse")

                        val userDetailResponse = withContext(Dispatchers.IO) {
                            authenticatedApiService.getUserDetail(userIdToUse)
                        }

                        if (userDetailResponse.isSuccessful) {
                            userDetailResponse.body()?.let { userDetail ->
                                val roleNames = userDetail.roles.map { it.name }
                                val primaryRole = roleNames.firstOrNull() ?: ""

                                val finalRoute = when {
                                    !savedProtectedRoute.isNullOrEmpty() -> savedProtectedRoute
                                    primaryRole.equals("admin", ignoreCase = true) ||
                                            primaryRole.equals("administrador", ignoreCase = true) -> "/admin/dashboard"
                                    primaryRole.equals("padre", ignoreCase = true) ||
                                            primaryRole.equals("padres", ignoreCase = true) -> "/padre/dashboard"
                                    primaryRole.equals("hijo", ignoreCase = true) ||
                                            primaryRole.equals("hijos", ignoreCase = true) -> "/hijo/dashboard"
                                    primaryRole.equals("docente", ignoreCase = true) ||
                                            primaryRole.equals("profesor", ignoreCase = true) -> "/docente/dashboard"
                                    primaryRole.equals("especialista", ignoreCase = true) -> "/especialista/dashboard"
                                    else -> "/dashboard"
                                }

                                AuthManager.saveAuthData(
                                    accessToken = otpData.bearerToken,
                                    refreshToken = otpData.refreshToken,
                                    userId = userIdToUse,
                                    nombresApellidos = userDetail.nombresApellidos,
                                    correo = userDetail.correo,
                                    telefono = userDetail.telefono ?: "",
                                    tipoDocumento = userDetail.tipoDocumento,
                                    numDocumento = userDetail.numeroDocumento,
                                    sexo = userDetail.sexo,
                                    fotoMovil = userDetail.fotoMovil,
                                    roles = roleNames,
                                    roleIds = emptyList(),
                                    protectedRoute = finalRoute
                                )

                                tempPrefs.edit().clear().apply()
                                resetResendAttempts()

                                RouteNavigator.navigateToUserHome(this@OtpVerificationActivity)
                            }
                        } else {
                            Log.e("OTP", "Error al obtener UserDetail: ${userDetailResponse.code()}")
                            Log.e("OTP", "Error body: ${userDetailResponse.errorBody()?.string()}")

                            showErrorDialog(
                                "Error",
                                "No se pudieron obtener los datos del usuario. Por favor, intenta nuevamente."
                            )
                            binding.verifyButton.isEnabled = true
                            binding.verifyButton.text = "Verificar código"
                        }
                    }
                } else {
                    when (otpResponse.code()) {
                        400, 401 -> {
                            incrementFailedOtpAttempts()
                            clearOtpFields()

                            // DETENER EL CONTADOR ACTUAL
                            countDownTimer?.cancel()

                            // HABILITAR INMEDIATAMENTE EL BOTÓN DE REENVIAR
                            binding.resendText.apply {
                                isEnabled = true
                                alpha = 1.0f
                                text = "Reenviar código"
                                setTextColor(ContextCompat.getColor(this@OtpVerificationActivity, R.color.resend_enabled))
                            }

                            // OCULTAR EL TIMER
                            binding.timerText.visibility = View.GONE

                            if (failedOtpAttempts >= MAX_OTP_ATTEMPTS) {
                                showErrorDialog(
                                    "Cuenta bloqueada",
                                    "Has excedido el número máximo de intentos. Por favor espera 30 minutos antes de intentar nuevamente."
                                )
                                checkOtpAttemptsStatus()
                            } else {
                                val attemptsLeft = MAX_OTP_ATTEMPTS - failedOtpAttempts
                                showErrorDialog(
                                    "Código incorrecto",
                                    "El código ingresado no es válido. Te quedan $attemptsLeft intento${if (attemptsLeft > 1) "s" else ""}.\n\nPuedes solicitar un nuevo código."
                                )
                            }

                            // NO iniciar un nuevo timer, permitir reenvío inmediato
                            // saveLastResendTime() // COMENTADO
                            // startCountDownTimer(RESEND_COOLDOWN) // COMENTADO
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
                Log.e("OTP", "Excepción: ${e.message}", e)
                showErrorDialog(
                    "Error de conexión",
                    "No se pudo conectar con el servidor. Verifica tu conexión a internet."
                )
                binding.verifyButton.isEnabled = true
                binding.verifyButton.text = "Verificar código"
            }
        }
    }

    private fun canAttemptOtp(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val otpBlockTime = prefs.getLong(KEY_OTP_BLOCK_TIME + userEmail, 0)

        if (failedOtpAttempts >= MAX_OTP_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - otpBlockTime < OTP_BLOCK_DURATION) {
                val remainingMillis = OTP_BLOCK_DURATION - (currentTime - otpBlockTime)
                val remainingMinutes = (remainingMillis / 1000 / 60).toInt()

                val message = when {
                    remainingMinutes > 1 -> "Demasiados intentos fallidos. Intenta en $remainingMinutes minutos"
                    remainingMinutes == 1 -> "Demasiados intentos fallidos. Intenta en 1 minuto"
                    else -> "Demasiados intentos fallidos. Intenta en menos de 1 minuto"
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun incrementFailedOtpAttempts() {
        failedOtpAttempts++
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_FAILED_OTP_ATTEMPTS + userEmail, failedOtpAttempts)
            if (failedOtpAttempts >= MAX_OTP_ATTEMPTS) {
                putLong(KEY_OTP_BLOCK_TIME + userEmail, System.currentTimeMillis())
            }
            apply()
        }
    }

    private fun resetOtpAttempts() {
        failedOtpAttempts = 0
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_FAILED_OTP_ATTEMPTS + userEmail)
            remove(KEY_OTP_BLOCK_TIME + userEmail)
            apply()
        }
    }

    private fun canResendCode(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val blockTime = prefs.getLong(KEY_BLOCK_TIME + userEmail, 0)

        if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - blockTime < BLOCK_DURATION) {
                val remainingMillis = BLOCK_DURATION - (currentTime - blockTime)
                val remainingMinutes = (remainingMillis / 1000 / 60).toInt()
                val remainingHours = remainingMinutes / 60

                val message = when {
                    remainingHours >= 1 -> "Reenvío bloqueado. Intenta en $remainingHours hora${if (remainingHours > 1) "s" else ""}"
                    remainingMinutes >= 1 -> "Reenvío bloqueado. Intenta en $remainingMinutes minuto${if (remainingMinutes > 1) "s" else ""}"
                    else -> "Reenvío bloqueado. Intenta en menos de 1 minuto"
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
                        429 -> {
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                "Espera un momento antes de reenviar",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.isEnabled = true
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
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_RESEND_ATTEMPTS + userEmail, resendAttempts)
            if (resendAttempts >= MAX_RESEND_ATTEMPTS) {
                putLong(KEY_BLOCK_TIME + userEmail, System.currentTimeMillis())
            }
            apply()
        }
    }

    private fun saveLastResendTime() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_RESEND + userEmail, System.currentTimeMillis()).apply()
    }

    private fun resetResendAttempts() {
        resendAttempts = 0
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
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