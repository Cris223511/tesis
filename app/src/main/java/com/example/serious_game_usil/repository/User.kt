package com.example.serious_game_usil.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.CreateRoleRequest
import com.example.serious_game_usil.data.CreateUserRequest
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UpdatePasswordRequest
import com.example.serious_game_usil.data.UpdateRoleRequest
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.data.UpdateUserStatusRequest
import com.example.serious_game_usil.data.User
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UserSearchParams
import com.example.serious_game_usil.data.UsersListResponse
import com.example.serious_game_usil.guards.AuthManager
import com.example.serious_game_usil.`interface`.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

class UserRepository private constructor(private val context: Context) : IUserRepository {

    private val apiService: ApiService by lazy {
        RetrofitClient.getApiService()
    }

    companion object {
        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }


    override suspend fun getCurrentUser(): User {
        return User(
            id = AuthManager.getUserId(),
            nombresApellidos = AuthManager.getNombresApellidos(),
            correo = AuthManager.getCorreo(),
            telefono = AuthManager.getTelefono(),
            tipoDocumento = AuthManager.getTipoDocumento(),
            numDocumento = AuthManager.getNumDocumento(),
            sexo = AuthManager.getSexo(),
            foto = AuthManager.getFoto(),
            roles = AuthManager.getUserRoles()
        )
    }


    override suspend fun getUsers(params: UserSearchParams): ApiResult<UsersListResponse> {
        return try {
            Log.d("UserRepository", "Llamando a getUsers con params: $params")

            val response = apiService.getUsers(
                search = params.search,
                page = params.page,
                perPage = params.per_page,
                active = params.active,
                roleId = params.role_id,
                orderBy = params.order_by,
                orderDirection = params.order_direction
            )

            Log.d("UserRepository", "Response code: ${response.code()}")
            Log.d("UserRepository", "Response body: ${response.body()}")

            if (response.isSuccessful) {
                response.body()?.let { usersListResponse ->
                    Log.d("UserRepository", "Response recibido: $usersListResponse")
                    Log.d("UserRepository", "Usuarios recibidos: ${usersListResponse.users.size}")
                    Log.d("UserRepository", "Total: ${usersListResponse.total}")
                    Log.d("UserRepository", "Página: ${usersListResponse.page}")

                    ApiResult.Success(usersListResponse)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("UserRepository", "Error response: $errorBody")
                ApiResult.Error(response.code(), errorBody)
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Exception en getUsers", e)
            ApiResult.NetworkError(e)
        }
    }

    override suspend fun getUserById(userId: Int): ApiResult<UserDetailResponse> {
        return try {
            val response = apiService.getUserDetail(userId)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun searchUsers(query: String, limit: Int): ApiResult<List<UserListItem>> {
        return try {
            val response = apiService.searchUsers(query, limit)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }


    override suspend fun updateUserStatus(userId: Int, isActive: Boolean): ApiResult<BaseResponse> {
        return try {
            val response = apiService.updateUserStatus(
                userId,
                UpdateUserStatusRequest(isActive)  // Solo envía el campo activo
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun updateUserData(
        userId: Int,
        userData: UpdateUserRequest
    ): ApiResult<UserDetailResponse> {
        return try {
            val response = apiService.updateUser(userId, userData)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun deleteUser(userId: Int): ApiResult<BaseResponse> {
        return try {
            val response = apiService.deleteUser(userId)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    // Implementación de los métodos existentes que faltan
    override suspend fun updateUser(user: User): Boolean {
        // Implementar según tu lógica
        return true
    }

    override suspend fun updateLastNavigationItem(item: String) {
        context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_nav_item", item)
            .apply()
    }

    override suspend fun getLastNavigationItem(): String? {
        return context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .getString("last_nav_item", null)
    }

    override suspend fun logout(): Boolean {
        return try {
            AuthManager.clearSession()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun isUserLoggedIn(): Boolean {
        return AuthManager.isAuthenticated()
    }

    override suspend fun saveAuthToken(token: String) {
        AuthManager.updateAccessToken(token)
    }

    override suspend fun getAuthToken(): String? {
        return AuthManager.getAccessToken()
    }


    override suspend fun getRoles(): ApiResult<List<Role>> {
        return try {
            val response = apiService.getRoles()

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> {
        return try {
            val response = apiService.register(request)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }


    override suspend fun updateUserPassword(
        userId: Int,
        request: UpdatePasswordRequest
    ): ApiResult<BaseResponse> {
        return try {
            val response = apiService.updateUserPassword(userId, request)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                ApiResult.Error(
                    response.code(),
                    response.errorBody()?.string() ?: "Unknown error"
                )
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }


    override suspend fun createRole(name: String): ApiResult<Role> {
        return try {
            val response = apiService.createRole(CreateRoleRequest(name))

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                val errorBody = response.errorBody()?.string()
                ApiResult.Error(response.code(), errorBody ?: "Unknown error")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun updateRole(roleId: Int, newName: String): ApiResult<BaseResponse> {
        return try {
            val response = apiService.updateRole(roleId, UpdateRoleRequest(newName))

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                val errorBody = response.errorBody()?.string()
                ApiResult.Error(response.code(), errorBody ?: "Unknown error")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }

    override suspend fun deleteRole(roleId: Int): ApiResult<BaseResponse> {
        return try {
            val response = apiService.deleteRole(roleId)

            if (response.isSuccessful) {
                response.body()?.let {
                    ApiResult.Success(it)
                } ?: ApiResult.Error(response.code(), "Response body is null")
            } else {
                val errorBody = response.errorBody()?.string()
                ApiResult.Error(response.code(), errorBody ?: "Unknown error")
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            ApiResult.Error(-1, e.message ?: "Unknown error")
        }
    }
}