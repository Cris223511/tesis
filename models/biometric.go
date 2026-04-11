package models

import (
	"time"
	"gorm.io/gorm"
)

type BiometricCredential struct {
	ID                uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID            uint      `gorm:"column:usuarios_id_usuario;not null;index:idx_user_biometric" json:"user_id"`
	CredentialID      []byte    `gorm:"type:varbinary(255);uniqueIndex;not null" json:"credential_id"`
	PublicKey         []byte    `gorm:"type:longblob;not null" json:"-"`
	SignCount         uint32    `gorm:"not null;default:0" json:"sign_count"`
	FingerprintHash   string    `gorm:"size:255;uniqueIndex" json:"-"`
	DeviceID          string    `gorm:"size:100;not null;index" json:"device_id"`
	DeviceName        string    `gorm:"size:100" json:"device_name"`
	DeviceType        string    `gorm:"size:50" json:"device_type"`
	FingerIndex       int       `gorm:"check:finger_index IN (1,2)" json:"finger_index"`
	Transports        string    `gorm:"size:255" json:"transports"`
	IsActive          bool      `gorm:"default:true" json:"is_active"`
	RegisteredAt      time.Time `gorm:"not null" json:"registered_at"`
	LastUsedAt        *time.Time `json:"last_used_at"`
	Usuario           Usuarios  `gorm:"foreignKey:UserID;references:ID" json:"-"`
}

type BiometricAttempt struct {
	ID            uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID        uint      `gorm:"column:usuarios_id_usuario;not null;index:idx_user_attempts" json:"user_id"`
	DeviceID      string    `gorm:"size:100;not null" json:"device_id"`
	Success       bool      `gorm:"not null" json:"success"`
	AttemptTime   time.Time `gorm:"not null;index" json:"attempt_time"`
	IPAddress     string    `gorm:"size:45" json:"ip_address"`
	FailureReason string    `gorm:"size:255" json:"failure_reason,omitempty"`
}

type BiometricLockout struct {
	ID              uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID          uint      `gorm:"column:usuarios_id_usuario;uniqueIndex;not null" json:"user_id"`
	LockoutStart    time.Time `gorm:"not null" json:"lockout_start"`
	LockoutEnd      time.Time `gorm:"not null;index" json:"lockout_end"`
	AttemptCount    int       `gorm:"not null" json:"attempt_count"`
	UnlockedManually bool     `gorm:"default:false" json:"unlocked_manually"`
	Usuario         Usuarios  `gorm:"foreignKey:UserID;references:ID" json:"-"`
}

func (BiometricCredential) TableName() string {
	return "biometric_credentials"
}

func (BiometricAttempt) TableName() string {
	return "biometric_attempts"
}

func (BiometricLockout) TableName() string {
	return "biometric_lockouts"
}

func (bc *BiometricCredential) BeforeCreate(tx *gorm.DB) error {
	if bc.FingerIndex > 0 {
		var count int64
		tx.Model(&BiometricCredential{}).Where("usuarios_id_usuario = ? AND is_active = ? AND finger_index > 0", bc.UserID, true).Count(&count)
		if count >= 2 {
			return gorm.ErrCheckConstraintViolated
		}
	}

	bc.RegisteredAt = time.Now()
	return nil
}

func GetActiveFingerprints(db *gorm.DB, userID uint) ([]BiometricCredential, error) {
	var credentials []BiometricCredential
	err := db.Where("usuarios_id_usuario = ? AND is_active = ? AND finger_index > 0", userID, true).Find(&credentials).Error
	return credentials, err
}

func GetRecentFailedAttempts(db *gorm.DB, userID uint, duration time.Duration) (int64, error) {
	var count int64
	since := time.Now().Add(-duration)
	err := db.Model(&BiometricAttempt{}).
		Where("usuarios_id_usuario = ? AND success = ? AND attempt_time > ?", userID, false, since).
		Count(&count).Error
	return count, err
}

func IsUserLocked(db *gorm.DB, userID uint) (bool, *BiometricLockout, error) {
	var lockout BiometricLockout
	err := db.Where("usuarios_id_usuario = ? AND lockout_end > ? AND unlocked_manually = ?",
		userID, time.Now(), false).First(&lockout).Error

	if err == gorm.ErrRecordNotFound {
		return false, nil, nil
	}

	if err != nil {
		return false, nil, err
	}

	return true, &lockout, nil
}