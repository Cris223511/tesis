package com.example.serious_game_usil.presentation.ui.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
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
import org.json.JSONObject


class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var otpFields: List<EditText>
    private var userId: Int = 0
    private var userEmail: String = ""
    private var resendAttempts = 0
    private var failedOtpAttempts = 0
    private var countDownTimer: CountDownTimer? = null
    private var lastResendTime: Long = 0
    private var smsReceiver: BroadcastReceiver? = null
    private var clipboardMonitor: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastProcessedCode: String? = null
    private var otpRequestTime: Long = 0

    companion object {
        private const val PREFS_NAME = "OtpPrefs"
        private const val KEY_RESEND_ATTEMPTS = "resend_attempts_"
        private const val KEY_BLOCK_TIME = "block_time_"
        private const val KEY_LAST_RESEND = "last_resend_"
        private const val KEY_FAILED_OTP_ATTEMPTS = "failed_otp_attempts_"
        private const val KEY_OTP_BLOCK_TIME = "otp_block_time_"
        private const val MAX_RESEND_ATTEMPTS = 9999
        private const val MAX_OTP_ATTEMPTS = 5
        private const val BLOCK_DURATION = 24 * 60 * 60 * 1000L
        private const val OTP_BLOCK_DURATION = 30 * 60 * 1000L
        private const val RESEND_COOLDOWN = 5 * 60 * 1000L
        private const val CODE_VALIDITY_DURATION = 5 * 60 * 1000L
        private const val SMS_RETRIEVER_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("user_id", 0)
        userEmail = intent.getStringExtra("email") ?: ""
        if (userId == 0) finish()

        // Marcar el tiempo cuando se solicita el OTP
        otpRequestTime = System.currentTimeMillis()

        checkResendStatus()
        checkOtpAttemptsStatus()
        setupUI()
        setupOtpFields()
        setupSmsRetriever()
        setupClipboardMonitoring()

        // Auto-detectar código en portapapeles al cargar la pantalla
        checkClipboardOnLoad()
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
                            // Detectar si se pegó un código completo de 6 dígitos
                            if (text.length == 6 && text.all { it.isDigit() || it.isLetter() }) {
                                fillOtpFields(text.uppercase())
                                return
                            }

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

            // Detectar pegado desde portapapeles
            editText.setOnLongClickListener {
                checkClipboardForOtp()
                false // Permitir el menú contextual normal también
            }
        }

        // Agregar botón para pegar desde portapapeles
        addClipboardButton()
    }

    private fun fillOtpFields(code: String) {
        if (code.length == 6) {
            otpFields.forEachIndexed { index, editText ->
                editText.setText(code[index].toString())
            }
            otpFields.last().requestFocus()
            Toast.makeText(this, "Código completo detectado y aplicado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkClipboardForOtp() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip

            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()

                if (!clipText.isNullOrEmpty()) {
                    // Extraer código OTP del texto con múltiples patrones
                    val extractedCode = extractOtpFromText(clipText)

                    if (extractedCode != null) {
                        fillOtpFields(extractedCode)
                        Toast.makeText(this, "Código OTP pegado desde portapapeles", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No se encontró código OTP válido en el portapapeles", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OTP", "Error al acceder al portapapeles: ${e.message}")
        }
    }

    private fun addClipboardButton() {
        // Configurar el botón de pegar código del portapapeles
        binding.pasteCodeButton.setOnClickListener {
            checkClipboardForOtp()
        }
    }

    private fun checkClipboardOnLoad() {
        // Esperar un poco para asegurar que la pantalla esté completamente cargada
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip

                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString()

                    if (!clipText.isNullOrEmpty()) {
                        // Buscar código OTP con múltiples patrones
                        val extractedCode = extractOtpFromText(clipText)

                        if (extractedCode != null && isCodeRecent()) {
                            lastProcessedCode = extractedCode
                            // Mostrar dialogo preguntando si quiere usar el código del portapapeles
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Código detectado")
                                .setMessage("Se detectó un código OTP reciente en el portapapeles: $extractedCode\n\n¿Deseas usarlo?")
                                .setPositiveButton("Sí, usar código") { _, _ ->
                                    fillOtpFields(extractedCode)
                                    Toast.makeText(this, "Código aplicado desde portapapeles", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("No, gracias", null)
                                .setCancelable(true)
                                .show()
                        } else if (extractedCode != null) {
                            // Código encontrado pero muy antiguo
                            Log.d("OTP", "Código encontrado en portapapeles pero es muy antiguo, ignorando")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OTP", "Error al verificar portapapeles al cargar: ${e.message}")
            }
        }, 1000) // Esperar 1 segundo
    }

    private fun setupSmsRetriever() {
        try {
            // Inicializar SMS Retriever API
            val client = SmsRetriever.getClient(this)
            val task = client.startSmsRetriever()

            task.addOnSuccessListener {
                Log.d("OTP", "SMS Retriever iniciado exitosamente")

                // Registrar BroadcastReceiver para recibir SMS
                smsReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                            val extras = intent.extras
                            val status = extras?.get(SmsRetriever.EXTRA_STATUS) as Status

                            when (status.statusCode) {
                                CommonStatusCodes.SUCCESS -> {
                                    val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: ""
                                    Log.d("OTP", "SMS recibido: $message")

                                    val extractedCode = extractOtpFromText(message)
                                    if (extractedCode != null && isCodeRecent()) {
                                        runOnUiThread {
                                            showAutoFillDialog(extractedCode, "SMS")
                                        }
                                    }
                                }
                                CommonStatusCodes.TIMEOUT -> {
                                    Log.d("OTP", "SMS Retriever timeout")
                                }
                            }
                        }
                    }
                }

                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                registerReceiver(smsReceiver, intentFilter)
            }

            task.addOnFailureListener { e ->
                Log.e("OTP", "Error al inicializar SMS Retriever: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("OTP", "Excepción al configurar SMS Retriever: ${e.message}")
        }
    }

    private fun setupClipboardMonitoring() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            clipboardMonitor = ClipboardManager.OnPrimaryClipChangedListener {
                Handler(Looper.getMainLooper()).postDelayed({
                    checkClipboardForNewCode()
                }, 500) // Esperar medio segundo para asegurar que el clip esté listo
            }

            clipboard.addPrimaryClipChangedListener(clipboardMonitor)
            Log.d("OTP", "Monitoreo de portapapeles iniciado")
        } catch (e: Exception) {
            Log.e("OTP", "Error al configurar monitoreo de portapapeles: ${e.message}")
        }
    }

    private fun checkClipboardForNewCode() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip

            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).text?.toString()

                if (!clipText.isNullOrEmpty()) {
                    val extractedCode = extractOtpFromText(clipText)

                    if (extractedCode != null &&
                        extractedCode != lastProcessedCode &&
                        isCodeRecent()) {

                        lastProcessedCode = extractedCode
                        showAutoFillDialog(extractedCode, "portapapeles")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OTP", "Error al verificar portapapeles: ${e.message}")
        }
    }

    private fun isCodeRecent(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceRequest = currentTime - otpRequestTime
        return timeSinceRequest <= CODE_VALIDITY_DURATION
    }

    private fun showAutoFillDialog(code: String, source: String) {
        if (getOtpCode().length >= 6) {
            // Ya hay un código completo, no mostrar diálogo
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Código detectado")
            .setMessage("Se detectó un código OTP desde $source: $code\n\n¿Deseas usarlo para auto-completar?")
            .setPositiveButton("Sí, usar código") { _, _ ->
                fillOtpFields(code)
                Toast.makeText(this, "Código aplicado desde $source", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No, gracias", null)
            .setCancelable(true)
            .show()
    }

    private fun extractOtpFromText(text: String): String? {
        val upperText = text.uppercase()

        // Patrones comunes para códigos OTP más específicos
        val patterns = listOf(
            // Patrón en contexto de verificación: "código de verificación: ABC123"
            Regex("(?:código|code)\\s+(?:de\\s+)?(?:verificación|verification|otp|pin)\\s*:?\\s*([A-Z0-9]{6})"),
            // Patrón para email/SMS típico: "Tu código es ABC123" o "Your code is ABC123"
            Regex("(?:tu|your|su)\\s+(?:código|code)\\s+(?:es|is|de\\s+verificación)\\s*:?\\s*([A-Z0-9]{6})"),
            // Patrón directo: "código: ABC123" o "OTP: ABC123"
            Regex("(?:código|code|otp|pin)\\s*:?\\s*([A-Z0-9]{6})"),
            // Patrón con contexto de seguridad: "código de seguridad ABC123"
            Regex("(?:código|code)\\s+(?:de\\s+)?(?:seguridad|security)\\s*:?\\s*([A-Z0-9]{6})"),
            // Patrón con guiones: ABC-123 en contexto
            Regex("(?:código|code|otp)\\s*:?\\s*([A-Z0-9]{3}-[A-Z0-9]{3})"),
            // Patrón principal: 6 caracteres alfanuméricos consecutivos (menos prioritario)
            Regex("\\b([A-Z0-9]{6})\\b"),
            // Patrón numérico puro de 6 dígitos en contexto
            Regex("(?:código|code|otp|verification)\\s*:?\\s*([0-9]{6})"),
            // Patrón para mensajes en español
            Regex("(?:usar|utilizar|ingresar)\\s+(?:el\\s+)?(?:código|code)\\s*:?\\s*([A-Z0-9]{6})")
        )

        for (pattern in patterns) {
            val match = pattern.find(upperText)
            if (match != null) {
                var code = if (match.groupValues.size > 1) {
                    match.groupValues[1] // Usar grupo de captura si existe
                } else {
                    match.value // Usar el match completo
                }

                // Limpiar caracteres no deseados
                code = code.replace(Regex("[\\s-]"), "")

                // Verificar que el código final tenga exactamente 6 caracteres
                if (code.length == 6 && code.matches(Regex("[A-Z0-9]{6}"))) {
                    return code
                }
            }
        }

        return null
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
                resendCode(isAutomatic = true)
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
                            val errorPayload = parseErrorPayload(otpResponse.errorBody()?.string())
                            clearOtpFields()

                            if (errorPayload.autoResent) {
                                saveLastResendTime()
                                startCountDownTimer(RESEND_COOLDOWN)
                                showErrorDialog(
                                    "Código renovado",
                                    errorPayload.message.ifBlank {
                                        "El código expiró. Se generó y envió uno nuevo automáticamente."
                                    }
                                )
                            } else {
                                incrementFailedOtpAttempts()
                                if (failedOtpAttempts >= MAX_OTP_ATTEMPTS) {
                                    showErrorDialog(
                                        "Cuenta bloqueada",
                                        errorPayload.message.ifBlank {
                                            "Has excedido el número máximo de intentos. Por favor espera 30 minutos antes de intentar nuevamente."
                                        }
                                    )
                                    checkOtpAttemptsStatus()
                                } else {
                                    val attemptsLeft = MAX_OTP_ATTEMPTS - failedOtpAttempts
                                    showErrorDialog(
                                        "Código incorrecto",
                                        errorPayload.message.ifBlank {
                                            "El código ingresado no es válido. Te quedan $attemptsLeft intento${if (attemptsLeft > 1) "s" else ""}."
                                        }
                                    )
                                }
                            }
                        }
                        403 -> {
                            val errorPayload = parseErrorPayload(otpResponse.errorBody()?.string())
                            showErrorDialog(
                                if (errorPayload.message.contains("deshabilitada", ignoreCase = true)) {
                                    "Cuenta deshabilitada"
                                } else {
                                    "Cuenta bloqueada"
                                },
                                errorPayload.message.ifBlank { "Has excedido el número de intentos. Intenta más tarde." }
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
        val lastResend = prefs.getLong(KEY_LAST_RESEND + userEmail, 0)
        val elapsed = System.currentTimeMillis() - lastResend

        if (lastResend > 0 && elapsed < RESEND_COOLDOWN) {
            val remainingMillis = RESEND_COOLDOWN - elapsed
            val remainingSeconds = (remainingMillis / 1000).toInt()
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            Toast.makeText(
                this,
                String.format("Espera %02d:%02d para reenviar", minutes, seconds),
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    private fun resendCode(isAutomatic: Boolean = false) {
        if (!canResendCode()) {
            return
        }

        binding.resendText.isEnabled = false

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiServiceNoAuth()
                val response = apiService.resendOtp(ResendOTPRequest(userId))

                if (response.isSuccessful) {
                    saveLastResendTime()
                    otpRequestTime = System.currentTimeMillis()
                    lastProcessedCode = null

                    response.body()?.let { baseResponse ->
                        val message = if (isAutomatic) {
                            "El código expiró y se envió uno nuevo automáticamente."
                        } else {
                            baseResponse.message.ifBlank { "Código reenviado. Válido por 5 minutos." }
                        }
                        Toast.makeText(this@OtpVerificationActivity, message, Toast.LENGTH_LONG).show()
                    }

                    clearOtpFields()
                    startCountDownTimer(RESEND_COOLDOWN)
                } else {
                    when (response.code()) {
                        403 -> {
                            val errorPayload = parseErrorPayload(response.errorBody()?.string())
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                errorPayload.message.ifBlank { "No se puede reenviar el código en este momento." },
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.isEnabled = false
                        }
                        429 -> {
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                "Espera un momento antes de reenviar",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.isEnabled = !isAutomatic
                        }
                        else -> {
                            Toast.makeText(
                                this@OtpVerificationActivity,
                                "Error al reenviar el código",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.resendText.isEnabled = !isAutomatic
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OtpVerificationActivity,
                    "Error de conexión",
                    Toast.LENGTH_SHORT
                ).show()
                binding.resendText.isEnabled = !isAutomatic
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

    private fun parseErrorPayload(raw: String?): OtpErrorPayload {
        if (raw.isNullOrBlank()) return OtpErrorPayload()
        return runCatching {
            val json = JSONObject(raw)
            OtpErrorPayload(
                message = json.optString("error"),
                autoResent = json.optBoolean("auto_resent", false)
            )
        }.getOrDefault(OtpErrorPayload(message = raw))
    }

    private data class OtpErrorPayload(
        val message: String = "",
        val autoResent: Boolean = false
    )

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

        // Limpiar recursos de SMS Retriever
        try {
            smsReceiver?.let { receiver ->
                unregisterReceiver(receiver)
                smsReceiver = null
            }
        } catch (e: Exception) {
            Log.e("OTP", "Error al desregistrar SMS receiver: ${e.message}")
        }

        // Limpiar monitoreo de portapapeles
        try {
            clipboardMonitor?.let { monitor ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.removePrimaryClipChangedListener(monitor)
                clipboardMonitor = null
            }
        } catch (e: Exception) {
            Log.e("OTP", "Error al remover clipboard monitor: ${e.message}")
        }
    }
}
