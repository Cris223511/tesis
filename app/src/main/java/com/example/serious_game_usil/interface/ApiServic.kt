package com.example.serious_game_usil.`interface`

import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.LoginRequest
import com.example.serious_game_usil.data.LoginResponse
import com.example.serious_game_usil.data.OTPRequest
import com.example.serious_game_usil.data.OTPResponse
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.ResendOTPRequest
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.data.UpdateUserStatusRequest
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UsersListResponse
import com.example.serious_game_usil.presentation.ui.recuperation.OtpVerificationActivity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/otp/validate")
    suspend fun verifyOtp(@Body request: OTPRequest): Response<OTPResponse>

    @POST("/api/otp/resend")
    suspend fun resendOtp(@Body request: ResendOTPRequest): Response<BaseResponse>


    @GET("api/users")
    suspend fun getUsers(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("active") active: Boolean? = null,
        @Query("role_id") roleId: Int? = null,
        @Query("order_by") orderBy: String = "created_at",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<UsersListResponse>

    @GET("/api/users/{id}")
    suspend fun getUserDetail(@Path("id") userId: Int): Response<UserDetailResponse>


    @PUT("api/users/{id}")
    suspend fun updateUser(
        @Path("id") userId: Int,
        @Body request: UpdateUserRequest
    ): Response<UserDetailResponse>


    @PATCH("api/users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") userId: Int,
        @Body request: UpdateUserStatusRequest
    ): Response<BaseResponse>

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") userId: Int): Response<BaseResponse>


    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<UserListItem>>

    @GET("api/roles")
    suspend fun getRoles(): Response<List<Role>>

    @GET("/auth/gett")
    suspend fun getToken(): Response<TokenResponse>
}

data class TokenResponse(
    val token: String
)