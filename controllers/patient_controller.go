package controllers

import (
	"bytes"
	"fmt"
	"io"
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

type PatientController struct {
	patientService *services.PatientService
}

func NewPatientController(patientService *services.PatientService) *PatientController {
	return &PatientController{
		patientService: patientService,
	}
}

func (pc *PatientController) hasPatientPermission(c *gin.Context, action string) bool {
	user, exists := c.Get("user")
	if !exists {
		fmt.Printf("DEBUG: No user in context\n")
		return false
	}

	claims := user.(*utils.Claims)
	roles := strings.Split(claims.Roles, ",")

	fmt.Printf("DEBUG: UserID=%d, Roles=%s, Action=%s\n", claims.UserID, claims.Roles, action)

	for _, role := range roles {
		role = strings.TrimSpace(role) // Limpiar espacios
		roleLower := strings.ToLower(role)
		fmt.Printf("DEBUG: Checking role: '%s' (lowercase: '%s')\n", role, roleLower)

		// Aceptar tanto códigos como nombres completos
		switch roleLower {
		case "tr", "terapeuta", "ad", "administrador", "admin":
			fmt.Printf("DEBUG: Permission granted for role %s\n", role)
			return true
		case "pd", "padre", "cuidador":
			if action == "read" {
				fmt.Printf("DEBUG: Permission granted for %s read\n", role)
				return true
			}
		}
	}
	fmt.Printf("DEBUG: Permission denied\n")
	return false
}

func (pc *PatientController) CreatePatient(c *gin.Context) {
	if !pc.hasPatientPermission(c, "create") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Sin permisos para crear pacientes"})
		return
	}

	var dto dto.CreatePatientDTO
	if err := c.ShouldBindJSON(&dto); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	claims := c.MustGet("user").(*utils.Claims)

	// Validar que solo administradores puedan asignar cuidadores
	roles := strings.Split(claims.Roles, ",")
	isAdmin := false
	for _, role := range roles {
		roleLower := strings.ToLower(strings.TrimSpace(role))
		if roleLower == "ad" || roleLower == "administrador" || roleLower == "admin" {
			isAdmin = true
			break
		}
	}

	if !isAdmin && dto.CuidadorID != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores pueden asignar cuidadores"})
		return
	}

	patient, err := pc.patientService.Create(&dto, claims.UserID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, pc.toResponseDTO(patient))
}

func (pc *PatientController) GetPatients(c *gin.Context) {
	if !pc.hasPatientPermission(c, "read") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Sin permisos para ver pacientes"})
		return
	}

	claims := c.MustGet("user").(*utils.Claims)
	roles := strings.Split(claims.Roles, ",")

	page := 1
	if p := c.Query("page"); p != "" {
		if pageNum, err := strconv.Atoi(p); err == nil && pageNum > 0 {
			page = pageNum
		}
	}

	limit := 5
	if l := c.Query("limit"); l != "" {
		if limitNum, err := strconv.Atoi(l); err == nil && limitNum > 0 && limitNum <= 5 {
			limit = limitNum
		}
	}

	search := c.Query("search")

	result, err := pc.patientService.GetAllPaginated(claims.UserID, roles, page, limit, search)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error obteniendo pacientes"})
		return
	}

	var response []dto.PatientListDTO
	for _, p := range result.Patients {
		response = append(response, pc.toListDTO(&p))
	}

	c.JSON(http.StatusOK, gin.H{
		"patients":     response,
		"total":        result.Total,
		"page":         page,
		"limit":        limit,
		"total_pages":  result.TotalPages,
		"has_next":     result.HasNext,
		"has_previous": result.HasPrevious,
	})
}

func (pc *PatientController) GetPatient(c *gin.Context) {
	if !pc.hasPatientPermission(c, "read") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Sin permisos para ver paciente"})
		return
	}

	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	claims := c.MustGet("user").(*utils.Claims)
	roles := strings.Split(claims.Roles, ",")

	if !pc.patientService.CanAccess(uint(id), claims.UserID, roles) {
		c.JSON(http.StatusForbidden, gin.H{"error": "No tienes acceso a este paciente"})
		return
	}

	patient, err := pc.patientService.GetByID(uint(id))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Paciente no encontrado"})
		return
	}

	c.JSON(http.StatusOK, pc.toResponseDTO(patient))
}

