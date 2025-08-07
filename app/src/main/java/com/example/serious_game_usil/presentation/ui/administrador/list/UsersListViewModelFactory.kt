package com.example.serious_game_usil.presentation.ui.administrador.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.serious_game_usil.repository.IUserRepository

class UsersListViewModelFactory(
    private val userRepository: IUserRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsersListViewModel::class.java)) {
            return UsersListViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}