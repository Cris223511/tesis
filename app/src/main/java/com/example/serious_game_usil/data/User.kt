package com.example.serious_game_usil.data

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val role: UserRole = UserRole.PARENT,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

enum class UserRole {
    PARENT,
    THERAPIST,
    ADMIN
}