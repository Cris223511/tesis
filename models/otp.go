package models

import (
	"strings"
	"time"
	"gorm.io/gorm"
)

type OTP struct {
	ID            uint      `gorm:"primaryKey" json:"id"`
	UserID        uint      `json:"user_id" gorm:"index"`
	Code          string    `json:"-" gorm:"size:6"`
	ExpiresAt     time.Time `json:"expires_at" gorm:"index"`
	IsUsed        bool      `json:"is_used" gorm:"default:false;index"`
	Attempts      int       `json:"attempts" gorm:"default:0"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`

	EncryptedCode string `json:"-" gorm:"size:255;column:encrypted_code"`
	CodeHash      string `json:"-" gorm:"size:64;column:code_hash"`
	Salt          string `json:"-" gorm:"size:32;column:salt"`
	ClientIP      string `json:"client_ip" gorm:"size:45;column:client_ip"`
	UserAgent     string `json:"-" gorm:"size:500;column:user_agent"`
	Purpose       string `json:"purpose" gorm:"size:50;default:'login';column:purpose"`
}

func (OTP) TableName() string {
	return "otps"
}

func (o *OTP) BeforeCreate(tx *gorm.DB) error {
	if o.CreatedAt.IsZero() {
		o.CreatedAt = time.Now()
	}
	if o.UpdatedAt.IsZero() {
		o.UpdatedAt = time.Now()
	}
	if o.Purpose == "" {
		o.Purpose = "login"
	}
	return nil
}

func (o *OTP) BeforeUpdate(tx *gorm.DB) error {
	o.UpdatedAt = time.Now()
	return nil
}

func (o *OTP) IsEncrypted() bool {
	return o.EncryptedCode != "" && o.CodeHash != "" && o.Salt != ""
}

func (o *OTP) VerifyLegacy(inputCode string) bool {
	return strings.ToUpper(inputCode) == strings.ToUpper(o.Code)
}