package services

import (
	"errors"
	"usuarios/models"
	"gorm.io/gorm"
)



type RoleService interface {
	CreateRole(name string) (*models.Role, error)
	GetAllRoles() ([]models.Role, error)
	GetAvailableRoles() ([]models.Role, error)
	GetRoleByName(name string) (*models.Role, error)
	UpdateRole(id uint, newName string) error
	DeleteRole(id uint) error
}

type roleService struct {
	db *gorm.DB
}

func NewRoleService(db *gorm.DB) RoleService {
	return &roleService{db: db}
}

// Crear un nuevo rol
func (s *roleService) CreateRole(name string) (*models.Role, error) {
	// Verificar si el rol ya existe
	var existingRole models.Role
	if err := s.db.Where("name = ?", name).First(&existingRole).Error; err == nil {
		return nil, errors.New("el rol ya existe")
	}

	role := models.Role{Name: name}
	if err := s.db.Create(&role).Error; err != nil {
		return nil, err
	}
	return &role, nil
}

// Obtener todos los roles
func (s *roleService) GetAllRoles() ([]models.Role, error) {
	var roles []models.Role
	if err := s.db.Find(&roles).Error; err != nil {
		return nil, err
	}
	return roles, nil
}

// Obtener roles disponibles para asignación (excluye AD)
func (s *roleService) GetAvailableRoles() ([]models.Role, error) {
	var roles []models.Role
	if err := s.db.Where("name != ?", "AD").Find(&roles).Error; err != nil {
		return nil, err
	}
	return roles, nil
}

// Buscar un rol por nombre
func (s *roleService) GetRoleByName(name string) (*models.Role, error) {
	var role models.Role
	if err := s.db.Where("name = ?", name).First(&role).Error; err != nil {
		return nil, errors.New("rol no encontrado")
	}
	return &role, nil
}

// Actualizar un rol (se puede actualizar solo dos veces)
func (s *roleService) UpdateRole(id uint, newName string) error {
	var role models.Role
	if err := s.db.First(&role, id).Error; err != nil {
		return errors.New("rol no encontrado")
	}

	// Verificar que no haya un rol con el mismo nombre
	var existingRole models.Role
	if err := s.db.Where("name = ?", newName).First(&existingRole).Error; err == nil {
		return errors.New("ya existe un rol con este nombre")
	}

	// Lógica para permitir solo dos actualizaciones
	if role.UpdatedCount >= 2 {
		return errors.New("el rol ha alcanzado el límite de modificaciones")
	}

	role.Name = newName
	role.UpdatedCount++
	return s.db.Save(&role).Error
}

// Eliminar un rol (no se puede volver a crear un rol eliminado)
func (s *roleService) DeleteRole(id uint) error {
	var role models.Role
	if err := s.db.First(&role, id).Error; err != nil {
		return errors.New("rol no encontrado")
	}

	// Marcar el rol como eliminado en lugar de eliminarlo físicamente
	role.Name = "DELETED_" + role.Name
	return s.db.Save(&role).Error
}
