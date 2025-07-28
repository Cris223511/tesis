package com.example.serious_game_usil.presentation.ui.administrador.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UserSearchParams
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch



class UsersListViewModel(
    private val userRepository: IUserRepository
) : ViewModel() {

    private val _usersState = MutableLiveData<UsersListState>()
    val usersState: LiveData<UsersListState> = _usersState

    private val _searchState = MutableLiveData<SearchState>()
    val searchState: LiveData<SearchState> = _searchState

    private val _actionState = MutableLiveData<UserActionState>()
    val actionState: LiveData<UserActionState> = _actionState

    private var searchJob: Job? = null

    fun loadUsers(page: Int = 1, perPage: Int = 10, search: String? = null) {
        viewModelScope.launch {
            _usersState.value = UsersListState.Loading

            val params = UserSearchParams(
                search = search,
                page = page,
                per_page = perPage,
                active = null,
                role_id = null,
                order_by = "created_at",
                order_direction = "desc"
            )

            when (val result = userRepository.getUsers(params)) {
                is ApiResult.Success -> {
                    _usersState.value = UsersListState.Success(
                        users = result.data.users,
                        totalPages = result.data.total_pages,
                        currentPage = result.data.page,
                        total = result.data.total
                    )
                }
                is ApiResult.Error -> {
                    _usersState.value = UsersListState.Error(
                        "Error al cargar usuarios: ${result.message}"
                    )
                }
                is ApiResult.NetworkError -> {
                    _usersState.value = UsersListState.Error(
                        "Sin conexión a internet. Verifica tu conexión."
                    )
                }
            }
        }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _searchState.value = SearchState.Loading

            when (val result = userRepository.searchUsers(query, limit = 50)) {
                is ApiResult.Success -> {
                    _searchState.value = SearchState.Success(result.data)
                }
                is ApiResult.Error -> {
                    _searchState.value = SearchState.Error(
                        "Error al buscar: ${result.message}"
                    )
                }
                is ApiResult.NetworkError -> {
                    _searchState.value = SearchState.Error(
                        "Sin conexión a internet"
                    )
                }
            }
        }
    }

    fun toggleUserStatus(user: UserListItem) {
        viewModelScope.launch {
            when (val result = userRepository.updateUserStatus(user.id, !user.activo)) {
                is ApiResult.Success -> {
                    val action = if (!user.activo) "activado" else "desactivado"
                    _actionState.value = UserActionState.Success(
                        "Usuario $action correctamente"
                    )
                }
                is ApiResult.Error -> {
                    _actionState.value = UserActionState.Error(
                        "Error al cambiar estado: ${result.message}"
                    )
                }
                is ApiResult.NetworkError -> {
                    _actionState.value = UserActionState.Error(
                        "Sin conexión a internet"
                    )
                }
            }
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            when (val result = userRepository.deleteUser(userId)) {
                is ApiResult.Success -> {
                    _actionState.value = UserActionState.Success(
                        "Usuario eliminado correctamente"
                    )
                }
                is ApiResult.Error -> {
                    _actionState.value = UserActionState.Error(
                        "Error al eliminar: ${result.message}"
                    )
                }
                is ApiResult.NetworkError -> {
                    _actionState.value = UserActionState.Error(
                        "Sin conexión a internet"
                    )
                }
            }
        }
    }
}

sealed class UsersListState {
    object Loading : UsersListState()
    data class Success(
        val users: List<UserListItem>,
        val totalPages: Int,
        val currentPage: Int,
        val total: Int
    ) : UsersListState()
    data class Error(val message: String) : UsersListState()
}

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val users: List<UserListItem>) : SearchState()
    data class Error(val message: String) : SearchState()
}

sealed class UserActionState {
    object Idle : UserActionState()
    data class Success(val message: String) : UserActionState()
    data class Error(val message: String) : UserActionState()
}