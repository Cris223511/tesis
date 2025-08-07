package models

import (
	"time"
)

type PhotoChange struct {
	ID        uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID    uint      `gorm:"not null;index" json:"user_id"`
	ChangedAt time.Time `gorm:"not null;index" json:"changed_at"`
	PhotoData string    `gorm:"type:mediumtext;not null" json:"-"`
	CreatedAt time.Time `json:"created_at"`
}

func (PhotoChange) TableName() string {
	return "photo_changes"
}