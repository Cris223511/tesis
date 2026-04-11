package com.example.serious_game_usil.presentation.ui.login

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.serious_game_usil.repository.BiometricRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BiometricLoginHelper(private val activity: FragmentActivity) {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private val repository = BiometricRepository(activity)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun showBiometricPromptForLogin(
        username: String,
        onSuccess: (token: String, refreshToken: String) -> Unit,
        onError: (message: String, remainingAttempts: Int?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(200)
                    }
                    onError(errString.toString(), null)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(50)
                    }

                    scope.launch {
                        val biometricData = ByteArray(32).apply {
                            java.util.Random().nextBytes(this)
                        }

                        val authResult = repository.authenticateWithFingerprint(username, biometricData)

                        authResult.fold(
                            onSuccess = { response ->
                                if (response.success && response.bearer_token != null) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        onSuccess(response.bearer_token, response.refresh_token ?: "")
                                    }, 100)
                                } else {
                                    onError(
                                        response.error ?: "Authentication failed",
                                        response.remaining_attempts
                                    )
                                }
                            },
                            onFailure = { exception ->
                                onError(exception.message ?: "Unknown error", null)
                            }
                        )
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("🔒 Serious Game")
            .setSubtitle("Autenticación biométrica")
            .setDescription("Toca el sensor o mira la cámara")
            .setNegativeButtonText("Usar contraseña")
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun showBiometricPromptForRegistration(
        token: String,
        fingerIndex: Int,
        onSuccess: (message: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(200)
                    }
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(50)
                    }

                    scope.launch {
                        val biometricData = ByteArray(32).apply {
                            java.util.Random().nextBytes(this)
                        }

                        val registerResult = repository.registerFingerprint(
                            token = token,
                            biometricData = biometricData,
                            fingerIndex = fingerIndex,
                            deviceName = repository.getDeviceName()
                        )

                        registerResult.fold(
                            onSuccess = { response ->
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onSuccess(response.message)
                                }, 100)
                            },
                            onFailure = { exception ->
                                onError(exception.message ?: "Registration failed")
                            }
                        )
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(100)
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("🔒 Registrar Huella")
            .setSubtitle("Dedo ${if(fingerIndex == 1) "índice" else "pulgar"}")
            .setDescription("Coloca tu dedo en el sensor")
            .setNegativeButtonText("Cancelar")
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    suspend fun checkBiometricStatus(token: String): BiometricStatus? {
        return withContext(Dispatchers.IO) {
            repository.getBiometricStatus(token).fold(
                onSuccess = { response ->
                    BiometricStatus(
                        registeredFingerprints = response.registered_fingerprints,
                        canRegisterMore = response.can_register_more,
                        isLocked = response.is_locked,
                        remainingAttempts = response.remaining_attempts.toInt(),
                        lockoutTimeRemaining = response.remaining_lockout_seconds ?: 0
                    )
                },
                onFailure = { null }
            )
        }
    }

    suspend fun deleteFingerprint(token: String, fingerIndex: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            repository.deleteFingerprint(token, fingerIndex).fold(
                onSuccess = { Result.success(it.message) },
                onFailure = { Result.failure(it) }
            )
        }
    }

    data class BiometricStatus(
        val registeredFingerprints: Int,
        val canRegisterMore: Boolean,
        val isLocked: Boolean,
        val remainingAttempts: Int,
        val lockoutTimeRemaining: Int
    )
}