package controllers

import (
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
	"usuarios/dto"
	"usuarios/models"
	"usuarios/service"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
)


type TherapyController struct {
	therapyService *services.TherapyService
}

func NewTherapyController(therapyService *services.TherapyService) *TherapyController {
	return &TherapyController{therapyService: therapyService}
}

func (tc *TherapyController) Create(c *gin.Context) {
	userID, _, err := tc.validateAdminOrTherapist(c)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores y terapeutas pueden crear sesiones"})
		return
	}

	var dto dto.CreateTherapySessionDTO
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	session, err := tc.therapyService.Create(&dto, userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Sesión creada exitosamente",
		"data":    tc.toSessionResponse(session),
	})
}

func (tc *TherapyController) GetAll(c *gin.Context) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	sessions, err := tc.therapyService.GetAll(userID, roles)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	responses := make([]dto.SessionResponse, len(sessions))
	for i, session := range sessions {
		responses[i] = tc.toSessionResponse(&session)
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesiones obtenidas exitosamente",
		"data":    responses,
	})
}

func (tc *TherapyController) GetPaginated(c *gin.Context) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	var search dto.SearchSessionsDTO
	if err := c.ShouldBindQuery(&search); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if search.Page <= 0 {
		search.Page = 1
	}
	if search.Limit <= 0 || search.Limit > 5 {
		search.Limit = 5
	}

	result, err := tc.therapyService.GetPaginated(userID, roles, &search)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesiones obtenidas exitosamente",
		"data":    result,
	})
}

func (tc *TherapyController) GetByID(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	session, err := tc.therapyService.GetByID(uint(id), userID, roles)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesión obtenida exitosamente",
		"data":    tc.toSessionResponse(session),
	})
}

func (tc *TherapyController) GetPatientSessions(c *gin.Context) {
	patientID, err := strconv.ParseUint(c.Param("patient_id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de paciente inválido"})
		return
	}

	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	sessions, err := tc.therapyService.GetPatientSessions(uint(patientID), userID, roles)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	responses := make([]dto.SessionResponse, len(sessions))
	for i, session := range sessions {
		responses[i] = tc.toSessionResponse(&session)
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesiones del paciente obtenidas exitosamente",
		"data":    responses,
	})
}

func (tc *TherapyController) Update(c *gin.Context) {
	userID, roles, err := tc.validateAdminOrTherapist(c)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores y terapeutas pueden actualizar sesiones"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var dto dto.UpdateTherapySessionDTO
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	session, err := tc.therapyService.Update(uint(id), &dto, userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesión actualizada exitosamente",
		"data":    tc.toSessionResponse(session),
	})
}

func (tc *TherapyController) Reschedule(c *gin.Context) {
	userID, roles, err := tc.validateAdminOrTherapist(c)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores y terapeutas pueden reprogramar sesiones"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var dto dto.RescheduleSessionDTO
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	session, err := tc.therapyService.Reschedule(uint(id), &dto, userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Sesión reprogramada exitosamente",
		"data":    tc.toSessionResponse(session),
	})
}

func (tc *TherapyController) Delete(c *gin.Context) {
	userID, roles, err := tc.validateAdminOrTherapist(c)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores y terapeutas pueden eliminar sesiones"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	err = tc.therapyService.Delete(uint(id), userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Sesión eliminada exitosamente"})
}

func (tc *TherapyController) ExportToPDF(c *gin.Context) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	pdfBytes, filename, err := tc.therapyService.ExportToPDF(uint(id), userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.Header("Content-Type", "application/pdf")
	c.Header("Content-Disposition", "attachment; filename="+filename)
	c.Data(http.StatusOK, "application/pdf", pdfBytes)
}

func (tc *TherapyController) ExportToJPG(c *gin.Context) {
	userID, roles, err := tc.validateAdminOrTherapist(c)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores y terapeutas pueden exportar a JPG"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	jpgBytes, filename, err := tc.therapyService.ExportToJPG(uint(id), userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.Header("Content-Type", "image/jpeg")
	c.Header("Content-Disposition", "attachment; filename="+filename)
	c.Data(http.StatusOK, "image/jpeg", jpgBytes)
}

func (tc *TherapyController) GetLatestPatients(c *gin.Context) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	patients, err := tc.therapyService.GetLatestPatients(userID, roles)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Últimos 3 pacientes obtenidos exitosamente",
		"data":    patients,
	})
}

func (tc *TherapyController) GetPatientStats(c *gin.Context) {
	patientID, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de paciente inválido"})
		return
	}

	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	stats, err := tc.therapyService.GetPatientStats(uint(patientID), userID, roles)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Estadísticas del paciente obtenidas exitosamente",
		"data":    stats,
	})
}

