package com.example.serious_game_usil.presentation.ui.administrador.roles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.repository.IUserRepository

class CreateRoleViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateRoleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateRoleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}