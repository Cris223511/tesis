package controllers

import (
	"net/http"
	"strconv"
	"time"
	"usuarios/service"

	"github.com/gin-gonic/gin"
)

type AdminController struct {
	AdminService *services.AdminService
}

func NewAdminController(adminService *services.AdminService) *AdminController {
	return &AdminController{
		AdminService: adminService,
	}
}

func (ctrl *AdminController) GetAdminStats(c *gin.Context) {
	stats, err := ctrl.AdminService.GetAdminStats()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener estadísticas administrativas",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Estadísticas obtenidas exitosamente",
		"data":    stats,
	})
}

func (ctrl *AdminController) GetTherapists(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "100"))

	activo := true

	filter := services.UserFilter{
		Role:   "TR",
		Activo: &activo,
		Page:   page,
		Limit:  limit,
	}

	users, total, err := ctrl.AdminService.GetUsersByRole(filter)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener terapeutas",
		})
		return
	}

	therapists := make([]gin.H, 0, len(users))
	for _, user := range users {
		therapists = append(therapists, gin.H{
			"id":               user.ID,
			"nombres_apellidos": user.Nombres_Apellidos,
			"correo":           user.Correo,
			"usuario":          user.Usuario,
			"telefono":         user.Telefono,
			"activo":           user.Activo,
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Terapeutas obtenidos exitosamente",
		"data": gin.H{
			"therapists":  therapists,
			"total":       total,
			"page":        page,
			"limit":       limit,
			"total_pages": (int(total) + limit - 1) / limit,
		},
	})
}

func (ctrl *AdminController) GetSessionStats(c *gin.Context) {
	var dateFrom, dateTo *time.Time

	if dateFromStr := c.Query("date_from"); dateFromStr != "" {
		if parsed, err := time.Parse("2006-01-02", dateFromStr); err == nil {
			dateFrom = &parsed
		}
	}

	if dateToStr := c.Query("date_to"); dateToStr != "" {
		if parsed, err := time.Parse("2006-01-02", dateToStr); err == nil {
			dateTo = &parsed
		}
	}

	stats, err := ctrl.AdminService.GetSessionStats(dateFrom, dateTo)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error al obtener estadísticas de sesiones",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Estadísticas de sesiones obtenidas exitosamente",
		"data":    stats,
	})
}
