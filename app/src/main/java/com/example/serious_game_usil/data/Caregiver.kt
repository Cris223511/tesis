package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class Caregiver(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val sexo: String,
    @SerializedName("foto_movil") val fotoMovil: String?,
    val activo: Boolean,
    @SerializedName("fecha_creacion") val fechaCreacion: String,
    @SerializedName("pacientes_asignados") val pacientesAsignados: Int = 0,
    @SerializedName("fecha_ultimo_acceso") val fechaUltimoAcceso: String?
)

data class CaregiverListResponse(
    val message: String,
    val caregivers: List<Caregiver>?,
    val total: Int?,
    val page: Int?,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("has_previous") val hasPrevious: Boolean?,
    @SerializedName("has_next") val hasNext: Boolean?
)

data class CaregiverDetailResponse(
    val message: String,
    val caregiver: CaregiverDetail
)

data class CaregiverDetail(
    val id: Int,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val sexo: String,
    @SerializedName("foto_movil") val fotoMovil: String?,
    val activo: Boolean,
    @SerializedName("fecha_creacion") val fechaCreacion: String,
    @SerializedName("fecha_ultimo_acceso") val fechaUltimoAcceso: String?,
    @SerializedName("pacientes_asignados") val pacientesAsignados: List<PatientListItem>
)

data class CreateCaregiverRequest(
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val sexo: String,
    @SerializedName("foto_movil") val fotoMovil: String?,
    val password: String
)

data class UpdateCaregiverRequest(
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    val correo: String,
    val telefono: String?,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val sexo: String,
    @SerializedName("foto_movil") val fotoMovil: String?,
    val activo: Boolean
)