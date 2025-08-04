package com.example.serious_game_usil.presentation.ui.administrador.roles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UserSearchParams
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch

class UserRoleViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _usersState = MutableLiveData<UsersState>()
    val usersState: LiveData<UsersState> = _usersState

    private var allUsersWithRole = listOf<UserListItem>()



    fun loadUsersWithRole(roleName: String) {
        viewModelScope.launch {
            _usersState.value = UsersState.Loading

            val params = UserSearchParams(per_page = 100)
            when (val result = repository.getUsers(params)) {
                is ApiResult.Success -> {
                    val usersWithRole = result.data.users.filter { user ->
                        user.roles.any { role ->
                            role.name.equals(roleName, ignoreCase = true)
                        }
                    }

                    allUsersWithRole = usersWithRole
                    _usersState.value = UsersState.Success(usersWithRole)
                }
                is ApiResult.Error -> {
                    _usersState.value = UsersState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _usersState.value = UsersState.Error("Error de conexión")
                }
            }
        }
    }

    fun searchUsersInRole(roleName: String, query: String) {
        viewModelScope.launch {
            _usersState.value = UsersState.Loading

            val filteredUsers = allUsersWithRole.filter { user ->
                user.nombresApellidos.contains(query, ignoreCase = true) ||
                        user.nombreUsuario.contains(query, ignoreCase = true) ||
                        user.correo.contains(query, ignoreCase = true) ||
                        user.numeroDocumento.contains(query, ignoreCase = true)
            }

            _usersState.value = UsersState.Success(filteredUsers)
        }
    }

    sealed class UsersState {
        object Loading : UsersState()
        data class Success(val users: List<UserListItem>) : UsersState()
        data class Error(val message: String) : UsersState()
    }
}

class UserRoleViewModelFactory(
    private val repository: IUserRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserRoleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserRoleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}