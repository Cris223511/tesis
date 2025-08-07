package models

import "time"
type OTP struct {
    ID         uint      `gorm:"primaryKey" json:"id"`
    UserID     uint      `json:"user_id"`
    Code       string    `json:"code" gorm:"size:6"`
    ExpiresAt  time.Time `json:"expires_at"`
    IsUsed     bool      `json:"is_used" gorm:"default:false"`
    Attempts   int       `json:"attempts" gorm:"default:0"`
    CreatedAt  time.Time `json:"created_at"`
    UpdatedAt  time.Time `json:"updated_at"`
}