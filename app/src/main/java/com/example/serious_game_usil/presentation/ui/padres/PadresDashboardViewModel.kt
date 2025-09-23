package com.example.serious_game_usil.presentation.ui.padres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.PatientListItem
import com.example.serious_game_usil.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PadresDashboardViewModel : ViewModel() {
    private val patientRepository = PatientRepository()

    private val _myPatients = MutableStateFlow<List<PatientListItem>>(emptyList())
    val myPatients: StateFlow<List<PatientListItem>> = _myPatients.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPatientIndex = MutableStateFlow(0)
    val currentPatientIndex: StateFlow<Int> = _currentPatientIndex.asStateFlow()

    fun loadMyPatients() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                patientRepository.getPatients(page = 1, limit = 100).collect { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val patients = result.data.patients ?: emptyList()
                            _myPatients.value = patients
                            if (patients.isNotEmpty() && _currentPatientIndex.value >= patients.size) {
                                _currentPatientIndex.value = 0
                            }
                        }
                        is ApiResult.Error -> {
                            android.util.Log.e("PadresDashboard", "Error loading patients: ${result.message}")
                        }
                        is ApiResult.NetworkError -> {
                            android.util.Log.e("PadresDashboard", "Network error loading patients", result.exception)
                        }
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                android.util.Log.e("PadresDashboard", "Exception loading patients", e)
                _isLoading.value = false
            }
        }
    }

    fun getCurrentPatient(): PatientListItem? {
        val patients = _myPatients.value
        return if (patients.isNotEmpty() && _currentPatientIndex.value < patients.size) {
            patients[_currentPatientIndex.value]
        } else null
    }

    fun nextPatient() {
        val patients = _myPatients.value
        if (patients.isNotEmpty()) {
            _currentPatientIndex.value = (_currentPatientIndex.value + 1) % patients.size
        }
    }

    fun previousPatient() {
        val patients = _myPatients.value
        if (patients.isNotEmpty()) {
            _currentPatientIndex.value = if (_currentPatientIndex.value > 0) {
                _currentPatientIndex.value - 1
            } else {
                patients.size - 1
            }
        }
    }

    fun setCurrentPatientIndex(index: Int) {
        val patients = _myPatients.value
        if (index in 0 until patients.size) {
            _currentPatientIndex.value = index
        }
    }
}