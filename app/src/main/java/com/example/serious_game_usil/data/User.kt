package com.example.serious_game_usil.data

data class User(
    val id: Int,
    val nombresApellidos: String,
    val correo: String,
    val telefono: String,
    val tipoDocumento: String,
    val numDocumento: String,
    val sexo: String,
    val foto: String?,
    val roles: List<String>
)
