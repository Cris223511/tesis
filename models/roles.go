package models

import ()

type Role struct {
	ID           uint   `gorm:"primaryKey" json:"id"`
	Name         string `gorm:"size:50;uniqueIndex;not null" json:"name"`
	UpdatedCount int    `gorm:"default:0" json:"-"`
}