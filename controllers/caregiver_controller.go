package controllers

import (
	"net/http"
	"strconv"
	"usuarios/models"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type CaregiverController struct {
	db *gorm.DB
}

func NewCaregiverController(db *gorm.DB) *CaregiverController {
	return &CaregiverController{db: db}
}

func (cc *CaregiverController) GetCaregivers(c *gin.Context) {
	userID, exists := c.Get("user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	var currentUser models.Usuarios
	if err := cc.db.Preload("Roles").First(&currentUser, userID).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuario"})
		return
	}

	isAdmin := false
	for _, role := range currentUser.Roles {
		if role.ID == 1 {
			isAdmin = true
			break
		}
	}

	if !isAdmin {
		c.JSON(http.StatusForbidden, gin.H{"error": "Acceso denegado. Solo administradores pueden ver todos los cuidadores"})
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

	var caregivers []models.Usuarios
	var total int64

	query := cc.db.Model(&models.Usuarios{}).Where("role_id = ?", 4)

	query.Count(&total)

	if err := query.
		Select("idusuario", "nombres_apellidos", "usuario", "correo", "telefono",
			"tipo_documento", "num_documento", "sexo", "foto_movil", "activo", "created_at").
		Limit(limit).
		Offset(offset).
		Find(&caregivers).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener cuidadores",
		})
		return
	}

	var caregiverList []gin.H
	for _, caregiver := range caregivers {
		var patientCount int64
		cc.db.Model(&models.Patient{}).Where("cuidador_id = ?", caregiver.ID).Count(&patientCount)

		caregiverList = append(caregiverList, gin.H{
			"id":                   caregiver.ID,
			"nombres_apellidos":    caregiver.Nombres_Apellidos,
			"correo":              caregiver.Correo,
			"telefono":            caregiver.Telefono,
			"tipo_documento":      caregiver.Tipo_Documento,
			"num_documento":       caregiver.Num_Documento,
			"sexo":                caregiver.Sexo,
			"foto_movil":          caregiver.FotoMovil,
			"activo":              caregiver.Activo,
			"fecha_creacion":      caregiver.CreatedAt,
			"pacientes_asignados": patientCount,
		})
	}

	totalPages := (int(total) + limit - 1) / limit

	c.JSON(http.StatusOK, gin.H{
		"message":      "Cuidadores obtenidos exitosamente",
		"caregivers":   caregiverList,
		"total":        total,
		"page":         page,
		"total_pages":  totalPages,
		"has_previous": page > 1,
		"has_next":     page < totalPages,
	})
}

func (cc *CaregiverController) GetCaregiver(c *gin.Context) {
	id := c.Param("id")

	var caregiver models.Usuarios
	if err := cc.db.Where("id = ? AND role_id = ?", id, 4).First(&caregiver).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "Cuidador no encontrado"})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener cuidador"})
		}
		return
	}

	var patients []models.Patient
	cc.db.Where("cuidador_id = ?", caregiver.ID).Find(&patients)

	var patientList []gin.H
	for _, patient := range patients {
		patientList = append(patientList, gin.H{
			"id":                patient.ID,
			"serial_id":         patient.SerialID,
			"nombres_apellidos": patient.NombresApellidos,
			"sexo":              patient.Sexo,
			"activo":            patient.Activo,
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Cuidador obtenido exitosamente",
		"caregiver": gin.H{
			"id":                   caregiver.ID,
			"nombres_apellidos":    caregiver.Nombres_Apellidos,
			"correo":              caregiver.Correo,
			"telefono":            caregiver.Telefono,
			"tipo_documento":      caregiver.Tipo_Documento,
			"num_documento":       caregiver.Num_Documento,
			"sexo":                caregiver.Sexo,
			"foto_movil":          caregiver.FotoMovil,
			"activo":              caregiver.Activo,
			"fecha_creacion":      caregiver.CreatedAt,
			"pacientes_asignados": patientList,
		},
	})
}

