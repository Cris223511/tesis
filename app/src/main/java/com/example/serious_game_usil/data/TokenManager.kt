package com.example.serious_game_usil.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext



object TokenManager {
    @Volatile
    private var currentToken: String? = null

    private val tokenLock = Any()

    // ESTE MÉTODO DEBE ESTAR AQUÍ
    fun setToken(token: String) {
        synchronized(tokenLock) {
            currentToken = token
            RetrofitClient.setAuthToken(token)
        }
    }

    suspend fun ensureToken(): String? {
        synchronized(tokenLock) {
            currentToken?.let { return it }
        }

        return obtainToken()
    }

    private suspend fun obtainToken(): String? {
        return try {
            val apiService = RetrofitClient.getApiServiceNoAuth()
            val response = apiService.getToken()

            if (response.isSuccessful) {
                response.body()?.token?.let { token ->
                    synchronized(tokenLock) {
                        currentToken = token
                        RetrofitClient.setAuthToken(token)
                    }
                    token
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCurrentToken(): String? = synchronized(tokenLock) { currentToken }

    fun invalidateToken() {
        synchronized(tokenLock) {
            currentToken = null
            RetrofitClient.setAuthToken(null)
        }
    }
}