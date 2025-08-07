package models

import (
	"time"
)

type UserRelationship struct {
	ID        uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	ParentID  uint      `gorm:"not null;index:idx_parent_child,unique" json:"parent_id"`
	ChildID   uint      `gorm:"not null;index:idx_parent_child,unique" json:"child_id"`
	Parent    Usuarios  `gorm:"foreignKey:ParentID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"parent,omitempty"`
	Child     Usuarios  `gorm:"foreignKey:ChildID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"child,omitempty"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

func (UserRelationship) TableName() string {
	return "user_relationships"
}