func (cc *CaregiverController) CreateCaregiver(c *gin.Context) {
	userID, exists := c.Get("user_id")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	var currentUser models.Usuarios
	if err := cc.db.Preload("Roles").First(&currentUser, userID).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuario"})
		return
	}

	isAdmin := false
	for _, role := range currentUser.Roles {
		if role.ID == 1 {
			isAdmin = true
			break
		}
	}

	if !isAdmin {
		c.JSON(http.StatusForbidden, gin.H{"error": "Acceso denegado. Solo administradores pueden crear cuidadores"})
		return
	}

	var req struct {
		Nombres       string `json:"nombres_apellidos" binding:"required"`
		Correo        string `json:"correo" binding:"required,email"`
		Usuario       string `json:"usuario" binding:"required"`
		Telefono      string `json:"telefono"`
		TipoDocumento string `json:"tipo_documento" binding:"required"`
		NumDocumento  string `json:"num_documento" binding:"required"`
		Sexo          string `json:"sexo" binding:"required"`
		FotoMovil     string `json:"foto_movil"`
		Password      string `json:"password" binding:"required"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	var existingUser models.Usuarios
	if err := cc.db.Where("correo = ? OR usuario = ? OR num_documento = ?", req.Correo, req.Usuario, req.NumDocumento).First(&existingUser).Error; err == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "El correo, usuario o documento ya existe"})
		return
	}

	hashedPassword, err := utils.HashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al encriptar contraseña"})
		return
	}

	newCaregiver := models.Usuarios{
		Nombres_Apellidos: req.Nombres,
		Usuario:           req.Usuario,
		Correo:            req.Correo,
		Telefono:          req.Telefono,
		Tipo_Documento:    req.TipoDocumento,
		Num_Documento:     req.NumDocumento,
		Sexo:              req.Sexo,
		FotoMovil:         req.FotoMovil,
		Contrasena:        hashedPassword,
		Activo:            true,
	}

	if err := cc.db.Create(&newCaregiver).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al crear cuidador"})
		return
	}

	var caregiverRole models.Role
	if err := cc.db.Where("id = ?", 4).First(&caregiverRole).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener rol de cuidador"})
		return
	}

	userRole := models.UserRole{
		UsuariosIDUsuario: newCaregiver.ID,
		RolesID:           caregiverRole.ID,
	}

	if err := cc.db.Create(&userRole).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al asignar rol"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Cuidador creado exitosamente",
		"caregiver": gin.H{
			"id":                newCaregiver.ID,
			"nombres_apellidos": newCaregiver.Nombres_Apellidos,
			"usuario":           newCaregiver.Usuario,
			"correo":            newCaregiver.Correo,
			"telefono":          newCaregiver.Telefono,
			"tipo_documento":    newCaregiver.Tipo_Documento,
			"num_documento":     newCaregiver.Num_Documento,
			"sexo":              newCaregiver.Sexo,
			"activo":            newCaregiver.Activo,
		},
	})
}

func (cc *CaregiverController) UpdateCaregiver(c *gin.Context) {
	id := c.Param("id")

	var caregiver models.Usuarios
	if err := cc.db.Where("id = ? AND role_id = ?", id, 4).First(&caregiver).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "Cuidador no encontrado"})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al buscar cuidador"})
		}
		return
	}

	var req struct {
		Nombres       string `json:"nombres_apellidos"`
		Correo        string `json:"correo"`
		Telefono      string `json:"telefono"`
		TipoDocumento string `json:"tipo_documento"`
		NumDocumento  string `json:"num_documento"`
		Sexo          string `json:"sexo"`
		FotoMovil     string `json:"foto_movil"`
		Activo        *bool  `json:"activo"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Función de actualizar cuidador no implementada completamente",
	})
}

func (cc *CaregiverController) DeleteCaregiver(c *gin.Context) {
	id := c.Param("id")

	var caregiver models.Usuarios
	if err := cc.db.Where("id = ? AND role_id = ?", id, 4).First(&caregiver).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "Cuidador no encontrado"})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al buscar cuidador"})
		}
		return
	}

	caregiver.Activo = false
	if err := cc.db.Save(&caregiver).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al desactivar cuidador"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Cuidador desactivado exitosamente",
	})
}
