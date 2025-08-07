package models

import "time"
type OTPResend struct {
    ID              uint      `gorm:"primaryKey"`
    UserID          uint      `json:"user_id"`
    ResendCount     int       `json:"resend_count" gorm:"default:0"`
    LastResendAt    time.Time `json:"last_resend_at"`
    BlockedUntil    *time.Time `json:"blocked_until"`
}
