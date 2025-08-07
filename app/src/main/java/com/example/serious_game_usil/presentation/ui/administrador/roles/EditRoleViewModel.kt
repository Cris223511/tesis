package com.example.serious_game_usil.presentation.ui.administrador.roles



import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult

import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class EditRoleViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _updateRoleState = MutableLiveData<UpdateRoleState>()
    val updateRoleState: LiveData<UpdateRoleState> = _updateRoleState

    fun updateRole(roleId: Int, newName: String) {
        viewModelScope.launch {
            _updateRoleState.value = UpdateRoleState.Loading

            when (val result = repository.updateRole(roleId, newName)) {
                is ApiResult.Success -> {
                    _updateRoleState.value = UpdateRoleState.Success
                }
                is ApiResult.Error -> {
                    _updateRoleState.value = UpdateRoleState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _updateRoleState.value = UpdateRoleState.Error(
                        "Error de conexión: ${result.exception.message}"
                    )
                }
            }
        }
    }

    sealed class UpdateRoleState {
        object Loading : UpdateRoleState()
        object Success : UpdateRoleState()
        data class Error(val message: String) : UpdateRoleState()
    }
}

class EditRoleViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditRoleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditRoleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}