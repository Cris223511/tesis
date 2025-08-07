package models

import (
	"time"
	"gorm.io/gorm"
)

type ProfileChange struct {
	ID         uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID     uint      `gorm:"not null;index" json:"user_id"`
	ChangedAt  time.Time `gorm:"not null;index" json:"changed_at"`
	OldEmail   string    `gorm:"type:varchar(100)" json:"old_email"`
	NewEmail   string    `gorm:"type:varchar(100)" json:"new_email"`
	OldPhone   string    `gorm:"type:varchar(20)" json:"old_phone"`
	NewPhone   string    `gorm:"type:varchar(20)" json:"new_phone"`
	ChangeType string    `gorm:"type:varchar(50);not null" json:"change_type"` // "email", "phone", "both"
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

func (ProfileChange) TableName() string {
	return "profile_changes"
}

// BeforeCreate se ejecuta antes de crear el registro
func (pc *ProfileChange) BeforeCreate(tx *gorm.DB) error {
	pc.ChangedAt = time.Now()
	return nil
}