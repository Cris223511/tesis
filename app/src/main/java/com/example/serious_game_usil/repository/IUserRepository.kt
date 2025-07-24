package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.User

interface IUserRepository {

    suspend fun getCurrentUser(): User

    suspend fun updateUser(user: User): Boolean

    suspend fun updateLastNavigationItem(item: String)

    suspend fun getLastNavigationItem(): String?

    suspend fun logout(): Boolean

    suspend fun isUserLoggedIn(): Boolean

    suspend fun saveAuthToken(token: String)

    suspend fun getAuthToken(): String?
}