func (tc *TherapyController) GetAvailableTherapists(c *gin.Context) {
	// Permitir acceso a administradores Y terapeutas
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	// Verificar si es admin o terapeuta
	isAuthorized := false
	for _, role := range roles {
		roleLower := strings.ToLower(strings.TrimSpace(role))
		if roleLower == "admin" || roleLower == "administrador" ||
		   roleLower == "terapeuta" || roleLower == "therapist" ||
		   roleLower == "tr" {
			isAuthorized = true
			break
		}
	}

	if !isAuthorized {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tienes permisos para ver terapeutas disponibles"})
		return
	}

	log.Printf("[GetAvailableTherapists] UserID: %d, Roles: %v", userID, roles)

	// Ejecutar debug del preload de cuidadores antes de obtener terapeutas
	if err := tc.therapyService.DebugCaregiverPreload(); err != nil {
		log.Printf("[ERROR] Debug caregiver preload failed: %v", err)
	}

	// Obtener solo terapeutas disponibles
	therapists, err := tc.therapyService.GetAvailableTherapists()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Terapeutas disponibles obtenidos exitosamente",
		"data":    therapists,
	})
}

func (tc *TherapyController) validateToken(c *gin.Context) (uint, []string, error) {
	claims := c.MustGet("user").(*utils.Claims)
	roles := strings.Split(claims.Roles, ",")
	return claims.UserID, roles, nil
}

func (tc *TherapyController) validateAdminOnly(c *gin.Context) (uint, []string, error) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		return 0, nil, err
	}

	if !tc.hasRole(roles, "AD") {
		return 0, nil, errors.New("Acceso denegado")
	}

	return userID, roles, nil
}

func (tc *TherapyController) validateAdminOrTherapist(c *gin.Context) (uint, []string, error) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		return 0, nil, err
	}

	if !tc.hasRole(roles, "AD") && !tc.hasRole(roles, "TR") {
		return 0, nil, errors.New("Acceso denegado")
	}

	return userID, roles, nil
}

func (tc *TherapyController) hasRole(roles []string, targetRole string) bool {
	for _, role := range roles {
		roleClean := strings.ToLower(strings.TrimSpace(role))
		switch targetRole {
		case "AD":
			if roleClean == "ad" || roleClean == "administrador" || roleClean == "admin" {
				return true
			}
		case "TR":
			if roleClean == "tr" || roleClean == "terapeuta" {
				return true
			}
		case "PD":
			if roleClean == "pd" || roleClean == "padre" || roleClean == "cuidador" {
				return true
			}
		}
	}
	return false
}

func (tc *TherapyController) toSessionResponse(session *models.TherapySession) dto.SessionResponse {
	// Delegar al servicio que ya tiene la lógica completa con rating y terapeuta reasignado
	return tc.therapyService.ToSessionResponse(*session)
}


func (tc *TherapyController) CreateTherapistRating(c *gin.Context) {
	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	// Only caregivers and administrators can rate therapists
	if !tc.hasRole(roles, "PD") && !tc.hasRole(roles, "AD") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo los cuidadores y administradores pueden calificar terapeutas"})
		return
	}

	var createDto dto.CreateTherapistRatingDTO
	if err := c.ShouldBindJSON(&createDto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Verify that the caregiver ID in the DTO matches the authenticated user
	// Exception: administrators can rate on behalf of caregivers
	if createDto.CaregiverID != userID && !tc.hasRole(roles, "AD") {
		c.JSON(http.StatusForbidden, gin.H{"error": "No puedes calificar en nombre de otro cuidador"})
		return
	}

	rating, err := tc.therapyService.CreateTherapistRating(&createDto, userID, roles)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Convert to response map for now to avoid struct issues
	response := gin.H{
		"id":           rating.ID,
		"session_id":   rating.SessionID,
		"therapist_id": rating.TherapistID,
		"caregiver_id": rating.CaregiverID,
		"patient_id":   rating.PatientID,
		"rating":       rating.Rating,
		"comment":      rating.Comment,
		"created_at":   rating.CreatedAt.Format("2006-01-02 15:04:05"),
		"updated_at":   rating.UpdatedAt.Format("2006-01-02 15:04:05"),
	}

	// Add names if relationships are loaded
	if rating.Therapist.ID > 0 {
		response["therapist_name"] = rating.Therapist.Nombres_Apellidos
	}
	if rating.Caregiver.ID > 0 {
		response["caregiver_name"] = rating.Caregiver.Nombres_Apellidos
	}
	if rating.Patient.ID > 0 {
		response["patient_name"] = rating.Patient.NombresApellidos
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Calificación creada exitosamente",
		"data":    response,
	})
}

