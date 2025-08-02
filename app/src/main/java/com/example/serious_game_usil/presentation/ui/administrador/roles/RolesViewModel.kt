package com.example.serious_game_usil.presentation.ui.administrador.roles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class RolesViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _rolesState = MutableLiveData<RolesState>()
    val rolesState: LiveData<RolesState> = _rolesState

    private val _actionState = MutableLiveData<ActionState>()
    val actionState: LiveData<ActionState> = _actionState

    fun loadRoles() {
        viewModelScope.launch {
            _rolesState.value = RolesState.Loading

            when (val result = repository.getRoles()) {
                is ApiResult.Success -> {
                    _rolesState.value = RolesState.Success(result.data)
                }
                is ApiResult.Error -> {
                    _rolesState.value = RolesState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _rolesState.value = RolesState.Error("Error de conexión")
                }
            }
        }
    }

    fun deleteRole(roleId: Int) {
        viewModelScope.launch {
            _actionState.value = ActionState.Error("Eliminar rol - En desarrollo")
        }
    }

    sealed class RolesState {
        object Loading : RolesState()
        data class Success(val roles: List<Role>) : RolesState()
        data class Error(val message: String) : RolesState()
    }

    sealed class ActionState {
        data class Success(val message: String) : ActionState()
        data class Error(val message: String) : ActionState()
    }
}