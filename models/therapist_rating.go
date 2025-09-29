package models

import (
	"time"
)

type TherapistRating struct {
	ID           uint          `gorm:"primaryKey;autoIncrement" json:"id"`
	SessionID    uint          `gorm:"not null;index" json:"session_id"`
	TherapistID  uint          `gorm:"not null;index" json:"therapist_id"`
	CaregiverID  uint          `gorm:"not null;index" json:"caregiver_id"`
	PatientID    uint          `gorm:"not null;index" json:"patient_id"`
	Rating       int           `gorm:"not null;check:rating >= 1 AND rating <= 5" json:"rating"`
	Comment      string        `gorm:"type:text" json:"comment"`
	CreatedAt    time.Time     `gorm:"index" json:"created_at"`
	UpdatedAt    time.Time     `json:"updated_at"`

	// Relationships
	Session      TherapySession `gorm:"foreignKey:SessionID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"session,omitempty"`
	Therapist    Usuarios       `gorm:"foreignKey:TherapistID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"therapist,omitempty"`
	Caregiver    Usuarios       `gorm:"foreignKey:CaregiverID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"caregiver,omitempty"`
	Patient      Patient        `gorm:"foreignKey:PatientID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"patient,omitempty"`
}

func (tr *TherapistRating) TableName() string {
	return "therapist_ratings"
}

// TherapistDisqualification tracks bad ratings to auto-delete therapists
type TherapistDisqualification struct {
	ID           uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	TherapistID  uint      `gorm:"not null;index" json:"therapist_id"`
	BadRatings   int       `gorm:"default:0" json:"bad_ratings"` // Count of 1-3 star ratings
	IsDeleted    bool      `gorm:"default:false" json:"is_deleted"`
	CreatedAt    time.Time `gorm:"index" json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`

	// Relationships
	Therapist    Usuarios  `gorm:"foreignKey:TherapistID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE" json:"therapist,omitempty"`
}

func (td *TherapistDisqualification) TableName() string {
	return "therapist_disqualifications"
}