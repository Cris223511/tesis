package com.example.serious_game_usil.network

import retrofit2.Response
import retrofit2.http.*

interface BiometricApiService {

    @POST("api/fingerprint/auth")
    suspend fun authenticateFingerprint(@Body request: FingerprintAuthRequest): Response<BiometricAuthResponse>

    @POST("api/fingerprint/register")
    suspend fun registerFingerprint(
        @Header("Authorization") token: String,
        @Body request: FingerprintRegisterRequest
    ): Response<BiometricResponse>

    @DELETE("api/fingerprint/{finger_index}")
    suspend fun deleteFingerprint(
        @Header("Authorization") token: String,
        @Path("finger_index") fingerIndex: Int
    ): Response<BiometricResponse>

    @GET("api/fingerprint/status")
    suspend fun getFingerprintStatus(
        @Header("Authorization") token: String
    ): Response<BiometricStatusResponse>
}

data class FingerprintAuthRequest(
    val username: String,
    val fingerprint_data: String,
    val device_id: String
)

data class FingerprintRegisterRequest(
    val fingerprint_data: String,
    val device_id: String,
    val device_name: String,
    val finger_index: Int
)

data class BiometricAuthResponse(
    val success: Boolean,
    val bearer_token: String?,
    val refresh_token: String?,
    val user: UserData?,
    val error: String?,
    val remaining_attempts: Int?,
    val is_locked: Boolean?
)

data class BiometricResponse(
    val success: Boolean,
    val message: String
)

data class BiometricStatusResponse(
    val registered_fingerprints: Int,
    val can_register_more: Boolean,
    val is_locked: Boolean,
    val failed_attempts: Long,
    val remaining_attempts: Long,
    val lockout_end_time: String?,
    val remaining_lockout_seconds: Int?
)

data class UserData(
    val id: Int,
    val nombres_apellidos: String
)