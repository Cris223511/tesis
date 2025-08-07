package com.example.serious_game_usil.presentation.ui.administrador

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.Activity
import com.example.serious_game_usil.data.User
import com.example.serious_game_usil.repository.ActivityRepository
import com.example.serious_game_usil.repository.UserRepository
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _user = MutableLiveData<User>()
    val user: LiveData<User> = _user

    private val _activities = MutableLiveData<List<Activity>>()
    val activities: LiveData<List<Activity>> = _activities

    private val _stimulusTime = MutableLiveData<Pair<Int, Int>>()
    val stimulusTime: LiveData<Pair<Int, Int>> = _stimulusTime

    private val _pendingTime = MutableLiveData<Pair<Int, Int>>()
    val pendingTime: LiveData<Pair<Int, Int>> = _pendingTime

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Cargar datos del usuario
                val currentUser = userRepository.getCurrentUser()
                _user.value = currentUser

                // Cargar actividades
                val activitiesList = activityRepository.getRecommendedActivities()
                _activities.value = activitiesList

                // Cargar estadísticas
                loadStatistics()

            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadStatistics() {
        // Simular carga de estadísticas
        val todayStats = activityRepository.getTodayStatistics()
        _stimulusTime.value = Pair(todayStats.stimulusHours, todayStats.stimulusMinutes)
        _pendingTime.value = Pair(todayStats.pendingHours, todayStats.pendingMinutes)
    }

    fun refreshData() {
        loadDashboardData()
    }

    fun onActivitySelected(activity: Activity) {
        // Manejar selección de actividad
        viewModelScope.launch {
            // Por ejemplo, registrar el evento
            activityRepository.logActivitySelection(activity.id)
        }
    }

    fun updateNavigationSelection(item: NavigationItem) {
        // Solo manejar la selección localmente
        // No necesita guardar en el repositorio
        when (item) {
            NavigationItem.CHILDREN -> {
                // Lógica para mostrar vista de niños
            }
            NavigationItem.PROFILE -> {
                // Lógica para mostrar perfil
            }
            NavigationItem.INFO -> {
                // Lógica para mostrar información
            }
        }
    }

    enum class NavigationItem {
        CHILDREN, PROFILE, INFO
    }
}