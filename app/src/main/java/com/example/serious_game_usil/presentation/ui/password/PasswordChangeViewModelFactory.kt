package com.example.serious_game_usil.presentation.ui.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.repository.IUserRepository

class PasswordChangeViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PasswordChangeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PasswordChangeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}