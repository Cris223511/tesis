package controllers

import (
	"net/http"
	"strconv"
	"time"
	"usuarios/models"
	"usuarios/service"

	"github.com/gin-gonic/gin"
)

type AutismController struct {
	progressService *services.AutismProgressService
}

func NewAutismController(progressService *services.AutismProgressService) *AutismController {
	return &AutismController{
		progressService: progressService,
	}
}

// GetThreeMonthComparison - Obtiene la comparativa de los últimos 3 meses
// @Summary Comparativa de progreso de los últimos 3 meses
// @Description Obtiene estadísticas de progreso terapéutico de un niño comparando los últimos 3 meses
// @Tags autism
// @Accept json
// @Produce json
// @Param child_id path int true "ID del niño"
// @Success 200 {object} services.ThreeMonthComparison
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /api/autism/children/{child_id}/progress/3months [get]
func (ctrl *AutismController) GetThreeMonthComparison(c *gin.Context) {
	childIDParam := c.Param("child_id")
	childID, err := strconv.ParseUint(childIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de niño inválido"})
		return
	}

	// Verificar que el usuario tenga permisos para ver este niño
	userID, _ := c.Get("userID")
	if !ctrl.canAccessChild(c, uint(childID), userID.(uint)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para acceder a este niño"})
		return
	}

	comparison, err := ctrl.progressService.GetThreeMonthComparison(uint(childID))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error obteniendo comparativa: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, comparison)
}

// RecordTherapySession - Registra una nueva sesión terapéutica
// @Summary Registrar sesión terapéutica
// @Description Registra una nueva sesión de terapia con serious game para un niño autista
// @Tags autism
// @Accept json
// @Produce json
// @Param session body models.TherapySession true "Datos de la sesión"
// @Success 201 {object} map[string]interface{}
// @Failure 400 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /api/autism/sessions [post]
func (ctrl *AutismController) RecordTherapySession(c *gin.Context) {
	var session models.TherapySession
	if err := c.ShouldBindJSON(&session); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos de sesión inválidos: " + err.Error()})
		return
	}

	// Validar que el usuario tenga permisos para registrar sesiones de este niño
	userID, _ := c.Get("userID")
	if !ctrl.canAccessChild(c, session.ChildID, userID.(uint)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para registrar sesiones de este niño"})
		return
	}

	// Establecer IDs adicionales
	session.ParentID = userID.(uint)
	if session.SessionDate.IsZero() {
		session.SessionDate = time.Now()
	}

	if err := ctrl.progressService.RecordTherapySession(&session); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error registrando sesión: " + err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message":    "Sesión registrada exitosamente",
		"session_id": session.ID,
	})
}

// GetChildrenProgress - Obtiene el progreso de todos los hijos de un padre
// @Summary Progreso de todos los hijos
// @Description Obtiene un resumen del progreso de todos los hijos asociados a un padre
// @Tags autism
// @Accept json
// @Produce json
// @Success 200 {array} services.ThreeMonthComparison
// @Failure 500 {object} map[string]string
// @Router /api/autism/children/progress [get]
func (ctrl *AutismController) GetChildrenProgress(c *gin.Context) {
	userID, _ := c.Get("userID")
	
	// Obtener hijos del usuario
	children, err := ctrl.getUserChildren(userID.(uint))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error obteniendo hijos: " + err.Error()})
		return
	}

	var progressList []services.ThreeMonthComparison
	for _, child := range children {
		comparison, err := ctrl.progressService.GetThreeMonthComparison(child.ID)
		if err != nil {
			// Log error pero continúa con otros hijos
			continue
		}
		progressList = append(progressList, *comparison)
	}

	c.JSON(http.StatusOK, progressList)
}

// GetRecentSessions - Obtiene las sesiones recientes de un niño
// @Summary Sesiones recientes
// @Description Obtiene las últimas sesiones terapéuticas de un niño
// @Tags autism
// @Accept json
// @Produce json
// @Param child_id path int true "ID del niño"
// @Param limit query int false "Límite de sesiones (default: 10)"
// @Success 200 {array} models.TherapySession
// @Failure 400 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /api/autism/children/{child_id}/sessions [get]
func (ctrl *AutismController) GetRecentSessions(c *gin.Context) {
	childIDParam := c.Param("child_id")
	childID, err := strconv.ParseUint(childIDParam, 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de niño inválido"})
		return
	}

	userID, _ := c.Get("userID")
	if !ctrl.canAccessChild(c, uint(childID), userID.(uint)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos para acceder a este niño"})
		return
	}

	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))
	
	sessions, err := ctrl.getRecentSessions(uint(childID), limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error obteniendo sesiones: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, sessions)
}

// StartTherapySession - Inicia una nueva sesión (registro en tiempo real)
// @Summary Iniciar sesión terapéutica
// @Description Inicia una nueva sesión y devuelve el ID para ir actualizando en tiempo real
// @Tags autism
// @Accept json
// @Produce json
// @Param session body StartSessionRequest true "Datos iniciales de la sesión"
// @Success 201 {object} map[string]interface{}
// @Failure 400 {object} map[string]string
// @Router /api/autism/sessions/start [post]
func (ctrl *AutismController) StartTherapySession(c *gin.Context) {
	type StartSessionRequest struct {
		ChildID      uint   `json:"child_id" binding:"required"`
		ActivityType string `json:"activity_type" binding:"required"`
		ActivityName string `json:"activity_name" binding:"required"`
		DifficultyLevel int `json:"difficulty_level"`
	}

	var req StartSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos: " + err.Error()})
		return
	}

	userID, _ := c.Get("userID")
	if !ctrl.canAccessChild(c, req.ChildID, userID.(uint)) {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tiene permisos"})
		return
	}

	session := models.TherapySession{
		ChildID:         req.ChildID,
		ParentID:        userID.(uint),
		SessionDate:     time.Now(),
		StartTime:       time.Now(),
		ActivityType:    req.ActivityType,
		ActivityName:    req.ActivityName,
		DifficultyLevel: req.DifficultyLevel,
	}

	if err := ctrl.progressService.RecordTherapySession(&session); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error iniciando sesión: " + err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"session_id": session.ID,
		"start_time": session.StartTime,
		"message":    "Sesión iniciada exitosamente",
	})
}

// Helper methods

func (ctrl *AutismController) canAccessChild(c *gin.Context, childID, userID uint) bool {
	// Verificar si el usuario es padre del niño o tiene permisos
	// Esta lógica dependería de tu modelo de relaciones padre-hijo
	return true // Por ahora permitir todo - implementar según tu lógica de negocio
}

func (ctrl *AutismController) getUserChildren(userID uint) ([]models.Usuarios, error) {
	// Implementar lógica para obtener hijos del usuario
	// Esto dependería de tu modelo de relaciones
	var children []models.Usuarios
	// ... lógica de consulta
	return children, nil
}

func (ctrl *AutismController) getRecentSessions(childID uint, limit int) ([]models.TherapySession, error) {
	// Implementar consulta de sesiones recientes
	var sessions []models.TherapySession
	// ... lógica de consulta
	return sessions, nil
}