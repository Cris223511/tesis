package com.example.serious_game_usil.presentation.ui.administrador.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UpdatePasswordRequest
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch


class EditUserViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _userState = MutableLiveData<UserDetailResponse?>()
    val userState: LiveData<UserDetailResponse?> = _userState

    private val _rolesState = MutableLiveData<List<Role>>()
    val rolesState: LiveData<List<Role>> = _rolesState

    private val _updateState = MutableLiveData<EditUserState>()
    val updateState: LiveData<EditUserState> = _updateState

    fun loadUserData(userId: Int) {
        viewModelScope.launch {
            when (val result = repository.getUserById(userId)) {
                is ApiResult.Success -> {
                    _userState.value = result.data
                }
                is ApiResult.Error -> {
                    _updateState.value = EditUserState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _updateState.value = EditUserState.Error(
                        "Error de conexión: ${result.exception.message}"
                    )
                }
            }
        }
    }

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

    fun updateUser(userId: Int, userData: UpdateUserRequest, newPassword: String?) {
        viewModelScope.launch {
            _updateState.value = EditUserState.Loading

            // Primero actualizar datos del usuario
            when (val result = repository.updateUserData(userId, userData)) {
                is ApiResult.Success -> {
                    if (newPassword != null) {
                        // Si hay nueva contraseña, actualizarla también
                        updatePassword(userId, newPassword)
                    } else {
                        // Si no hay cambio de contraseña, terminamos aquí
                        _updateState.value = EditUserState.Success(null)
                    }
                }
                is ApiResult.Error -> {
                    _updateState.value = EditUserState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _updateState.value = EditUserState.Error(
                        "Error de conexión: ${result.exception.message}"
                    )
                }
            }
        }
    }

    private suspend fun updatePassword(userId: Int, newPassword: String) {
        val passwordRequest = UpdatePasswordRequest(
            newPassword = newPassword,
            confirmPassword = newPassword
        )

        when (val result = repository.updateUserPassword(userId, passwordRequest)) {
            is ApiResult.Success -> {
                _updateState.value = EditUserState.Success(newPassword)
            }
            is ApiResult.Error -> {
                // Si falla el cambio de contraseña, pero los datos se actualizaron
                // mostramos un mensaje parcial
                _updateState.value = EditUserState.Error(
                    "Datos actualizados, pero error al cambiar contraseña: ${result.message}"
                )
            }
            is ApiResult.NetworkError -> {
                _updateState.value = EditUserState.Error(
                    "Datos actualizados, pero error de conexión al cambiar contraseña"
                )
            }
        }
    }
}

sealed class EditUserState {
    object Loading : EditUserState()
    data class Success(val newPassword: String?) : EditUserState()
    data class Error(val message: String) : EditUserState()
}

class EditUserViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditUserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditUserViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}