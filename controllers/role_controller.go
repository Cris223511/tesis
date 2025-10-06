package controllers

import (
	"net/http"
	"usuarios/service"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
)

type RoleController struct {
	RoleService services.RoleService
}

func NewRoleController(roleService services.RoleService) *RoleController {
	return &RoleController{RoleService: roleService}
}


// GetHelloWorld godoc
// @Summary      Devuelve un saludo
// @Description  Endpoint de ejemplo que retorna "Hello World"
// @Tags         Ejemplos
// @Produce      json
// @Success      200 {string} string "Hello World"
// @Router       /hello [get]

// Crear un nuevo rol
func (ctrl *RoleController) CreateRole(c *gin.Context) {
	var request struct {
		Name string `json:"name"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	role, err := ctrl.RoleService.CreateRole(request.Name)
	if err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, role)
}

// Obtener todos los roles disponibles
func (ctrl *RoleController) GetAllRoles(c *gin.Context) {
	roles, err := ctrl.RoleService.GetAvailableRoles()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener los roles"})
		return
	}

	c.JSON(http.StatusOK, roles)
}

// Actualizar un rol (solo permitido dos veces)
func (ctrl *RoleController) UpdateRole(c *gin.Context) {
	var request struct {
		NewName string `json:"new_name"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	id := c.Param("id")
	roleID, err := utils.ParseID(id)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	if err := ctrl.RoleService.UpdateRole(roleID, request.NewName); err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Rol actualizado exitosamente"})
}

// Eliminar un rol (no se puede volver a crear un rol eliminado)
func (ctrl *RoleController) DeleteRole(c *gin.Context) {
	id := c.Param("id")
	roleID, err := utils.ParseID(id)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	if err := ctrl.RoleService.DeleteRole(roleID); err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Rol eliminado exitosamente"})
}
