package com.example.serious_game_usil.`interface`

import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("/auth/gett")
    suspend fun getToken(): Response<TokenResponse>

}

data class TokenResponse(
    val token: String
)