package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.*
import com.example.serious_game_usil.network.RetrofitClient
import com.example.serious_game_usil.`interface`.LatestPatientsResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PatientRepository {
    private val apiService = RetrofitClient.getApiService()

    fun getPatients(page: Int = 1, limit: Int = 10, search: String? = null): Flow<ApiResult<PatientsListResponse>> = flow {
        try {
            val response = apiService.getPatients(page, limit, search)
            if (response.isSuccessful) {
                response.body()?.let { patientsResponse ->
                    emit(ApiResult.Success(patientsResponse))
                } ?: emit(ApiResult.Success(PatientsListResponse(emptyList())))
            } else {
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(ApiResult.NetworkError(e))
        }
    }

    fun createPatient(request: CreatePatientRequest): Flow<ApiResult<PatientResponse>> = flow {
        try {
            val response = apiService.createPatient(request)
            if (response.isSuccessful) {
                response.body()?.let { patientResponse ->
                    emit(ApiResult.Success(patientResponse))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getPatient(patientId: Int): Flow<ApiResult<Patient>> = flow {
        try {
            android.util.Log.d("PatientRepository", "Getting patient with ID: $patientId")
            val response = apiService.getPatient(patientId)
            android.util.Log.d("PatientRepository", "Response code: ${response.code()}")
            android.util.Log.d("PatientRepository", "Response successful: ${response.isSuccessful}")

            if (response.isSuccessful) {
                response.body()?.let { patient ->
                    android.util.Log.d("PatientRepository", "Patient data: $patient")
                    emit(ApiResult.Success(patient))
                } ?: run {
                    android.util.Log.e("PatientRepository", "Response body is null")
                    emit(ApiResult.Error(400, "No data received"))
                }
            } else {
                android.util.Log.e("PatientRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PatientRepository", "Exception getting patient", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun updatePatient(patientId: Int, request: UpdatePatientRequest): Flow<ApiResult<PatientResponse>> = flow {
        try {
            val response = apiService.updatePatient(patientId, request)
            if (response.isSuccessful) {
                response.body()?.let { patientResponse ->
                    emit(ApiResult.Success(patientResponse))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(ApiResult.NetworkError(e))
        }
    }

    fun deletePatient(patientId: Int): Flow<ApiResult<DeletePatientResponse>> = flow {
        try {
            val response = apiService.deletePatient(patientId)
            if (response.isSuccessful) {
                response.body()?.let { deleteResponse ->
                    emit(ApiResult.Success(deleteResponse))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getCaregivers(): Flow<ApiResult<List<UserListItem>>> = flow {
        try {
            android.util.Log.d("PatientRepository", "Fetching caregivers...")
            val response = apiService.searchUsers("", 100)
            android.util.Log.d("PatientRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { users ->
                    android.util.Log.d("PatientRepository", "Received ${users.size} users")
                    val caregivers = users.filter { user ->
                        user.roles.any { role ->
                            role.name.lowercase() in listOf("pd", "cuidador", "padre")
                        }
                    }
                    android.util.Log.d("PatientRepository", "Filtered to ${caregivers.size} caregivers")
                    emit(ApiResult.Success(caregivers))
                } ?: emit(ApiResult.Error(400, "No data received"))
            } else {
                android.util.Log.e("PatientRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PatientRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getLatestPatients(): Flow<ApiResult<List<PatientListItem>>> = flow {
        try {
            android.util.Log.d("PatientRepository", "Fetching latest 3 patients...")
            val response = apiService.getLatestPatients()
            android.util.Log.d("PatientRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { latestPatientsResponse ->
                    android.util.Log.d("PatientRepository", "Received ${latestPatientsResponse.data.size} latest patients")
                    emit(ApiResult.Success(latestPatientsResponse.data))
                } ?: emit(ApiResult.Error(400, "No data received"))
            } else {
                android.util.Log.e("PatientRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PatientRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    suspend fun getAllPatients(): List<com.example.serious_game_usil.`interface`.PatientListItem> {
        return try {
            android.util.Log.d("PatientRepository", "Fetching all patients for therapy session...")
            val response = apiService.getPatients(page = 1, limit = 100)

            if (response.isSuccessful) {
                response.body()?.let { patientsResponse ->
                    android.util.Log.d("PatientRepository", "Received ${patientsResponse.patients?.size ?: 0} patients")
                    // Convert Patient objects to PatientListItem
                    patientsResponse.patients?.map { patient ->
                        com.example.serious_game_usil.`interface`.PatientListItem(
                            id = patient.id,
                            nombresApellidos = patient.nombresApellidos,
                            serialId = patient.serialId,
                            edad = patient.edad,
                            fotoMovil = patient.fotoMovil
                        )
                    } ?: emptyList()
                } ?: emptyList()
            } else {
                android.util.Log.e("PatientRepository", "Error response: ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("PatientRepository", "Exception: ${e.message}", e)
            emptyList()
        }
    }

    fun getPatientStats(patientId: Int): Flow<ApiResult<PatientStatsResponse>> = flow {
        try {
            android.util.Log.d("PatientRepository", "Getting stats for patient ID: $patientId")
            val response = apiService.getPatientStats(patientId)
            android.util.Log.d("PatientRepository", "Stats response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { statsResponse ->
                    android.util.Log.d("PatientRepository", "Stats received: ${statsResponse.data}")
                    emit(ApiResult.Success(statsResponse.data))
                } ?: emit(ApiResult.Error(400, "No stats data received"))
            } else {
                emit(ApiResult.Error(response.code(), "Error getting stats: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("PatientRepository", "Error getting patient stats", e)
            emit(ApiResult.NetworkError(e))
        }
    }
}