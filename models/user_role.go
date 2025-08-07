package models

import "time"

type UserRole struct {
		UsuariosIDUsuario uint      `gorm:"column:usuarios_id_usuario;primaryKey"`
	RolesID           uint      `gorm:"column:roles_id;primaryKey"`
	CreatedAt         time.Time `gorm:"index"`
}