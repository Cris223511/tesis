package com.example.serious_game_usil.presentation.ui.administrador.roles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UserSearchParams
import com.example.serious_game_usil.repository.IUserRepository
import kotlinx.coroutines.launch



class RolesViewModel(
    private val repository: IUserRepository
) : ViewModel() {

    private val _rolesState = MutableLiveData<RolesState>()
    val rolesState: LiveData<RolesState> = _rolesState

    private val _searchState = MutableLiveData<SearchState>()
    val searchState: LiveData<SearchState> = _searchState

    private val _actionState = MutableLiveData<ActionState>()
    val actionState: LiveData<ActionState> = _actionState

    private val _roleUsersState = MutableLiveData<RoleUsersState>()
    val roleUsersState: LiveData<RoleUsersState> = _roleUsersState

    private var allRoles = listOf<Role>()
    private var allUsers = listOf<UserListItem>()
    private var currentPage = 1
    private val rolesPerPage = 10

    fun loadRoles(page: Int = 1) {
        viewModelScope.launch {
            _rolesState.value = RolesState.Loading
            currentPage = page

            when (val result = repository.getRoles()) {
                is ApiResult.Success -> {
                    allRoles = result.data.filterNot { role ->
                        role.name.contains("DELETED_", ignoreCase = true)
                    }

                    loadUsersForCounting()

                    val paginatedRoles = getPaginatedRoles(allRoles, page)

                    _rolesState.value = RolesState.Success(
                        roles = paginatedRoles,
                        totalRoles = allRoles.size,
                        currentPage = page,
                        totalPages = calculateTotalPages(allRoles.size)
                    )
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

    private suspend fun loadUsersForCounting() {
        val params = UserSearchParams(per_page = 100)
        when (val result = repository.getUsers(params)) {
            is ApiResult.Success -> {
                allUsers = result.data.users
            }
            else -> {
                allUsers = emptyList()
            }
        }
    }

    fun getUserCountForRole(roleName: String): Int {
        return allUsers.count { user ->
            user.roles.any { role ->
                role.name.equals(roleName, ignoreCase = true)
            }
        }
    }

    fun getUsersWithRole(roleName: String) {
        viewModelScope.launch {
            _roleUsersState.value = RoleUsersState.Loading

            val usersWithRole = allUsers.filter { user ->
                user.roles.any { role ->
                    role.name.equals(roleName, ignoreCase = true)
                }
            }

            _roleUsersState.value = RoleUsersState.Success(
                roleName = roleName,
                users = usersWithRole
            )
        }
    }

    fun searchRoles(query: String) {
        if (query.isBlank()) {
            loadRoles(1)
            return
        }

        viewModelScope.launch {
            _searchState.value = SearchState.Loading

            val filteredRoles = allRoles.filter { role ->
                !role.name.contains("DELETED_", ignoreCase = true) &&
                        role.name.contains(query, ignoreCase = true)
            }

            val paginatedRoles = getPaginatedRoles(filteredRoles, 1)

            _searchState.value = SearchState.Success(
                roles = paginatedRoles,
                query = query,
                totalResults = filteredRoles.size,
                currentPage = 1,
                totalPages = calculateTotalPages(filteredRoles.size)
            )
        }
    }

    fun deleteRole(roleId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteRole(roleId)) {
                is ApiResult.Success -> {
                    _actionState.value = ActionState.Success("Rol eliminado exitosamente")
                }
                is ApiResult.Error -> {
                    _actionState.value = ActionState.Error(result.message)
                }
                is ApiResult.NetworkError -> {
                    _actionState.value = ActionState.Error("Error de conexión")
                }
            }
        }
    }

    private fun getPaginatedRoles(roles: List<Role>, page: Int): List<Role> {
        val startIndex = (page - 1) * rolesPerPage
        val endIndex = minOf(startIndex + rolesPerPage, roles.size)

        return if (startIndex < roles.size) {
            roles.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    private fun calculateTotalPages(totalRoles: Int): Int {
        return if (totalRoles == 0) 1 else (totalRoles + rolesPerPage - 1) / rolesPerPage
    }

    sealed class RolesState {
        object Loading : RolesState()
        data class Success(
            val roles: List<Role>,
            val totalRoles: Int,
            val currentPage: Int,
            val totalPages: Int
        ) : RolesState()
        data class Error(val message: String) : RolesState()
    }

    sealed class SearchState {
        object Loading : SearchState()
        data class Success(
            val roles: List<Role>,
            val query: String,
            val totalResults: Int,
            val currentPage: Int,
            val totalPages: Int
        ) : SearchState()
        data class Error(val message: String) : SearchState()
    }

    sealed class ActionState {
        data class Success(val message: String) : ActionState()
        data class Error(val message: String) : ActionState()
    }

    sealed class RoleUsersState {
        object Loading : RoleUsersState()
        data class Success(
            val roleName: String,
            val users: List<UserListItem>
        ) : RoleUsersState()
        data class Error(val message: String) : RoleUsersState()
    }
}