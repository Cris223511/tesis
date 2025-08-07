package models

import "time"

type SecurityLog struct {
	ID         uint       `gorm:"primaryKey"`
	EventType  string     `gorm:"type:varchar(50);not null;index"`
	Username   string     `gorm:"type:varchar(50);index"`
	IPAddress  string     `gorm:"type:varchar(45);index"`
	UserAgent  string     `gorm:"type:varchar(255)"`
	Details    string     `gorm:"type:text"`
	Severity   string     `gorm:"type:varchar(20);default:'info'"` // info, warning, alert, critical
	Resolved   bool       `gorm:"default:false"`
	ResolvedBy uint      
	ResolvedAt *time.Time
	CreatedAt  time.Time  `gorm:"index"`
}