package models

import "time"


type LoginHistory struct {
	ID         uint      `gorm:"primaryKey"`
	UserID     uint      `gorm:"not null;index"`
	IPAddress  string    `gorm:"type:varchar(45);not null"`
	UserAgent  string    `gorm:"type:varchar(255)"`
	LoginType  string    `gorm:"type:varchar(20)"` 
	Success    bool      `gorm:"default:true"`
	FailReason string    `gorm:"type:varchar(100)"`
	Location   string    `gorm:"type:varchar(100)"`
	DeviceInfo string    `gorm:"type:text"`
	CreatedAt  time.Time `gorm:"index"`
	User       User      `gorm:"foreignKey:UserID"`
}