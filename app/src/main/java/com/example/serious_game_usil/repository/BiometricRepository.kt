package com.example.serious_game_usil.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.example.serious_game_usil.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class BiometricRepository(private val context: Context) {

    private val biometricService = RetrofitClient.getBiometricServiceNoAuth()
    private val biometricServiceAuth = RetrofitClient.getBiometricService()

    suspend fun authenticateWithFingerprint(
        username: String,
        biometricData: ByteArray
    ): Result<BiometricAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val fingerprintHash = hashBiometricData(biometricData)
            val deviceId = getDeviceId()

            val response = biometricService.authenticateFingerprint(
                FingerprintAuthRequest(
                    username = username,
                    fingerprint_data = fingerprintHash,
                    device_id = deviceId
                )
            )

            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerFingerprint(
        token: String,
        biometricData: ByteArray,
        fingerIndex: Int,
        deviceName: String
    ): Result<BiometricResponse> = withContext(Dispatchers.IO) {
        try {
            val fingerprintHash = hashBiometricData(biometricData)
            val deviceId = getDeviceId()

            val response = biometricServiceAuth.registerFingerprint(
                "Bearer $token",
                FingerprintRegisterRequest(
                    fingerprint_data = fingerprintHash,
                    device_id = deviceId,
                    device_name = deviceName.ifEmpty { "${Build.MANUFACTURER} ${Build.MODEL}" },
                    finger_index = fingerIndex
                )
            )

            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFingerprint(
        token: String,
        fingerIndex: Int
    ): Result<BiometricResponse> = withContext(Dispatchers.IO) {
        try {
            val response = biometricServiceAuth.deleteFingerprint(
                "Bearer $token",
                fingerIndex
            )

            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Delete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBiometricStatus(token: String): Result<BiometricStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = biometricServiceAuth.getFingerprintStatus("Bearer $token")

            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Status fetch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hashBiometricData(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}