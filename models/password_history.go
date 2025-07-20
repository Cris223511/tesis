package models

import "time"

type PasswordHistory struct {
	ID           uint      `gorm:"primaryKey"`
		UserID       uint   `gorm:"column:usuarios_id_usuario;not null;index"`
	PasswordHash string    `gorm:"type:varchar(255);not null"`
	ChangedBy    uint      `gorm:"index"` 
	ChangeReason string    `gorm:"type:varchar(100)"` 
	IPAddress    string    `gorm:"type:varchar(45)"`
	CreatedAt    time.Time `gorm:"index"`
	Usuarios         Usuarios      `gorm:"foreignKey:UserID"`
}