package com.example.serious_game_usil.repository

import com.example.serious_game_usil.data.ApiResult
import com.example.serious_game_usil.network.RetrofitClient
import com.example.serious_game_usil.`interface`.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TherapySessionRepository {
    private val apiService = RetrofitClient.getApiService()

    fun getSessions(): Flow<ApiResult<List<TherapySession>>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Fetching all sessions...")
            val response = apiService.getSessions()
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { sessionsResponse ->
                    android.util.Log.d("TherapySessionRepository", "Received ${sessionsResponse.data.size} sessions")
                    emit(ApiResult.Success(sessionsResponse.data))
                } ?: emit(ApiResult.Error(400, "No data received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getSessionsPaginated(
        page: Int = 1,
        limit: Int = 5,
        search: String? = null,
        estado: String? = null
    ): Flow<ApiResult<PaginatedSessionsData>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Fetching paginated sessions - Page: $page, Limit: $limit, Search: $search, Estado: $estado")
            val response = apiService.getSessionsPaginated(page, limit, search, estado)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { paginatedResponse ->
                    android.util.Log.d("TherapySessionRepository", "Received ${paginatedResponse.data.sessions.size} sessions of ${paginatedResponse.data.total} total")
                    emit(ApiResult.Success(paginatedResponse.data))
                } ?: emit(ApiResult.Error(400, "No data received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getSession(sessionId: Int): Flow<ApiResult<TherapySession>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Getting session with ID: $sessionId")

            // Siempre usar el método de obtener desde la lista de todas las sesiones
            // ya que ese método funciona correctamente y tiene los datos del terapeuta
            android.util.Log.d("TherapySessionRepository", "Fetching session from all sessions list...")

            val allSessionsResponse = apiService.getSessions()
            if (allSessionsResponse.isSuccessful) {
                allSessionsResponse.body()?.let { sessionsResponse ->
                    val session = sessionsResponse.data.find { it.id == sessionId }
                    if (session != null) {
                        android.util.Log.d("TherapySessionRepository", "Found session in list: ${session.id}")
                        android.util.Log.d("TherapySessionRepository", "Therapist: ${session.terapeuta.nombresApellidos}")
                        android.util.Log.d("TherapySessionRepository", "Patient: ${session.paciente.nombresApellidos}")
                        emit(ApiResult.Success(session))
                    } else {
                        emit(ApiResult.Error(404, "Session not found"))
                    }
                } ?: emit(ApiResult.Error(400, "No sessions data"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${allSessionsResponse.errorBody()?.string()}")
                emit(ApiResult.Error(allSessionsResponse.code(), "Error fetching sessions list"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getSessionDetail(sessionId: Int): Flow<ApiResult<com.example.serious_game_usil.data.SessionDetail>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Getting session detail for ID: $sessionId")
            val response = apiService.getSession(sessionId)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { sessionDetailResponse ->
                    if (sessionDetailResponse.session != null) {
                        android.util.Log.d("TherapySessionRepository", "Received session detail: ${sessionDetailResponse.session.id}")
                        emit(ApiResult.Success(sessionDetailResponse.session))
                    } else {
                        android.util.Log.e("TherapySessionRepository", "Session detail is null in response, falling back to session list")
                        // Fallback: obtener desde la lista de sesiones y convertir
                        getSessionAsDetail(sessionId).collect { fallbackResult ->
                            emit(fallbackResult)
                        }
                    }
                } ?: run {
                    android.util.Log.e("TherapySessionRepository", "No session detail data received, falling back to session list")
                    // Fallback: obtener desde la lista de sesiones y convertir
                    getSessionAsDetail(sessionId).collect { fallbackResult ->
                        emit(fallbackResult)
                    }
                }
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                android.util.Log.e("TherapySessionRepository", "Falling back to session list")
                // Fallback: obtener desde la lista de sesiones y convertir
                getSessionAsDetail(sessionId).collect { fallbackResult ->
                    emit(fallbackResult)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}, falling back to session list", e)
            // Fallback: obtener desde la lista de sesiones y convertir
            try {
                getSessionAsDetail(sessionId).collect { fallbackResult ->
                    emit(fallbackResult)
                }
            } catch (fallbackException: Exception) {
                android.util.Log.e("TherapySessionRepository", "Fallback also failed: ${fallbackException.message}", fallbackException)
                emit(ApiResult.NetworkError(e))
            }
        }
    }

    private fun getSessionAsDetail(sessionId: Int): Flow<ApiResult<com.example.serious_game_usil.data.SessionDetail>> = flow {
        try {
            val allSessionsResponse = apiService.getSessions()
            if (allSessionsResponse.isSuccessful) {
                allSessionsResponse.body()?.let { sessionsResponse ->
                    val session = sessionsResponse.data.find { it.id == sessionId }
                    if (session != null) {
                        android.util.Log.d("TherapySessionRepository", "Converting TherapySession to SessionDetail")
                        val sessionDetail = convertTherapySessionToSessionDetail(session)
                        emit(ApiResult.Success(sessionDetail))
                    } else {
                        emit(ApiResult.Error(404, "Session not found"))
                    }
                } ?: emit(ApiResult.Error(400, "No sessions data"))
            } else {
                emit(ApiResult.Error(allSessionsResponse.code(), "Error fetching sessions list"))
            }
        } catch (e: Exception) {
            emit(ApiResult.NetworkError(e))
        }
    }

    private fun convertTherapySessionToSessionDetail(session: TherapySession): com.example.serious_game_usil.data.SessionDetail {
        return com.example.serious_game_usil.data.SessionDetail(
            id = session.id,
            nombreSesion = session.tipoSesion ?: "Sesión Terapéutica",
            fechaHora = "${session.fechaSesion}T${session.horaInicio}:00",
            terapeutaNombre = session.terapeuta.nombresApellidos,
            terapeutaTelefono = session.terapeuta.telefono,
            terapeutaCorreo = session.terapeuta.correo,
            pacienteNombre = session.paciente.nombresApellidos,
            pacienteEdad = null, // No disponible en TherapySession
            cuidadorNombre = session.cuidador?.nombresApellidos,
            ubicacion = session.ubicacion,
            direccion = session.direccion,
            descripcion = session.descripcion,
            objetivos = session.objetivos?.joinToString("\n") { "• $it" },
            estado = session.estado,
            duracionMinutos = session.duracion,
            notasTerapeuta = session.notasTerapeuta,
            notasCuidador = null, // No disponible en TherapySession
            materialesNecesarios = session.materiales?.joinToString("\n") { "• $it" },
            createdAt = session.createdAt,
            updatedAt = session.updatedAt
        )
    }

    fun createSession(request: CreateSessionRequest): Flow<ApiResult<TherapySession>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Creating session for patient: ${request.paciente_id}")
            android.util.Log.d("TherapySessionRepository", "Request data: $request")
            val response = apiService.createSession(request)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { sessionResponse ->
                    android.util.Log.d("TherapySessionRepository", "Session created successfully: ${sessionResponse.data.id}")
                    emit(ApiResult.Success(sessionResponse.data))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("TherapySessionRepository", "Error response body: $errorBody")
                android.util.Log.e("TherapySessionRepository", "Error response code: ${response.code()}")
                android.util.Log.e("TherapySessionRepository", "Error response message: ${response.message()}")

                // Intentar parsear el mensaje de error del backend
                val errorMessage = try {
                    val jsonError = org.json.JSONObject(errorBody ?: "{}")
                    jsonError.optString("error", "Error: ${response.code()}")
                } catch (e: Exception) {
                    "Error: ${response.code()} - ${errorBody?.take(200)}"
                }

                emit(ApiResult.Error(response.code(), errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun updateSession(sessionId: Int, request: UpdateSessionRequest): Flow<ApiResult<TherapySession>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Updating session: $sessionId")
            val response = apiService.updateSession(sessionId, request)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { sessionResponse ->
                    android.util.Log.d("TherapySessionRepository", "Session updated successfully: ${sessionResponse.data.id}")
                    emit(ApiResult.Success(sessionResponse.data))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun rescheduleSession(sessionId: Int, request: RescheduleSessionRequest): Flow<ApiResult<TherapySession>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Rescheduling session: $sessionId")
            val response = apiService.rescheduleSession(sessionId, request)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { sessionResponse ->
                    android.util.Log.d("TherapySessionRepository", "Session rescheduled successfully: ${sessionResponse.data.id}")
                    emit(ApiResult.Success(sessionResponse.data))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun deleteSession(sessionId: Int): Flow<ApiResult<String>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Deleting session: $sessionId")
            val response = apiService.deleteSession(sessionId)
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { baseResponse ->
                    android.util.Log.d("TherapySessionRepository", "Session deleted successfully")
                    emit(ApiResult.Success(baseResponse.message))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    fun getAvailableTherapists(): Flow<ApiResult<List<com.example.serious_game_usil.data.UserListItem>>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Fetching available therapists...")
            val response = apiService.getAvailableTherapists()
            android.util.Log.d("TherapySessionRepository", "Response code: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { therapistsResponse ->
                    android.util.Log.d("TherapySessionRepository", "Received ${therapistsResponse.data.size} therapists")
                    emit(ApiResult.Success(therapistsResponse.data))
                } ?: emit(ApiResult.Error(400, "No data received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    // Métodos para exportar (retornan ResponseBody para manejar archivos)
    suspend fun exportSessionToPDF(sessionId: Int): ApiResult<okhttp3.ResponseBody> {
        return try {
            android.util.Log.d("TherapySessionRepository", "Exporting session $sessionId to PDF")
            val response = apiService.exportSessionToPDF(sessionId)

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    ApiResult.Success(body)
                } ?: ApiResult.Error(400, "No response received")
            } else {
                ApiResult.Error(response.code(), "Error: ${response.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            ApiResult.NetworkError(e)
        }
    }

    fun exportSessionToPdf(sessionId: Int): Flow<ApiResult<String>> = flow {
        try {
            android.util.Log.d("TherapySessionRepository", "Exporting session $sessionId to PDF")
            val response = apiService.exportSessionToPDF(sessionId)

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    // Here you would handle file saving, for now just emit success message
                    android.util.Log.d("TherapySessionRepository", "PDF export successful")
                    emit(ApiResult.Success("PDF exportado exitosamente"))
                } ?: emit(ApiResult.Error(400, "No response received"))
            } else {
                android.util.Log.e("TherapySessionRepository", "Error response: ${response.errorBody()?.string()}")
                emit(ApiResult.Error(response.code(), "Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            emit(ApiResult.NetworkError(e))
        }
    }

    suspend fun exportSessionToJPG(sessionId: Int): ApiResult<okhttp3.ResponseBody> {
        return try {
            android.util.Log.d("TherapySessionRepository", "Exporting session $sessionId to JPG")
            val response = apiService.exportSessionToJPG(sessionId)

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    ApiResult.Success(body)
                } ?: ApiResult.Error(400, "No response received")
            } else {
                ApiResult.Error(response.code(), "Error: ${response.code()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TherapySessionRepository", "Exception: ${e.message}", e)
            ApiResult.NetworkError(e)
        }
    }

    private fun convertToTherapySession(sessionDetailResponse: com.example.serious_game_usil.data.SessionDetailResponse): TherapySession {
        val sessionDetail = sessionDetailResponse.session
        return TherapySession(
            id = sessionDetail.id,
            pacienteId = 0, // No disponible en SessionDetail
            terapeutaId = 0, // No disponible en SessionDetail
            fechaSesion = sessionDetail.fechaHora.split("T")[0], // Extraer fecha
            horaInicio = sessionDetail.fechaHora.split("T").getOrElse(1) { "00:00" }.substring(0, 5), // Extraer hora
            horaFin = "", // No disponible
            duracion = sessionDetail.duracionMinutos ?: 60,
            ubicacion = sessionDetail.ubicacion ?: "",
            direccion = sessionDetail.direccion ?: "",
            descripcion = sessionDetail.descripcion ?: "",
            objetivos = sessionDetail.objetivos?.split(",") ?: emptyList(),
            materiales = sessionDetail.materialesNecesarios?.split(",") ?: emptyList(),
            notasTerapeuta = sessionDetail.notasTerapeuta,
            estado = sessionDetail.estado,
            tipoSesion = "Individual", // Valor por defecto
            modalidad = "Presencial", // Valor por defecto
            updateCount = 0,
            createdAt = sessionDetail.createdAt,
            updatedAt = sessionDetail.updatedAt,
            paciente = PacienteInfo(
                id = 0,
                nombresApellidos = sessionDetail.pacienteNombre,
                foto = null
            ),
            terapeuta = TerapeutaInfo(
                id = 0,
                nombresApellidos = sessionDetail.terapeutaNombre,
                correo = sessionDetail.terapeutaCorreo ?: "",
                telefono = sessionDetail.terapeutaTelefono
            ),
            cuidador = null // Por ahora null, se puede mejorar más tarde
        )
    }
}