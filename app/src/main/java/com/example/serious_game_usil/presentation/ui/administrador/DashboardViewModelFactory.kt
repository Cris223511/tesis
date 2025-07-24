package com.example.serious_game_usil.presentation.ui.administrador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.repository.ActivityRepository
import com.example.serious_game_usil.repository.UserRepository


class DashboardViewModelFactory(
    private val userRepository: UserRepository,
    private val activityRepository: ActivityRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(userRepository, activityRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}