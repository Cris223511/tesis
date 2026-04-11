package com.example.serious_game_usil.`interface`

import com.google.gson.annotations.SerializedName
import com.example.serious_game_usil.data.BaseResponse
import com.example.serious_game_usil.data.Caregiver
import com.example.serious_game_usil.data.CaregiverDetailResponse
import com.example.serious_game_usil.data.CaregiverListResponse
import com.example.serious_game_usil.data.CreateCaregiverRequest
import com.example.serious_game_usil.data.CreateRoleRequest
import com.example.serious_game_usil.data.LoginRequest
import com.example.serious_game_usil.data.LoginResponse
import com.example.serious_game_usil.data.OTPRequest
import com.example.serious_game_usil.data.OTPResponse
import com.example.serious_game_usil.data.RegisterRequest
import com.example.serious_game_usil.data.RegisterResponse
import com.example.serious_game_usil.data.ResendOTPRequest
import com.example.serious_game_usil.data.Role
import com.example.serious_game_usil.data.UpdateCaregiverRequest
import com.example.serious_game_usil.data.UpdatePasswordRequest
import com.example.serious_game_usil.data.UpdateRoleRequest
import com.example.serious_game_usil.data.UpdateUserRequest
import com.example.serious_game_usil.data.UpdateUserStatusRequest
import com.example.serious_game_usil.data.UserDetailResponse
import com.example.serious_game_usil.data.UserListItem
import com.example.serious_game_usil.data.UsersListResponse
import com.example.serious_game_usil.data.UserProfileResponse
import com.example.serious_game_usil.data.ChildrenResponse
import com.example.serious_game_usil.data.ThreeMonthComparison
import com.example.serious_game_usil.data.CreatePatientRequest
import com.example.serious_game_usil.data.UpdatePatientRequest
import com.example.serious_game_usil.data.Patient
import com.example.serious_game_usil.data.PatientResponse
import com.example.serious_game_usil.data.PatientsListResponse
import com.example.serious_game_usil.data.DeletePatientResponse
import com.example.serious_game_usil.data.RefreshTokenRequest
import com.example.serious_game_usil.data.RefreshTokenResponse
import com.example.serious_game_usil.data.SessionDetailResponse
import com.example.serious_game_usil.data.PatientStatsApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/otp/validate")
    suspend fun verifyOtp(@Body request: OTPRequest): Response<OTPResponse>

    @POST("/api/otp/resend")
    suspend fun resendOtp(@Body request: ResendOTPRequest): Response<BaseResponse>


    @GET("api/users")
    suspend fun getUsers(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("active") active: Boolean? = null,
        @Query("role_id") roleId: Int? = null,
        @Query("order_by") orderBy: String = "created_at",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<UsersListResponse>

    @GET("/api/users/{id}")
    suspend fun getUserDetail(@Path("id") userId: Int): Response<UserDetailResponse>


    @PUT("api/users/{id}")
    suspend fun updateUser(
        @Path("id") userId: Int,
        @Body request: UpdateUserRequest
    ): Response<UserDetailResponse>


    @PATCH("api/users/{id}/status")
    suspend fun updateUserStatus(
        @Path("id") userId: Int,
        @Body request: UpdateUserStatusRequest
    ): Response<BaseResponse>

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") userId: Int): Response<BaseResponse>


    @PUT("api/users/{userId}/password")
    suspend fun updateUserPassword(
        @Path("userId") userId: Int,
        @Body request: UpdatePasswordRequest
    ): Response<BaseResponse>


    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<UserListItem>>

    @GET("api/roles")
    suspend fun getRoles(): Response<List<Role>>

    @POST("api/roles")
    suspend fun createRole(@Body request: CreateRoleRequest): Response<Role>

    @PUT("api/roles/{id}")
    suspend fun updateRole(
        @Path("id") roleId: Int,
        @Body request: UpdateRoleRequest
    ): Response<BaseResponse>

    @DELETE("api/roles/{id}")
    suspend fun deleteRole(@Path("id") roleId: Int): Response<BaseResponse>

    @GET("/auth/gett")
    suspend fun getToken(): Response<TokenResponse>
    
    @GET("api/profile")
    suspend fun getCurrentUserProfile(): Response<UserProfileResponse>
    
    @GET("api/profile/{id}")
    suspend fun getUserProfile(@Path("id") userId: Int): Response<UserProfileResponse>
    
    @GET("api/profile/children")
    suspend fun getUserChildren(): Response<ChildrenResponse>
    
    @POST("api/users/photo")
    suspend fun uploadUserPhoto(@Body request: UploadPhotoRequest): Response<UploadPhotoResponse>
    
    @GET("api/users/photo/changes")
    suspend fun getPhotoChanges(): Response<PhotoChangesResponse>
    
    @POST("api/users/banner")
    suspend fun uploadUserBanner(@Body request: UploadBannerRequest): Response<UploadBannerResponse>
    
    @GET("api/users/banner/changes")
    suspend fun getBannerChanges(): Response<BannerChangesResponse>
    
    @POST("api/password/validate-email")
    suspend fun validateEmailForPasswordChange(@Body request: Map<String, String>): Response<BaseResponse>
    
    @POST("api/password/send-otp")
    suspend fun sendPasswordChangeOTP(@Body request: Map<String, String>): Response<com.example.serious_game_usil.data.PasswordChangeOTPResponse>
    
    @POST("api/password/verify-otp")
    suspend fun verifyPasswordOTP(@Body request: Map<String, String>): Response<BaseResponse>
    
    @POST("api/password/change-with-otp")
    suspend fun changePasswordWithOTP(@Body request: Map<String, String>): Response<BaseResponse>
    
    @PUT("api/profile/update")
    suspend fun updateProfileData(@Body request: Map<String, String>): Response<UserProfileResponse>
    
    @GET("api/profile/changes")
    suspend fun getProfileChanges(): Response<ProfileChangesResponse>
    
    // ============== PATIENT MANAGEMENT ENDPOINTS ==============
    @GET("api/patients")
    suspend fun getPatients(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("search") search: String? = null
    ): Response<PatientsListResponse>

    @GET("api/patients/{id}")
    suspend fun getPatient(@Path("id") patientId: Int): Response<Patient>

    @POST("api/patients")
    suspend fun createPatient(@Body request: CreatePatientRequest): Response<PatientResponse>

    @PUT("api/patients/{id}")
    suspend fun updatePatient(
        @Path("id") patientId: Int,
        @Body request: UpdatePatientRequest
    ): Response<PatientResponse>

    @DELETE("api/patients/{id}")
    suspend fun deletePatient(@Path("id") patientId: Int): Response<DeletePatientResponse>

    @GET("api/patients/{id}/stats")
    suspend fun getPatientStats(@Path("id") patientId: Int): Response<PatientStatsApiResponse>

    // ============== THERAPY SESSION ENDPOINTS ==============
    @GET("api/sessions/latest-patients")
    suspend fun getLatestPatients(): Response<LatestPatientsResponse>

    @GET("api/sessions")
    suspend fun getSessions(): Response<SessionsListResponse>

    @GET("api/sessions/paginated")
    suspend fun getSessionsPaginated(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 5,
        @Query("search") search: String? = null,
        @Query("estado") estado: String? = null
    ): Response<PaginatedSessionsResponse>

    @GET("api/sessions/{id}")
    suspend fun getSession(@Path("id") sessionId: Int): Response<SessionDetailResponse>

    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Response<SessionResponse>

    @PUT("api/sessions/{id}")
    suspend fun updateSession(
        @Path("id") sessionId: Int,
        @Body request: UpdateSessionRequest
    ): Response<SessionResponse>

    @PUT("api/sessions/{id}/reschedule")
    suspend fun rescheduleSession(
        @Path("id") sessionId: Int,
        @Body request: RescheduleSessionRequest
    ): Response<SessionResponse>

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(@Path("id") sessionId: Int): Response<BaseResponse>

    @GET("api/sessions/{id}/export/pdf")
    suspend fun exportSessionToPDF(@Path("id") sessionId: Int): Response<okhttp3.ResponseBody>

    @GET("api/sessions/{id}/export/jpg")
    suspend fun exportSessionToJPG(@Path("id") sessionId: Int): Response<okhttp3.ResponseBody>

    @GET("api/sessions/rating/{session_id}")
    suspend fun getSessionRating(@Path("session_id") sessionId: Int): Response<TherapistRatingResponse>

    @GET("api/sessions/available-therapists")
    suspend fun getAvailableTherapists(): Response<TherapistsListResponse>

    @GET("api/autism/children/{child_id}/progress/3months")
    suspend fun getThreeMonthComparison(@Path("child_id") childId: Int): Response<ThreeMonthComparison>


    // Caregiver endpoints
    @GET("api/caregivers")
    suspend fun getCaregivers(@Query("page") page: Int = 1): Response<CaregiverListResponse>

    // Caregiver's own patients
    @GET("api/caregiver/my-patients")
    suspend fun getMyPatients(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): Response<PatientsListResponse>

    @GET("api/caregiver/my-patients/{patient_id}/sessions")
    suspend fun getPatientSessions(
        @Path("patient_id") patientId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10
    ): Response<TherapySessionsResponse>

    @GET("api/caregiver/my-profile")
    suspend fun getCaregiverProfile(): Response<CaregiverDetailResponse>

    @GET("api/caregivers/search")
    suspend fun searchCaregivers(
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): Response<CaregiverListResponse>

    @GET("api/caregivers/{id}")
    suspend fun getCaregiverDetail(@Path("id") caregiverId: Int): Response<CaregiverDetailResponse>

    @POST("api/caregivers")
    suspend fun createCaregiver(@Body request: CreateCaregiverRequest): Response<BaseResponse>

    @PUT("api/caregivers/{id}")
    suspend fun updateCaregiver(
        @Path("id") caregiverId: Int,
        @Body request: UpdateCaregiverRequest
    ): Response<BaseResponse>

    @DELETE("api/caregivers/{id}")
    suspend fun deleteCaregiver(@Path("id") caregiverId: Int): Response<BaseResponse>

    @POST("api/caregivers/{caregiver_id}/assign-patient/{patient_id}")
    suspend fun assignPatientToCaregiver(
        @Path("caregiver_id") caregiverId: Int,
        @Path("patient_id") patientId: Int
    ): Response<BaseResponse>

    @DELETE("api/caregivers/{caregiver_id}/unassign-patient/{patient_id}")
    suspend fun unassignPatientFromCaregiver(
        @Path("caregiver_id") caregiverId: Int,
        @Path("patient_id") patientId: Int
    ): Response<BaseResponse>

    @POST("api/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    // Therapist Rating endpoints
    @POST("api/therapist-ratings")
    suspend fun rateTherapist(@Body request: TherapistRatingRequest): Response<TherapistRatingResponse>

    @GET("api/therapist-ratings/{therapist_id}")
    suspend fun getTherapistRatings(@Path("therapist_id") therapistId: Int): Response<TherapistRatingsHistoryResponse>

    @GET("api/therapist-ratings/disqualifications/{therapist_id}")
    suspend fun getTherapistDisqualifications(@Path("therapist_id") therapistId: Int): Response<TherapistDisqualificationsResponse>

}

data class TokenResponse(
    val token: String
)

data class UploadPhotoRequest(
    val photo: String
)

data class UploadPhotoResponse(
    val message: String,
    val changes_remaining: Int
)

data class PhotoChangesResponse(
    val changes_used: Int,
    val changes_remaining: Int,
    val max_changes: Int
)

data class UploadBannerRequest(
    @SerializedName("banner_movil") val bannerMovil: String
)

data class UploadBannerResponse(
    val message: String,
    val changes_remaining: Int
)

data class BannerChangesResponse(
    val changes_used: Int,
    val changes_remaining: Int,
    val max_changes: Int
)

data class ProfileChangesResponse(
    val changes_used: Int,
    val changes_remaining: Int,
    val max_changes: Int
)

data class LatestPatientsResponse(
    val message: String,
    val data: List<com.example.serious_game_usil.data.PatientListItem>
)


// TherapySession moved to separate file to avoid duplication

data class PaginatedSessionsResponse(
    val message: String,
    val data: PaginatedSessionsData
)

data class PaginatedSessionsData(
    val sessions: List<com.example.serious_game_usil.`interface`.TherapySession>,
    val total: Int,
    val total_pages: Int,
    val current_page: Int,
    val has_next: Boolean,
    val has_previous: Boolean
)

data class SessionResponse(
    val message: String,
    val data: com.example.serious_game_usil.`interface`.TherapySession
)

data class CreateSessionRequest(
    val paciente_id: Int,
    val terapeuta_id: Int?,
    val fecha_sesion: String,
    val hora_inicio: String,
    val hora_fin: String,
    val ubicacion: String,
    val direccion: String,
    val descripcion: String,
    val objetivos: List<String>,
    val materiales: List<String>,
    val tipo_sesion: String,
    val modalidad: String
)

data class UpdateSessionRequest(
    val fecha_sesion: String?,
    val hora_inicio: String?,
    val hora_fin: String?,
    val ubicacion: String?,
    val direccion: String?,
    val descripcion: String?,
    val objetivos: List<String>?,
    val materiales: List<String>?,
    val notas_terapeuta: String?,
    val estado: String?,
    val tipo_sesion: String?,
    val modalidad: String?
)

data class RescheduleSessionRequest(
    val fecha_sesion: String,
    val hora_inicio: String,
    val hora_fin: String,
    val razon: String?
)

data class TherapistsListResponse(
    val message: String,
    val data: List<com.example.serious_game_usil.data.UserListItem>
)

data class SessionsListResponse(
    val message: String,
    val data: List<com.example.serious_game_usil.`interface`.TherapySession>
)

data class TherapySessionsResponse(
    val message: String,
    val patient: String? = null,
    val sessions: List<CaregiverPatientSession> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerializedName("total_pages") val totalPages: Int = 1,
    @SerializedName("has_previous") val hasPrevious: Boolean = false,
    @SerializedName("has_next") val hasNext: Boolean = false
)

data class CaregiverPatientSession(
    val id: Int,
    @SerializedName("fecha_sesion") val fechaSesion: String,
    @SerializedName("hora_inicio") val horaInicio: String,
    @SerializedName("hora_fin") val horaFin: String,
    val duracion: Int,
    val estado: String,
    val ubicacion: String? = null,
    val direccion: String? = null,
    val descripcion: String? = null,
    val objetivos: String? = null,
    val materiales: String? = null,
    @SerializedName("notas_terapeuta") val notasTerapeuta: String? = null,
    @SerializedName("terapeuta_nombre") val terapeutaNombre: String? = null,
    @SerializedName("tipo_sesion") val tipoSesion: String? = null,
    val modalidad: String? = null
)

// Therapist Rating Data Classes
data class TherapistRatingRequest(
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("therapist_id") val therapistId: Int,
    @SerializedName("caregiver_id") val caregiverId: Int,
    @SerializedName("patient_id") val patientId: Int,
    val rating: Int,
    val feedback: String?,
    @SerializedName("reassign_therapist") val reassignTherapist: Boolean
)

data class TherapistRatingResponse(
    val success: Boolean,
    val message: String,
    val data: TherapistRatingData?
)

data class TherapistRatingData(
    @SerializedName("rating_id") val ratingId: Int,
    @SerializedName("new_therapist_id") val newTherapistId: Int?,
    @SerializedName("new_therapist_name") val newTherapistName: String?,
    @SerializedName("therapist_disqualifications") val therapistDisqualifications: Int?,
    @SerializedName("therapist_removed") val therapistRemoved: Boolean?
)

data class TherapistRatingsHistoryResponse(
    val message: String,
    val data: TherapistRatingsHistoryData
)

data class TherapistRatingsHistoryData(
    @SerializedName("therapist_id") val therapistId: Int,
    @SerializedName("therapist_name") val therapistName: String,
    @SerializedName("average_rating") val averageRating: Double,
    @SerializedName("total_ratings") val totalRatings: Int,
    @SerializedName("total_disqualifications") val totalDisqualifications: Int,
    val ratings: List<TherapistRating>
)

data class TherapistRating(
    val id: Int,
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("caregiver_name") val caregiverName: String,
    @SerializedName("patient_name") val patientName: String,
    val rating: Int,
    val feedback: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("reassign_requested") val reassignRequested: Boolean
)

data class TherapistDisqualificationsResponse(
    val message: String,
    val data: TherapistDisqualificationsData
)

data class TherapistDisqualificationsData(
    @SerializedName("therapist_id") val therapistId: Int,
    @SerializedName("therapist_name") val therapistName: String,
    @SerializedName("total_disqualifications") val totalDisqualifications: Int,
    @SerializedName("is_removed") val isRemoved: Boolean,
    @SerializedName("removal_date") val removalDate: String?,
    val disqualifications: List<TherapistDisqualification>
)

data class TherapistDisqualification(
    val id: Int,
    @SerializedName("session_id") val sessionId: Int,
    @SerializedName("caregiver_name") val caregiverName: String,
    @SerializedName("patient_name") val patientName: String,
    val rating: Int,
    val feedback: String?,
    @SerializedName("created_at") val createdAt: String
)
