package models

import (
	"time"

)

type UserDeviceIP struct {
	ID        uint      `gorm:"primaryKey"`
	UserID    uint      `gorm:"index;not null"`
	Device    string    `gorm:"size:100;not null"`
	IP        string    `gorm:"size:45;not null"`
	CreatedAt time.Time
}
