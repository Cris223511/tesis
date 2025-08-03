package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.CreateUserRequest
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UpdatePasswordRequest
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.data.User
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UserSearchParams
import com.example.serious_game_usil.data.UsersListResponse

interface IUserRepository {

    suspend fun getCurrentUser(): User
    suspend fun updateUser(user: User): Boolean
    suspend fun updateLastNavigationItem(item: String)
    suspend fun getLastNavigationItem(): String?
    suspend fun logout(): Boolean
    suspend fun isUserLoggedIn(): Boolean
    suspend fun saveAuthToken(token: String)
    suspend fun getAuthToken(): String?

    suspend fun getUsers(params: UserSearchParams): ApiResult<UsersListResponse>
    suspend fun getUserById(userId: Int): ApiResult<UserDetailResponse>
    suspend fun searchUsers(query: String, limit: Int = 10): ApiResult<List<UserListItem>>
    suspend fun updateUserStatus(userId: Int, isActive: Boolean): ApiResult<BaseResponse>
    suspend fun updateUserData(userId: Int, userData: UpdateUserRequest): ApiResult<UserDetailResponse>
    suspend fun deleteUser(userId: Int): ApiResult<BaseResponse>

    suspend fun getRoles(): ApiResult<List<Role>>
    suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse>

    suspend fun updateUserPassword(userId: Int, request: UpdatePasswordRequest): ApiResult<BaseResponse>

    suspend fun createRole(name: String): ApiResult<Role>
    suspend fun updateRole(roleId: Int, newName: String): ApiResult<BaseResponse>
    suspend fun deleteRole(roleId: Int): ApiResult<BaseResponse>
}