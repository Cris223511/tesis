package controllers

import (
	"net/http"
	"strconv"
	"strings"
	"usuarios/dto"
	"usuarios/models"
	"usuarios/service"

	"github.com/gin-gonic/gin"
)

type ReportController struct {
	reportService *services.ReportService
}

func NewReportController(reportService *services.ReportService) *ReportController {
	return &ReportController{reportService: reportService}
}

func (rc *ReportController) Create(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	var createDTO dto.CreateReportDTO
	if err := c.ShouldBindJSON(&createDTO); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	report, err := rc.reportService.Create(&createDTO, userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Reporte creado exitosamente",
		"data":    rc.reportService.ToReportResponse(report),
	})
}

func (rc *ReportController) Update(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var updateDTO dto.UpdateReportDTO
	if err := c.ShouldBindJSON(&updateDTO); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	report, err := rc.reportService.Update(uint(id), &updateDTO, userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Reporte actualizado exitosamente",
		"data":    rc.reportService.ToReportResponse(report),
	})
}

func (rc *ReportController) Delete(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	if err := rc.reportService.Delete(uint(id), userID); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Reporte eliminado exitosamente",
	})
}

func (rc *ReportController) GetByID(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	report, err := rc.reportService.GetByID(uint(id))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"data": rc.reportService.ToReportResponse(report),
	})
}

func (rc *ReportController) List(c *gin.Context) {
	var filter dto.ReportFilterDTO
	if err := c.ShouldBindQuery(&filter); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)

	rolesVal, _ := c.Get("roles")
	var roles []string
	if rolesSlice, ok := rolesVal.([]models.Role); ok {
		for _, role := range rolesSlice {
			roles = append(roles, role.Name)
		}
	}

	isAdmin := false
	for _, role := range roles {
		if role == "AD" {
			isAdmin = true
			break
		}
	}

	if !isAdmin && filter.GeneratedBy == nil {
		filter.GeneratedBy = &userID
	}

	response, err := rc.reportService.List(&filter)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, response)
}

func (rc *ReportController) GetStatistics(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	stats, err := rc.reportService.GetStatistics(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"data": stats,
	})
}

func (rc *ReportController) Download(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	reader, fileName, err := rc.reportService.DownloadReport(uint(id))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
		return
	}

	c.Header("Content-Disposition", "attachment; filename="+fileName)
	c.Header("Content-Type", getContentType(fileName))
	c.Header("Cache-Control", "no-cache, no-store, must-revalidate")

	c.DataFromReader(http.StatusOK, -1, getContentType(fileName), reader, nil)
}

func (rc *ReportController) GenerateSessionReport(c *gin.Context) {
	sessionID, err := strconv.ParseUint(c.Param("sessionId"), 10, 32)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID de sesión inválido"})
		return
	}

	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	var request dto.GenerateReportRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	sessionIDUint := uint(sessionID)
	createDTO := dto.CreateReportDTO{
		Type:            models.ReportTypeSession,
		Format:          request.Format,
		SessionID:       &sessionIDUint,
		Title:           "Reporte de Sesión #" + strconv.FormatUint(sessionID, 10),
		Description:     "Reporte detallado de la sesión terapéutica",
		IncludeAnalysis: request.Parameters.IncludeAnalysis,
		IncludeCharts:   request.Parameters.IncludeCharts,
	}

	report, err := rc.reportService.Create(&createDTO, userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Reporte de sesión generado exitosamente",
		"data":    rc.reportService.ToReportResponse(report),
	})
}

func (rc *ReportController) GenerateGeneralReport(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}
	userID := userIDVal.(uint)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "usuario no autenticado"})
		return
	}

	var request dto.GenerateReportRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	createDTO := dto.CreateReportDTO{
		Type:            models.ReportTypeGeneral,
		Format:          request.Format,
		TherapistID:     request.Parameters.TherapistID,
		PatientID:       request.Parameters.PatientID,
		Title:           "Reporte General de Sesiones",
		Description:     "Reporte consolidado de sesiones terapéuticas",
		IncludeAnalysis: request.Parameters.IncludeAnalysis,
		IncludeCharts:   request.Parameters.IncludeCharts,
	}

	if request.Parameters.StartDate != nil {
		startDateStr := request.Parameters.StartDate.Format("2006-01-02")
		createDTO.StartDate = &startDateStr
	}

	if request.Parameters.EndDate != nil {
		endDateStr := request.Parameters.EndDate.Format("2006-01-02")
		createDTO.EndDate = &endDateStr
	}

	report, err := rc.reportService.Create(&createDTO, userID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Reporte general generado exitosamente",
		"data":    rc.reportService.ToReportResponse(report),
	})
}

func getContentType(fileName string) string {
	switch {
	case strings.HasSuffix(fileName, ".pdf"):
		return "application/pdf"
	case strings.HasSuffix(fileName, ".json"):
		return "application/json"
	case strings.HasSuffix(fileName, ".html"):
		return "text/html"
	case strings.HasSuffix(fileName, ".xlsx"):
		return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
	default:
		return "application/octet-stream"
	}
}