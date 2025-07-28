package com.example.serious_game_usil.`interface`

import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.LoginRequest
import com.example.serious_game_usil.data.LoginResponse
import com.example.serious_game_usil.data.OTPRequest
import com.example.serious_game_usil.data.OTPResponse
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.ResendOTPRequest
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.presentation.ui.recuperation.OtpVerificationActivity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/otp/validate")
    suspend fun verifyOtp(@Body request: OTPRequest): Response<OTPResponse>

    @POST("/api/otp/resend")
    suspend fun resendOtp(@Body request: ResendOTPRequest): Response<BaseResponse>

    @GET("/api/users/{id}")
    suspend fun getUserDetail(@Path("id") userId: Int): Response<UserDetailResponse>

    @GET("/auth/gett")
    suspend fun getToken(): Response<TokenResponse>
}

data class TokenResponse(
    val token: String
)