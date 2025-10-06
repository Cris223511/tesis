package com.example.serious_game_usil.`interface`

import com.example.serious_game_usil.data.CreatePatientRequest
import com.example.serious_game_usil.data.UpdatePatientRequest
import com.example.serious_game_usil.data.PatientResponse
import com.example.serious_game_usil.data.PatientsListResponse
import com.example.serious_game_usil.data.DeletePatientResponse
import retrofit2.Response
import retrofit2.http.*

interface PatientService {
    @GET("api/patients")
    suspend fun getPatients(@Header("Authorization") token: String): Response<PatientsListResponse>

    @POST("api/patients")
    suspend fun createPatient(
        @Header("Authorization") token: String,
        @Body request: CreatePatientRequest
    ): Response<PatientResponse>

    @GET("api/patients/{id}")
    suspend fun getPatient(
        @Header("Authorization") token: String,
        @Path("id") patientId: Int
    ): Response<PatientResponse>

    @PUT("api/patients/{id}")
    suspend fun updatePatient(
        @Header("Authorization") token: String,
        @Path("id") patientId: Int,
        @Body request: UpdatePatientRequest
    ): Response<PatientResponse>

    @DELETE("api/patients/{id}")
    suspend fun deletePatient(
        @Header("Authorization") token: String,
        @Path("id") patientId: Int
    ): Response<DeletePatientResponse>
}