package controllers

import (
	"net/http"
	"strconv"
	"usuarios/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type CaregiverPatientsController struct {
	db *gorm.DB
}

func NewCaregiverPatientsController(db *gorm.DB) *CaregiverPatientsController {
	return &CaregiverPatientsController{db: db}
}

func (cpc *CaregiverPatientsController) GetMyPatients(c *gin.Context) {
	userID, exists := c.Get("user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	var currentUser models.Usuarios
	if err := cpc.db.Preload("Roles").First(&currentUser, userID).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuario"})
		return
	}

	isCaregiver := false
	for _, role := range currentUser.Roles {
		if role.ID == 4 {
			isCaregiver = true
			break
		}
	}

	if !isCaregiver {
		c.JSON(http.StatusForbidden, gin.H{"error": "Acceso denegado. Solo cuidadores pueden acceder"})
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))

	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 10
	}

	offset := (page - 1) * limit

	var patients []models.Patient
	var total int64

	query := cpc.db.Model(&models.Patient{}).Where("cuidador_id = ?", userID)
	query.Count(&total)

	if err := query.
		Limit(limit).
		Offset(offset).
		Find(&patients).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener pacientes",
		})
		return
	}

	var patientList []gin.H
	for _, patient := range patients {
		var lastSession models.TherapySession
		cpc.db.Where("paciente_id = ?", patient.ID).
			Order("fecha_sesion DESC").
			First(&lastSession)

		var sessionCount int64
		cpc.db.Model(&models.TherapySession{}).
			Where("paciente_id = ?", patient.ID).
			Count(&sessionCount)

		var fotoBase64 string
		if patient.FotoMovil != "" {
			fotoBase64 = patient.FotoMovil
		} else {
			fotoBase64 = ""
		}

		patientData := gin.H{
			"id":                patient.ID,
			"serial_id":         patient.SerialID,
			"nombres_apellidos": patient.NombresApellidos,
			"fecha_nacimiento":  patient.FechaNacimiento,
			"tipo_documento":    patient.TipoDocumento,
			"num_documento":     patient.NumDocumento,
			"sexo":              patient.Sexo,
			"altura":            patient.Altura,
			"peso":              patient.Peso,
			"imc":               patient.IMC,
			"diagnostico_clinico": patient.DiagnosticoClinico,
			"foto_movil":        fotoBase64,
			"activo":            patient.Activo,
			"total_sesiones":    sessionCount,
		}

		if lastSession.ID != 0 {
			patientData["ultima_sesion"] = gin.H{
				"fecha":  lastSession.FechaSesion,
				"estado": lastSession.Estado,
			}
		}

		patientList = append(patientList, patientData)
	}

	totalPages := (int(total) + limit - 1) / limit

	c.JSON(http.StatusOK, gin.H{
		"message":      "Pacientes obtenidos exitosamente",
		"patients":     patientList,
		"total":        total,
		"page":         page,
		"total_pages":  totalPages,
		"has_previous": page > 1,
		"has_next":     page < totalPages,
	})
}

func (cpc *CaregiverPatientsController) GetPatientSessions(c *gin.Context) {
	userID, exists := c.Get("user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	patientID := c.Param("patient_id")

	var patient models.Patient
	if err := cpc.db.Where("id = ? AND cuidador_id = ?", patientID, userID).First(&patient).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "Paciente no encontrado o no autorizado"})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al buscar paciente"})
		}
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "10"))

	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 10
	}

	offset := (page - 1) * limit

	var sessions []models.TherapySession
	var total int64

	query := cpc.db.Model(&models.TherapySession{}).Where("paciente_id = ?", patientID)
	query.Count(&total)

	if err := query.
		Order("fecha_sesion DESC").
		Limit(limit).
		Offset(offset).
		Find(&sessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener sesiones",
		})
		return
	}

	var sessionList []gin.H
	for _, session := range sessions {
		var therapist models.Usuarios
		cpc.db.First(&therapist, session.TerapeutaID)

		sessionList = append(sessionList, gin.H{
			"id":                session.ID,
			"fecha_sesion":      session.FechaSesion,
			"hora_inicio":       session.HoraInicio,
			"hora_fin":          session.HoraFin,
			"duracion":          session.Duracion,
			"estado":            session.Estado,
			"ubicacion":         session.Ubicacion,
			"direccion":         session.Direccion,
			"descripcion":       session.Descripcion,
			"objetivos":         session.Objetivos,
			"materiales":        session.Materiales,
			"notas_terapeuta":   session.NotasTerapeuta,
			"terapeuta_nombre":  therapist.Nombres_Apellidos,
			"tipo_sesion":       session.TipoSesion,
			"modalidad":         session.Modalidad,
		})
	}

	totalPages := (int(total) + limit - 1) / limit

	c.JSON(http.StatusOK, gin.H{
		"message":      "Sesiones obtenidas exitosamente",
		"patient":      patient.NombresApellidos,
		"sessions":     sessionList,
		"total":        total,
		"page":         page,
		"total_pages":  totalPages,
		"has_previous": page > 1,
		"has_next":     page < totalPages,
	})
}

func (cpc *CaregiverPatientsController) GetMyProfile(c *gin.Context) {
	userID, exists := c.Get("user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	var caregiver models.Usuarios
	if err := cpc.db.Preload("Roles").First(&caregiver, userID).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener perfil"})
		return
	}

	var patientCount int64
	cpc.db.Model(&models.Patient{}).Where("cuidador_id = ?", userID).Count(&patientCount)

	var fotoBase64 string
	if caregiver.FotoMovil != "" {
		fotoBase64 = caregiver.FotoMovil
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Perfil obtenido exitosamente",
		"profile": gin.H{
			"id":                   caregiver.ID,
			"nombres_apellidos":    caregiver.Nombres_Apellidos,
			"correo":              caregiver.Correo,
			"telefono":            caregiver.Telefono,
			"tipo_documento":      caregiver.Tipo_Documento,
			"num_documento":       caregiver.Num_Documento,
			"sexo":                caregiver.Sexo,
			"foto_movil":          fotoBase64,
			"activo":              caregiver.Activo,
			"pacientes_asignados": patientCount,
			"ultimo_acceso":       caregiver.LastLoginAt,
		},
	})
}