func (tc *TherapyController) GetTherapistRatings(c *gin.Context) {
	therapistID, err := strconv.ParseUint(c.Param("therapist_id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de terapeuta inválido"})
		return
	}

	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	ratings, err := tc.therapyService.GetTherapistRatings(uint(therapistID), userID, roles)
	if err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Calificaciones obtenidas exitosamente",
		"data":    ratings,
	})
}

func (tc *TherapyController) GetSessionRating(c *gin.Context) {
	sessionID, err := strconv.ParseUint(c.Param("session_id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de sesión inválido"})
		return
	}

	userID, roles, err := tc.validateToken(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	// Check permissions - caregivers and admins can view session ratings
	if !tc.hasRole(roles, "PD") && !tc.hasRole(roles, "AD") {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tienes permisos para ver esta calificación"})
		return
	}

	rating, err := tc.therapyService.GetSessionRating(uint(sessionID), userID, roles)
	if err != nil {
		if err.Error() == "calificación no encontrada" {
			c.JSON(http.StatusNotFound, gin.H{"error": "Esta sesión no ha sido calificada aún"})
			return
		}
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Convert to response map
	response := gin.H{
		"id":           rating.ID,
		"session_id":   rating.SessionID,
		"therapist_id": rating.TherapistID,
		"caregiver_id": rating.CaregiverID,
		"patient_id":   rating.PatientID,
		"rating":       rating.Rating,
		"comment":      rating.Comment,
		"created_at":   rating.CreatedAt.Format("2006-01-02 15:04:05"),
		"updated_at":   rating.UpdatedAt.Format("2006-01-02 15:04:05"),
	}

	// Add names if relationships are loaded
	if rating.Therapist.ID > 0 {
		response["therapist_name"] = rating.Therapist.Nombres_Apellidos
	}
	if rating.Caregiver.ID > 0 {
		response["caregiver_name"] = rating.Caregiver.Nombres_Apellidos
	}
	if rating.Patient.ID > 0 {
		response["patient_name"] = rating.Patient.NombresApellidos
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Calificación obtenida exitosamente",
		"data":    response,
	})
}

func (controller *TherapyController) UpdateExpiredSessions(c *gin.Context) {
	userID, _, err := controller.validateAdminOnly(c)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
		return
	}


	sessionsToUpdate, err := controller.therapyService.GetSessionsRequiringUpdate()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener sesiones para actualizar: " + err.Error(),
		})
		return
	}

	beforeCount := len(sessionsToUpdate)
	
	err = controller.therapyService.UpdateExpiredSessions()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al actualizar estados de sesiones: " + err.Error(),
		})
		return
	}

	
	sessionsAfterUpdate, err := controller.therapyService.GetSessionsRequiringUpdate()
	if err != nil {
		
		sessionsAfterUpdate = []models.TherapySession{}
	}

	afterCount := len(sessionsAfterUpdate)
	updatedCount := beforeCount - afterCount

	c.JSON(http.StatusOK, gin.H{
		"message":           "Actualización de sesiones ejecutada exitosamente",
		"sessions_found":    beforeCount,
		"sessions_updated":  updatedCount,
		"sessions_pending":  afterCount,
		"updated_by_admin":  userID,
		"updated_at":        time.Now().Format("2006-01-02 15:04:05"),
	})
}


func (controller *TherapyController) GetPatientReportHistory(c *gin.Context) {

	claims, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token de autenticación requerido"})
		return
	}

	userClaims := claims.(*utils.Claims)
	patientIDParam := c.Param("patient_id")
	patientID, err := strconv.ParseUint(patientIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de paciente inválido"})
		return
	}

	hasAccess, err := controller.therapyService.ValidatePatientAccess(uint(patientID), userClaims.UserID, userClaims.Roles)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al validar acceso: " + err.Error()})
		return
	}
	if !hasAccess {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para acceder a los reportes de este paciente"})
		return
	}

	// Parámetros de paginación
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))
	reportType := c.Query("report_type")

	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 50 {
		limit = 10
	}

	reports, total, err := controller.therapyService.GetPatientReportHistory(uint(patientID), page, limit, reportType)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener historial de reportes: " + err.Error()})
		return
	}

	totalPages := (total + limit - 1) / limit

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": gin.H{
			"reports":      reports,
			"pagination": gin.H{
				"current_page": page,
				"total_pages":  totalPages,
				"total_items":  total,
				"limit":        limit,
			},
			"patient_id": patientID,
		},
	})
}


