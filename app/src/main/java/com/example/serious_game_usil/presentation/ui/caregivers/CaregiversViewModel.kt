package com.example.serious_game_usil.presentation.ui.caregivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.CaregiverDetailResponse
import com.example.serious_game_usil.data.CaregiverListResponse
import com.example.serious_game_usil.data.CreateCaregiverRequest
import com.example.serious_game_usil.data.UpdateCaregiverRequest
import com.example.serious_game_usil.repository.CaregiverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaregiversViewModel(
    private val caregiverRepository: CaregiverRepository
) : ViewModel() {

    private val _caregivers = MutableStateFlow<ApiResult<CaregiverListResponse>>(ApiResult.Success(CaregiverListResponse("", emptyList(), 0, 1, 0, false, false)))
    val caregivers: StateFlow<ApiResult<CaregiverListResponse>> = _caregivers.asStateFlow()

    private val _caregiverDetail = MutableStateFlow<ApiResult<CaregiverDetailResponse>?>(null)
    val caregiverDetail: StateFlow<ApiResult<CaregiverDetailResponse>?> = _caregiverDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPage = 1
    private var currentSearchQuery: String? = null

    fun loadCaregivers(page: Int = 1) {
        viewModelScope.launch {
            _isLoading.value = true
            currentPage = page
            caregiverRepository.getCaregivers(page, currentSearchQuery).collect { result ->
                _caregivers.value = result
                _isLoading.value = false
            }
        }
    }

    fun searchCaregivers(query: String?) {
        currentSearchQuery = query
        currentPage = 1
        loadCaregivers(currentPage)
    }

    fun loadNextPage() {
        loadCaregivers(currentPage + 1)
    }

    fun loadPreviousPage() {
        if (currentPage > 1) {
            loadCaregivers(currentPage - 1)
        }
    }

    fun getCaregiverDetail(caregiverId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            caregiverRepository.getCaregiverDetail(caregiverId).collect { result ->
                _caregiverDetail.value = result
                _isLoading.value = false
            }
        }
    }

    suspend fun createCaregiver(request: CreateCaregiverRequest) =
        caregiverRepository.createCaregiver(request)

    suspend fun updateCaregiver(caregiverId: Int, request: UpdateCaregiverRequest) =
        caregiverRepository.updateCaregiver(caregiverId, request)

    suspend fun deleteCaregiver(caregiverId: Int) =
        caregiverRepository.deleteCaregiver(caregiverId)

    suspend fun assignPatientToCaregiver(caregiverId: Int, patientId: Int) =
        caregiverRepository.assignPatientToCaregiver(caregiverId, patientId)

    suspend fun unassignPatientFromCaregiver(caregiverId: Int, patientId: Int) =
        caregiverRepository.unassignPatientFromCaregiver(caregiverId, patientId)
}

class CaregiversViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaregiversViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CaregiversViewModel(CaregiverRepository.getInstance()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}