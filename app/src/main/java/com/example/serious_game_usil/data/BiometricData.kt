package com.example.serious_game_usil.data

import java.util.Date

// Modelo de datos para registro biométrico
data class BiometricRegistration(
    val userId: String,
    val fingerprintId: String,
    val fingerprintHash: String, // Hash único del biométrico
    val deviceId: String,
    val registrationDate: Date,
    val isActive: Boolean = true,
    val fingerIndex: Int // 1 o 2 (máximo 2 dedos)
)

// Modelo para intentos de autenticación
data class BiometricAttempt(
    val userId: String,
    val attemptTime: Date,
    val success: Boolean,
    val deviceId: String,
    val failureReason: String? = null
)

// Modelo para bloqueo temporal
data class BiometricLockout(
    val userId: String,
    val lockoutStartTime: Date,
    val lockoutEndTime: Date,
    val reason: String,
    val attemptCount: Int
)

// Respuesta de autenticación biométrica
data class BiometricAuthResponse(
    val success: Boolean,
    val message: String,
    val token: String? = null,
    val remainingAttempts: Int? = null,
    val lockoutTime: Long? = null // Tiempo restante de bloqueo en segundos
)

// Request para registrar huella
data class BiometricRegisterRequest(
    val userId: String,
    val fingerprintData: String, // Datos encriptados del biométrico
    val deviceId: String,
    val fingerIndex: Int
)

// Request para autenticar
data class BiometricAuthRequest(
    val userId: String,
    val fingerprintData: String,
    val deviceId: String
)

// Estado del usuario biométrico
data class BiometricUserStatus(
    val userId: String,
    val registeredFingerprints: Int,
    val isLocked: Boolean,
    val lockoutEndTime: Date? = null,
    val failedAttempts: Int,
    val canRegisterMore: Boolean
)