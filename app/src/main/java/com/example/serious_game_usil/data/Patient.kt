package com.example.serious_game_usil.data

import com.google.gson.annotations.SerializedName

data class CreatePatientRequest(
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val altura: Double? = null,
    val peso: Double? = null,
    val sexo: String,
    @SerializedName("diagnostico_clinico") val diagnosticoClinico: String? = null,
    val foto: String? = null,
    @SerializedName("cuidador_id") val cuidadorID: Int? = null,
    @SerializedName("terapeuta_id") val terapeutaID: Int? = null
)

data class UpdatePatientRequest(
    @SerializedName("nombres_apellidos") val nombresApellidos: String? = null,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String? = null,
    @SerializedName("tipo_documento") val tipoDocumento: String? = null,
    @SerializedName("num_documento") val numDocumento: String? = null,
    val altura: Double? = null,
    val peso: Double? = null,
    val sexo: String? = null,
    @SerializedName("diagnostico_clinico") val diagnosticoClinico: String? = null,
    val foto: String? = null,
    @SerializedName("cuidador_id") val cuidadorID: Int? = null,
    @SerializedName("terapeuta_id") val terapeutaID: Int? = null,
    val activo: Boolean? = null
)


data class PatientListItem(
    val id: Int,
    @SerializedName("serial_id") val serialId: String,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val edad: Int,
    val sexo: String,
    @SerializedName("terapeuta_nombre") val terapeutaNombre: String,
    @SerializedName("cuidador_nombre") val cuidadorNombre: String?,
    val activo: Boolean,
    val foto: String?
)

data class PatientsListResponse(
    val patients: List<PatientListItem>?,
    val total: Int? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total_pages: Int? = null,
    val has_next: Boolean? = null,
    val has_previous: Boolean? = null
)

data class DeletePatientResponse(
    val message: String
)

// Main Patient data class for internal use
data class Patient(
    val id: Int,
    @SerializedName("serial_id") val serialId: String,
    @SerializedName("nombres_apellidos") val nombresApellidos: String,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("num_documento") val numDocumento: String,
    val altura: Double? = null,
    val peso: Double? = null,
    val imc: Double? = null,
    val sexo: String,
    @SerializedName("diagnostico_clinico") val diagnosticoClinico: String? = null,
    val foto: String? = null,
    @SerializedName("terapeuta_id") val terapeutaId: Int,
    @SerializedName("terapeuta_nombre") val terapeutaNombre: String,
    @SerializedName("cuidador_id") val cuidadorId: Int? = null,
    @SerializedName("cuidador_nombre") val cuidadorNombre: String? = null,
    val activo: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

// Response wrapper for single patient
data class PatientResponse(
    val patient: Patient
)