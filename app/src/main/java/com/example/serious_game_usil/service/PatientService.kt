package com.example.serious_game_usil.service

import com.example.serious_game_usil.data.*
import com.example.serious_game_usil.repository.PatientRepository
import kotlinx.coroutines.flow.Flow


interface IPatientService {
    fun getPatients(page: Int = 1, limit: Int = 10, search: String? = null): Flow<ApiResult<PatientsListResponse>>
    fun getPatient(patientId: Int): Flow<ApiResult<Patient>>
    fun createPatient(request: CreatePatientRequest): Flow<ApiResult<PatientResponse>>
    fun updatePatient(patientId: Int, request: UpdatePatientRequest): Flow<ApiResult<PatientResponse>>
    fun deletePatient(patientId: Int): Flow<ApiResult<DeletePatientResponse>>
    fun getCaregivers(): Flow<ApiResult<List<UserListItem>>>
}

// Implementation of business logic
class PatientServiceImpl : IPatientService {
    private val patientRepository = PatientRepository()

    override fun getPatients(page: Int, limit: Int, search: String?): Flow<ApiResult<PatientsListResponse>> {
        return patientRepository.getPatients(page, limit, search)
    }

    override fun getPatient(patientId: Int): Flow<ApiResult<Patient>> {
        return patientRepository.getPatient(patientId)
    }

    override fun createPatient(request: CreatePatientRequest): Flow<ApiResult<PatientResponse>> {
        return patientRepository.createPatient(request)
    }

    override fun updatePatient(patientId: Int, request: UpdatePatientRequest): Flow<ApiResult<PatientResponse>> {
        return patientRepository.updatePatient(patientId, request)
    }

    override fun deletePatient(patientId: Int): Flow<ApiResult<DeletePatientResponse>> {
        return patientRepository.deletePatient(patientId)
    }

    override fun getCaregivers(): Flow<ApiResult<List<UserListItem>>> {
        return patientRepository.getCaregivers()
    }
}