package controllers

import (
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"
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
	response := dto.SessionResponse{
		ID:             session.ID,
		PacienteID:     session.PacienteID,
		TerapeutaID:    session.TerapeutaID,
		FechaSesion:    session.FechaSesion.Format("2006-01-02"),
		HoraInicio:     session.HoraInicio,
		HoraFin:        session.HoraFin,
		Duracion:       session.Duracion,
		Ubicacion:      session.Ubicacion,
		Direccion:      session.Direccion,
		Descripcion:    session.Descripcion,
		Objetivos:      []string(session.Objetivos),
		Materiales:     []string(session.Materiales),
		NotasTerapeuta: session.NotasTerapeuta,
		Estado:         session.Estado,
		TipoSesion:     session.TipoSesion,
		Modalidad:      session.Modalidad,
		UpdateCount:    session.UpdateCount,
		CreatedAt:      session.CreatedAt.Format("2006-01-02 15:04:05"),
		UpdatedAt:      session.UpdatedAt.Format("2006-01-02 15:04:05"),
		Paciente: dto.PatientBasicInfo{
			ID:               session.Paciente.ID,
			NombresApellidos: session.Paciente.NombresApellidos,
			FotoMovil:        session.Paciente.FotoMovil,
		},
		Terapeuta: dto.UserBasicInfo{
			ID:                session.Terapeuta.ID,
			NombresApellidos:  session.Terapeuta.Nombres_Apellidos,
			Correo:            session.Terapeuta.Correo,
			Telefono:          session.Terapeuta.Telefono,
		},
	}

	// Agregar información del cuidador si existe
	if session.Paciente.Cuidador != nil {
		response.Cuidador = &dto.UserBasicInfo{
			ID:                session.Paciente.Cuidador.ID,
			NombresApellidos:  session.Paciente.Cuidador.Nombres_Apellidos,
			Correo:            session.Paciente.Cuidador.Correo,
			Telefono:          session.Paciente.Cuidador.Telefono,
		}
	}

	return response
}