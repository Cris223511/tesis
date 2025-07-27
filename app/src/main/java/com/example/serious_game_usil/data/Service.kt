package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    val token: String
)

data class RegisterRequest(
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numeroDocumento: String,
    val sexo: String,
    val telefono: String,
    val correo: String,
    @SerializedName("role_ids") val roleIds: List<Int>
)

data class RegisterResponse(
    val message: String,
    val user: UserResponse
)

data class UserResponse(
    val id: Int,
    @SerializedName("usuario") val usuario: String,
    val correo: String,
    @SerializedName("password_temporal") val passwordTemporal: String
)

data class LoginRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("contrasena") val contrasena: String
)

data class LoginResponse(
    val message: String,
    val user: UserInfo
)

data class UserInfo(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
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

data class RefreshTokenResponse(
    @SerializedName("bearer_token") val bearerToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

data class UserDetailResponse(
    val id: Int,
    @SerializedName("usuario") val nombreUsuario: String,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numeroDocumento: String,
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