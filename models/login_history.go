package models

import "time"


type LoginHistory struct {
	ID         uint      `gorm:"primaryKey"`
		UserID       uint   `gorm:"column:usuarios_id_usuario;not null;index"`
	IPAddress  string    `gorm:"type:varchar(45);not null"`
	UserAgent  string    `gorm:"type:varchar(255)"`
	LoginType  string    `gorm:"type:varchar(20)"` 
	Success    bool      `gorm:"default:true"`
	FailReason string    `gorm:"type:varchar(100)"`
	Location   string    `gorm:"type:varchar(100)"`
	DeviceInfo string    `gorm:"type:text"`
	CreatedAt  time.Time `gorm:"index"`
	Usuarios       Usuarios      `gorm:"foreignKey:UserID"`
}