package com.example.serious_game_usil.data

data class VideoEducativo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val videoUrl: String,
    val category: String = "GENERAL",
    val description: String = ""
)

enum class VideoCategory(val displayName: String, val searchTerm: String) {
    TODOS("Todos", "TEA autismo"),
    CONDUCTA("Conducta", "TEA autismo conducta comportamiento"),
    COMUNICACION("Comunicación", "TEA autismo comunicación lenguaje"),
    EMOCIONES("Emociones", "TEA autismo emociones regulación")
}
