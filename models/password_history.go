package models

import "time"

type PasswordHistory struct {
	ID           uint      `gorm:"primaryKey"`
	UserID       uint      `gorm:"not null;index"`
	PasswordHash string    `gorm:"type:varchar(255);not null"`
	ChangedBy    uint      `gorm:"index"` 
	ChangeReason string    `gorm:"type:varchar(100)"` 
	IPAddress    string    `gorm:"type:varchar(45)"`
	CreatedAt    time.Time `gorm:"index"`
	User         User      `gorm:"foreignKey:UserID"`
}