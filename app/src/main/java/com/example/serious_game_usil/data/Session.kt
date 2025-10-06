package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class TherapySession(
    val id: Int,
    @SerializedName("nombre_sesion") val nombreSesion: String,
    @SerializedName("fecha_hora") val fechaHora: String, // "2023-10-15T14:30:00"
    @SerializedName("terapeuta_nombre") val terapeutaNombre: String,
    @SerializedName("paciente_nombre") val pacienteNombre: String,
    @SerializedName("ubicacion") val ubicacion: String? = null,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("estado") val estado: String, // "programada", "completada", "cancelada"
    @SerializedName("duracion_minutos") val duracionMinutos: Int? = null,
    @SerializedName("notas") val notas: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class SessionsListResponse(
    val sessions: List<TherapySession>? = null,
    val total: Int? = null,
    val page: Int? = null,
    val total_pages: Int? = null,
    val has_next: Boolean? = null,
    val has_previous: Boolean? = null
)

data class SessionDetailResponse(
    val session: SessionDetail
)

data class SessionDetail(
    val id: Int,
    @SerializedName("nombre_sesion") val nombreSesion: String,
    @SerializedName("fecha_hora") val fechaHora: String,
    @SerializedName("terapeuta_id") val terapeutaId: Int? = null,
    @SerializedName("terapeuta_nombre") val terapeutaNombre: String,
    @SerializedName("terapeuta_telefono") val terapeutaTelefono: String? = null,
    @SerializedName("terapeuta_correo") val terapeutaCorreo: String? = null,
    @SerializedName("paciente_id") val pacienteId: Int? = null,
    @SerializedName("paciente_nombre") val pacienteNombre: String,
    @SerializedName("paciente_edad") val pacienteEdad: Int? = null,
    @SerializedName("cuidador_id") val cuidadorId: Int? = null,
    @SerializedName("cuidador_nombre") val cuidadorNombre: String? = null,
    @SerializedName("ubicacion") val ubicacion: String? = null,
    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("objetivos") val objetivos: String? = null,
    @SerializedName("estado") val estado: String,
    @SerializedName("duracion_minutos") val duracionMinutos: Int? = null,
    @SerializedName("notas_terapeuta") val notasTerapeuta: String? = null,
    @SerializedName("notas_cuidador") val notasCuidador: String? = null,
    @SerializedName("materiales_necesarios") val materialesNecesarios: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)