package models

import "time"

type OTPResend struct {
	ID              uint       `gorm:"primaryKey"`
	UserID          uint       `json:"user_id"`
	ResendCount     int        `json:"resend_count" gorm:"default:0"`
	LastResendAt    time.Time  `json:"last_resend_at"`
	BlockedUntil    *time.Time `json:"blocked_until"`
	FailedAttempts  int        `json:"failed_attempts" gorm:"default:0"`
	OTPBlockedUntil *time.Time `gorm:"column:otp_blocked_until" json:"otp_blocked_until"`
	LockCycles      int        `json:"lock_cycles" gorm:"default:0"`
}