func (pc *PatientController) UpdatePatient(c *gin.Context) {
	if !pc.hasPatientPermission(c, "update") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Sin permisos para actualizar pacientes"})
		return
	}

	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}


	bodyBytes, _ := c.GetRawData()
	log.Printf("[PATIENT][UPDATE] Raw body received: %s", string(bodyBytes))

	c.Request.Body = io.NopCloser(bytes.NewBuffer(bodyBytes))

	var dto dto.UpdatePatientDTO
	if err := c.ShouldBindJSON(&dto); err != nil {
		log.Printf("[PATIENT][UPDATE] Binding error: %v", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	log.Printf("[PATIENT][UPDATE] DTO parsed: %+v", dto)

	claims := c.MustGet("user").(*utils.Claims)

	// Validar que solo administradores puedan asignar/modificar cuidadores
	roles := strings.Split(claims.Roles, ",")
	isAdmin := false
	for _, role := range roles {
		roleLower := strings.ToLower(strings.TrimSpace(role))
		if roleLower == "ad" || roleLower == "administrador" || roleLower == "admin" {
			isAdmin = true
			break
		}
	}

	if !isAdmin && dto.CuidadorID != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "Solo administradores pueden asignar cuidadores"})
		return
	}

	patient, err := pc.patientService.Update(uint(id), &dto)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, pc.toResponseDTO(patient))
}

func (pc *PatientController) DeletePatient(c *gin.Context) {
	if !pc.hasPatientPermission(c, "delete") {
		c.JSON(http.StatusForbidden, gin.H{"error": "Sin permisos para eliminar pacientes"})
		return
	}

	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	if err := pc.patientService.Delete(uint(id)); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Paciente eliminado exitosamente"})
}

func (pc *PatientController) toResponseDTO(p *models.Patient) dto.PatientResponseDTO {
	dto := dto.PatientResponseDTO{
		ID:                 p.ID,
		SerialID:           p.SerialID,
		NombresApellidos:   p.NombresApellidos,
		FechaNacimiento:    time.Time(p.FechaNacimiento).Format("2006-01-02"),
		TipoDocumento:      p.TipoDocumento,
		NumDocumento:       p.NumDocumento,
		Altura:             p.Altura,
		Peso:               p.Peso,
		IMC:                p.IMC,
		Sexo:               p.Sexo,
		DiagnosticoClinico: p.DiagnosticoClinico,
		FotoMovil:          p.FotoMovil,
		FotoWeb:            p.FotoWeb,
		TerapeutaID:        p.TerapeutaID,
		CuidadorID:         p.CuidadorID,
		Activo:             p.Activo,
		CreatedAt:          p.CreatedAt,
		UpdatedAt:          p.UpdatedAt,
	}

	if p.Terapeuta.ID > 0 {
		dto.TerapeutaNombre = p.Terapeuta.Nombres_Apellidos
	}

	if p.Cuidador != nil && p.Cuidador.ID > 0 {
		dto.CuidadorNombre = &p.Cuidador.Nombres_Apellidos
	}

	return dto
}

func (pc *PatientController) toListDTO(p *models.Patient) dto.PatientListDTO {
	edad := int(time.Since(time.Time(p.FechaNacimiento)).Hours() / 24 / 365)

	log.Printf("[DEBUG] Patient %d: TerapeutaID=%d, Terapeuta.ID=%d, Terapeuta.Nombre=%s",
		p.ID, p.TerapeutaID, p.Terapeuta.ID, p.Terapeuta.Nombres_Apellidos)

	dto := dto.PatientListDTO{
		ID:               p.ID,
		SerialID:         p.SerialID,
		NombresApellidos: p.NombresApellidos,
		TipoDocumento:    p.TipoDocumento,
		NumDocumento:     p.NumDocumento,
		Edad:             edad,
		Sexo:             p.Sexo,
		TerapeutaID:      p.TerapeutaID,
		TerapeutaNombre:  p.Terapeuta.Nombres_Apellidos,
		Activo:           p.Activo,
		FotoMovil:        p.FotoMovil,
		FotoWeb:          p.FotoWeb,
	}

	if p.Cuidador != nil && p.Cuidador.ID > 0 {
		dto.CuidadorNombre = &p.Cuidador.Nombres_Apellidos
	}

	return dto
}