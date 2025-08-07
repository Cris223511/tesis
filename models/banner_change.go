package models

import (
	"time"
)

type BannerChange struct {
	ID         uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID     uint      `gorm:"not null;index" json:"user_id"`
	ChangedAt  time.Time `gorm:"not null;index" json:"changed_at"`
	BannerData string    `gorm:"type:mediumtext;not null" json:"-"`
	CreatedAt  time.Time `json:"created_at"`
}

func (BannerChange) TableName() string {
	return "banner_changes"
}