func (controller *TherapyController) GetPatientFullReport(c *gin.Context) {
	claims, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token de autenticación requerido"})
		return
	}

	userClaims := claims.(*utils.Claims)
	patientIDParam := c.Param("patient_id")
	patientID, err := strconv.ParseUint(patientIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de paciente inválido"})
		return
	}

	// Validar formato
	format := c.Query("format")
	if format != "pdf" && format != "jpg" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Formato debe ser 'pdf' o 'jpg'"})
		return
	}

	// Validar acceso al paciente
	hasAccess, err := controller.therapyService.ValidatePatientAccess(uint(patientID), userClaims.UserID, userClaims.Roles)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al validar acceso: " + err.Error()})
		return
	}
	if !hasAccess {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para generar reportes de este paciente"})
		return
	}
	var dateFrom, dateTo *time.Time
	if dateFromStr := c.Query("date_from"); dateFromStr != "" {
		if parsed, err := time.Parse("2006-01-02", dateFromStr); err == nil {
			dateFrom = &parsed
		} else {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Formato de fecha_desde inválido (YYYY-MM-DD)"})
			return
		}
	}
	if dateToStr := c.Query("date_to"); dateToStr != "" {
		if parsed, err := time.Parse("2006-01-02", dateToStr); err == nil {
			dateTo = &parsed
		} else {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Formato de fecha_hasta inválido (YYYY-MM-DD)"})
			return
		}
	}
	reportData, err := controller.therapyService.GeneratePatientFullReport(uint(patientID), userClaims.UserID, dateFrom, dateTo)
	if err != nil {
		if err.Error() == "patient not found" {
			c.JSON(http.StatusNotFound, gin.H{"error": "Paciente no encontrado"})
			return
		}
		if err.Error() == "no sessions found" {
			c.JSON(http.StatusNotFound, gin.H{
				"error": "No se encontraron sesiones para este paciente en el período especificado",
				"message": "Para generar un reporte, el paciente debe tener al menos una sesión programada y un análisis emocional realizado.",
			})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al generar reporte: " + err.Error()})
		return
	}
	if len(reportData.Sessions) == 0 {
		c.JSON(http.StatusNotFound, gin.H{
			"error": "No hay sesiones disponibles para generar el reporte",
			"message": "Para generar un reporte, el paciente debe tener al menos una sesión programada.",
		})
		return
	}

	hasEmotionAnalysis := false
	for _, session := range reportData.Sessions {
		if session.EmotionAnalysis != nil {
			hasEmotionAnalysis = true
			break
		}
	}

	if !hasEmotionAnalysis {
		c.JSON(http.StatusNotFound, gin.H{
			"error": "No hay análisis emocionales disponibles para generar el reporte",
			"message": "Para generar un reporte completo, debe existir al menos un análisis emocional realizado durante las sesiones.",
		})
		return
	}


	pdfContent := ""
	if format == "pdf" {
		generatedPDFContent, err := controller.therapyService.GenerateHistoricalReportPDF(reportData)
		if err != nil {
			log.Printf("⚠️ Error al generar PDF: %v", err)
		} else {
			pdfContent = generatedPDFContent
		}
	}

	response := gin.H{
		"success": true,
		"message": "Reporte generado exitosamente",
		"data": reportData,
		"format": format,
		"generated_at": time.Now(),
		"generated_by": gin.H{
			"user_id": userClaims.UserID,
			"roles":   userClaims.Roles,
		},
	}
	if pdfContent != "" {
		response["pdf_content"] = pdfContent
	}

	c.JSON(http.StatusOK, response)
}


func (controller *TherapyController) DeletePatientReport(c *gin.Context) {
	claims, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token de autenticación requerido"})
		return
	}

	userClaims := claims.(*utils.Claims)
	patientIDParam := c.Param("patient_id")
	reportIDParam := c.Param("report_id")

	patientID, err := strconv.ParseUint(patientIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de paciente inválido"})
		return
	}

	reportID, err := strconv.ParseUint(reportIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de reporte inválido"})
		return
	}
	err = controller.therapyService.DeletePatientReport(uint(patientID), uint(reportID), userClaims.UserID, userClaims.Roles)
	if err != nil {
		if err.Error() == "report not found" {
			c.JSON(http.StatusNotFound, gin.H{"error": "Reporte no encontrado"})
			return
		}
		if err.Error() == "access denied" {
			c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para eliminar este reporte"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al eliminar reporte: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Reporte eliminado exitosamente",
		"deleted_by": userClaims.UserID,
		"deleted_at": time.Now(),
	})
}