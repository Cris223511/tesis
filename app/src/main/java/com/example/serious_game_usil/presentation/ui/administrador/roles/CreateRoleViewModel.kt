package com.example.serious_game_usil.presentation.ui.administrador.roles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class CreateRoleViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _createRoleState = MutableLiveData<CreateRoleState>()
    val createRoleState: LiveData<CreateRoleState> = _createRoleState

    fun createRole(name: String) {
        viewModelScope.launch {
            _createRoleState.value = CreateRoleState.Loading

            when (val result = repository.createRole(name)) {
                is ApiResult.Success -> {
                    _createRoleState.value = CreateRoleState.Success
                }
                is ApiResult.Error -> {
                    _createRoleState.value = CreateRoleState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _createRoleState.value = CreateRoleState.Error(
                        "Error de conexión: ${result.exception.message}"
                    )
                }
            }
        }
    }

    sealed class CreateRoleState {
        object Loading : CreateRoleState()
        object Success : CreateRoleState()
        data class Error(val message: String) : CreateRoleState()
    }
}