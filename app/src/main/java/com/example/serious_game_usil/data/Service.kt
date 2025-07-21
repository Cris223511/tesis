package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("nombre_apellidos") val nombreApellidos: String,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    val sexo: String,
    val celular: String,
    val correo: String,
    @SerializedName("role_ids") val roleIds: List<Int>
)

data class RegisterResponse(
    val message: String,
    val user: UserResponse
)

data class UserResponse(
    val id: Int,
    @SerializedName("nombre_usuario") val nombreUsuario: String,
    val correo: String,
    @SerializedName("password_temporal") val passwordTemporal: String
)

data class LoginRequest(
    @SerializedName("nombre_usuario") val nombreUsuario: String,
    val password: String
)

data class LoginResponse(
    val message: String,
    val user: UserInfo
)

data class UserInfo(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("nombre_apellidos") val nombreApellidos: String,
    val correo: String,
    val roles: List<Role>
)

data class Role(
    val id: Int,
    val name: String
)

data class OTPRequest(
    @SerializedName("user_id") val userId: Int,
    val otp: String
)

data class OTPResponse(
    val message: String,
    @SerializedName("user_id") val userId: Int,
    val roles: List<String>,
    @SerializedName("bearer_token") val bearerToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class ResendOTPRequest(
    @SerializedName("user_id") val userId: Int
)

data class BaseResponse(
    val message: String,
    @SerializedName("reenvios_restantes") val reenviosRestantes: Int? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class TokenResponse(
    @SerializedName("bearer_token") val bearerToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class UserDetailResponse(
    val id: Int,
    @SerializedName("nombre_usuario") val nombreUsuario: String,
    @SerializedName("nombre_apellidos") val nombreApellidos: String,
    val correo: String,
    val celular: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    val sexo: String,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    val activo: Boolean,
    val intentos: Int,
    val roles: List<Role>,
    @SerializedName("last_login_at") val lastLoginAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class UpdatePasswordRequest(
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val exception: Exception) : ApiResult<Nothing>()
}