package com.example.serious_game_usil.presentation.ui.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.PatientsListResponse
import com.example.serious_game_usil.data.PatientResponse
import com.example.serious_game_usil.data.DeletePatientResponse
import com.example.serious_game_usil.data.CreatePatientRequest
import com.example.serious_game_usil.data.UpdatePatientRequest
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.service.IPatientService
import com.example.serious_game_usil.service.PatientServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PatientsViewModel(
    private val patientService: IPatientService
) : ViewModel() {

    private val _patients = MutableStateFlow<ApiResult<PatientsListResponse>>(ApiResult.Success(PatientsListResponse(emptyList())))
    val patients: StateFlow<ApiResult<PatientsListResponse>> = _patients.asStateFlow()

    private val _selectedPatient = MutableStateFlow<ApiResult<com.example.serious_game_usil.data.Patient>?>(null)
    val selectedPatient: StateFlow<ApiResult<com.example.serious_game_usil.data.Patient>?> = _selectedPatient.asStateFlow()

    private val _createPatientResult = MutableStateFlow<ApiResult<PatientResponse>?>(null)
    val createPatientResult: StateFlow<ApiResult<PatientResponse>?> = _createPatientResult.asStateFlow()

    private val _caregivers = MutableStateFlow<ApiResult<List<UserListItem>>>(ApiResult.Success(emptyList()))
    val caregivers: StateFlow<ApiResult<List<UserListItem>>> = _caregivers.asStateFlow()

    private var currentPage = 1
    private var currentSearch: String? = null
    private val pageSize = 10

    fun loadPatients(page: Int = 1, search: String? = null) {
        currentPage = page
        currentSearch = search
        viewModelScope.launch {
            patientService.getPatients(page, pageSize, search).collect { result ->
                _patients.value = result
            }
        }
    }

    fun loadNextPage() {
        val currentResult = _patients.value
        if (currentResult is ApiResult.Success) {
            if (currentResult.data.has_next == true) {
                loadPatients(currentPage + 1, currentSearch)
            }
        }
    }

    fun loadPreviousPage() {
        val currentResult = _patients.value
        if (currentResult is ApiResult.Success) {
            if (currentResult.data.has_previous == true && currentPage > 1) {
                loadPatients(currentPage - 1, currentSearch)
            }
        }
    }

    fun searchPatients(query: String?) {
        loadPatients(1, query)
    }

    fun loadPatient(patientId: Int) {
        android.util.Log.d("PatientsViewModel", "loadPatient called with ID: $patientId")
        viewModelScope.launch {
            patientService.getPatient(patientId).collect { result ->
                android.util.Log.d("PatientsViewModel", "Patient service result: $result")
                _selectedPatient.value = result
            }
        }
    }

    fun createPatient(request: CreatePatientRequest) {
        viewModelScope.launch {
            patientService.createPatient(request).collect { result ->
                _createPatientResult.value = result
                if (result is ApiResult.Success) {
                    loadPatients() // Refresh the list
                }
            }
        }
    }

    fun updatePatient(patientId: Int, request: UpdatePatientRequest) {
        viewModelScope.launch {
            patientService.updatePatient(patientId, request).collect { result ->
                _createPatientResult.value = result
                if (result is ApiResult.Success) {
                    loadPatients() // Refresh the list
                }
            }
        }
    }

    fun deletePatient(patientId: Int): kotlinx.coroutines.flow.Flow<ApiResult<DeletePatientResponse>> {
        return patientService.deletePatient(patientId)
    }

    fun loadCaregivers() {
        viewModelScope.launch {
            patientService.getCaregivers().collect { result ->
                _caregivers.value = result
            }
        }
    }

    fun clearSelectedPatient() {
        _selectedPatient.value = null
    }

    fun clearCreateResult() {
        _createPatientResult.value = null
    }
}

class PatientsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PatientsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PatientsViewModel(PatientServiceImpl()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}