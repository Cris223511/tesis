package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.*
import com.example.serious_game_usil.data.ApiResult.*
import com.example.serious_game_usil.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

class CaregiverRepository {

    private val apiService = RetrofitClient.getApiService()

    suspend fun getCaregivers(page: Int = 1, search: String? = null): Flow<ApiResult<CaregiverListResponse>> = flow {
        try {
            // Usar directamente el ID 3 del rol cuidador según la base de datos
            val caregiverRoleId = 3

            // Usar el endpoint de usuarios existente y filtrar por rol de cuidador
            val response = apiService.getUsers(
                search = search,
                page = page,
                roleId = caregiverRoleId
            )

            if (response.isSuccessful) {
                response.body()?.let { usersList ->
                    // Filtrar solo usuarios con rol cuidador (ID 3) del lado cliente como seguridad adicional
                    val filteredUsers = usersList.users.filter { user ->
                        user.roles.any { role -> role.id == caregiverRoleId }
                    }

                    // Obtener pacientes para contar asignaciones (simplificado por rendimiento)
                    val patientsResponse = apiService.getPatients(limit = 200)
                    val allPatients = patientsResponse.body()?.patients ?: emptyList()

                    // Convertir UsersListResponse a CaregiverListResponse
                    val caregivers = filteredUsers.map { user ->
                        Caregiver(
                            id = user.id,
                            nombresApellidos = user.nombresApellidos,
                            correo = user.correo,
                            telefono = user.telefono ?: "",
                            tipoDocumento = user.tipoDocumento,
                            numDocumento = user.numeroDocumento,
                            sexo = user.sexo,
                            fotoMovil = user.fotoMovil ?: "",
                            activo = user.activo,
                            fechaCreacion = user.createdAt,
                            pacientesAsignados = allPatients.count { patient ->
                                patient.cuidadorNombre == user.nombresApellidos
                            },
                            fechaUltimoAcceso = user.updatedAt
                        )
                    }

                    val caregiverResponse = CaregiverListResponse(
                        message = "Lista de cuidadores obtenida exitosamente",
                        caregivers = caregivers,
                        total = usersList.total,
                        page = usersList.page,
                        totalPages = usersList.total_pages,
                        hasPrevious = usersList.page > 1,
                        hasNext = usersList.page < usersList.total_pages
                    )

                    emit(Success(caregiverResponse))
                } ?: emit(Error(500, "No se pudieron cargar los cuidadores"))
            } else {
                emit(Error(response.code(), "Error al cargar cuidadores"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun getCaregiverDetail(caregiverId: Int): Flow<ApiResult<CaregiverDetailResponse>> = flow {
        try {
            val response = apiService.getUserDetail(caregiverId)

            if (response.isSuccessful) {
                response.body()?.let { userDetail ->
                    // Convertir UserDetailResponse a CaregiverDetailResponse
                    val caregiverDetail = CaregiverDetail(
                        id = userDetail.id,
                        nombresApellidos = userDetail.nombresApellidos,
                        correo = userDetail.correo,
                        telefono = userDetail.telefono,
                        tipoDocumento = userDetail.tipoDocumento,
                        numDocumento = userDetail.numeroDocumento,
                        sexo = userDetail.sexo,
                        fotoMovil = userDetail.fotoMovil,
                        activo = userDetail.activo,
                        fechaCreacion = userDetail.createdAt,
                        fechaUltimoAcceso = userDetail.lastLoginAt,
                        pacientesAsignados = emptyList() // Los pacientes se cargan por separado con getCaregiverPatients()
                    )

                    val response = CaregiverDetailResponse(
                        message = "Detalle del cuidador obtenido exitosamente",
                        caregiver = caregiverDetail
                    )

                    emit(Success(response))
                } ?: emit(Error(500, "No se pudo obtener el detalle del cuidador"))
            } else {
                emit(Error(response.code(), "Error al obtener detalle"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun createCaregiver(request: CreateCaregiverRequest): Flow<ApiResult<BaseResponse>> = flow {
        try {
            // Usar directamente el ID 3 del rol cuidador según la base de datos
            val caregiverRoleId = 3

            // Convertir CreateCaregiverRequest a RegisterRequest
            val registerRequest = RegisterRequest(
                nombresApellidos = request.nombresApellidos,
                fechaNacimiento = "1990-01-01", // Campo requerido, usar valor por defecto
                tipoDocumento = request.tipoDocumento,
                numeroDocumento = request.numDocumento,
                sexo = request.sexo,
                telefono = request.telefono ?: "",
                correo = request.correo,
                roleIds = listOf(caregiverRoleId)
            )

            val response = apiService.register(registerRequest)

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    val baseResponse = BaseResponse(
                        message = result.message
                    )
                    emit(Success(baseResponse))
                } ?: emit(Error(500, "Error al crear cuidador"))
            } else {
                emit(Error(response.code(), "Error al crear cuidador"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun updateCaregiver(caregiverId: Int, request: UpdateCaregiverRequest): Flow<ApiResult<BaseResponse>> = flow {
        try {
            // Convertir UpdateCaregiverRequest a UpdateUserRequest
            val updateUserRequest = UpdateUserRequest(
                nombresApellidos = request.nombresApellidos,
                correo = request.correo,
                telefono = request.telefono ?: "",
                tipoDocumento = request.tipoDocumento,
                numeroDocumento = request.numDocumento,
                sexo = request.sexo,
                fechaNacimiento = "1990-01-01", // Campo requerido
                roleIds = emptyList() // Mantener roles existentes
            )

            val response = apiService.updateUser(caregiverId, updateUserRequest)

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    val baseResponse = BaseResponse(
                        message = "Cuidador actualizado exitosamente"
                    )
                    emit(Success(baseResponse))
                } ?: emit(Error(500, "Error al actualizar cuidador"))
            } else {
                emit(Error(response.code(), "Error al actualizar cuidador"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun deleteCaregiver(caregiverId: Int): Flow<ApiResult<BaseResponse>> = flow {
        try {
            val response = apiService.deleteUser(caregiverId)

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    emit(Success(result))
                } ?: emit(Error(500, "Error al eliminar cuidador"))
            } else {
                emit(Error(response.code(), "Error al eliminar cuidador"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun assignPatientToCaregiver(caregiverId: Int, patientId: Int): Flow<ApiResult<BaseResponse>> = flow {
        try {
            // Usar el endpoint de actualización de paciente para asignar cuidador
            val updateRequest = UpdatePatientRequest(cuidadorID = caregiverId)
            val response = apiService.updatePatient(patientId, updateRequest)

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    val baseResponse = BaseResponse(
                        message = "Paciente asignado al cuidador exitosamente"
                    )
                    emit(Success(baseResponse))
                } ?: emit(Error(500, "Error al asignar paciente"))
            } else {
                emit(Error(response.code(), "Error al asignar paciente"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun unassignPatientFromCaregiver(caregiverId: Int, patientId: Int): Flow<ApiResult<BaseResponse>> = flow {
        try {
            // Usar el endpoint de actualización de paciente para desasignar cuidador (cuidadorID = null)
            val updateRequest = UpdatePatientRequest(cuidadorID = null)
            val response = apiService.updatePatient(patientId, updateRequest)

            if (response.isSuccessful) {
                response.body()?.let { result ->
                    val baseResponse = BaseResponse(
                        message = "Paciente desasignado del cuidador exitosamente"
                    )
                    emit(Success(baseResponse))
                } ?: emit(Error(500, "Error al desasignar paciente"))
            } else {
                emit(Error(response.code(), "Error al desasignar paciente"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    suspend fun getCaregiverPatients(caregiverId: Int): Flow<ApiResult<List<PatientListItem>>> = flow {
        try {
            // Primero obtener el nombre del cuidador
            val caregiverResponse = apiService.getUserDetail(caregiverId)
            if (!caregiverResponse.isSuccessful) {
                emit(Error(caregiverResponse.code(), "Error al obtener información del cuidador"))
                return@flow
            }

            val caregiverName = caregiverResponse.body()?.nombresApellidos
            if (caregiverName == null) {
                emit(Error(404, "No se encontró el cuidador"))
                return@flow
            }

            // Obtener todos los pacientes y filtrar por nombre del cuidador
            val patientsResponse = apiService.getPatients(limit = 100)

            if (patientsResponse.isSuccessful) {
                patientsResponse.body()?.let { response ->
                    // Filtrar pacientes que pertenecen al cuidador especificado
                    val caregiverPatients = response.patients?.filter { patient ->
                        patient.cuidadorNombre == caregiverName
                    } ?: emptyList()

                    emit(Success(caregiverPatients))
                } ?: emit(Error(500, "No se pudieron cargar los pacientes del cuidador"))
            } else {
                emit(Error(patientsResponse.code(), "Error al cargar pacientes del cuidador"))
            }
        } catch (e: Exception) {
            emit(NetworkError(e))
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CaregiverRepository? = null

        fun getInstance(): CaregiverRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CaregiverRepository().also { INSTANCE = it }
            }
        }
    }
}