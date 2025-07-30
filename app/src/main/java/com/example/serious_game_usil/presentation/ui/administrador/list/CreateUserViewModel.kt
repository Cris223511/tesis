package com.example.serious_game_usil.presentation.ui.administrador.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch


class CreateUserViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _rolesState = MutableLiveData<List<Role>>()
    val rolesState: LiveData<List<Role>> = _rolesState

    private val _createUserState = MutableLiveData<CreateUserState>()
    val createUserState: LiveData<CreateUserState> = _createUserState

    fun loadRoles() {
        viewModelScope.launch {
            when (val result = repository.getRoles()) {
                is ApiResult.Success -> {
                    _rolesState.value = result.data
                }
                is ApiResult.Error -> {
                    _rolesState.value = emptyList()
                }
                is ApiResult.NetworkError -> {
                    _rolesState.value = emptyList()
                }
            }
        }
    }

    fun createUser(userData: RegisterRequest) {
        viewModelScope.launch {
            _createUserState.value = CreateUserState.Loading

            when (val result = repository.register(userData)) {
                is ApiResult.Success -> {
                    // Extraemos la contraseña temporal del response
                    val temporalPassword = result.data.user.passwordTemporal
                    _createUserState.value = CreateUserState.Success(temporalPassword)
                }
                is ApiResult.Error -> {
                    _createUserState.value = CreateUserState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _createUserState.value = CreateUserState.Error(
                        "Error de conexión: ${result.exception.message}"
                    )
                }
            }
        }
    }
}

sealed class CreateUserState {
    object Loading : CreateUserState()
    data class Success(val temporalPassword: String) : CreateUserState()
    data class Error(val message: String) : CreateUserState()
}

class CreateUserViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateUserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateUserViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}