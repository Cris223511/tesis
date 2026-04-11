package dto

type BiometricRegisterRequest struct {
	FingerprintData string `json:"fingerprint_data" binding:"required"`
	DeviceID        string `json:"device_id" binding:"required"`
	DeviceName      string `json:"device_name"`
	FingerIndex     int    `json:"finger_index" binding:"required,min=1,max=2"`
}

type BiometricAuthRequest struct {
	FingerprintData string `json:"fingerprint_data" binding:"required"`
	DeviceID        string `json:"device_id" binding:"required"`
}

type BiometricStatusResponse struct {
	RegisteredFingerprints    int    `json:"registered_fingerprints"`
	CanRegisterMore          bool   `json:"can_register_more"`
	IsLocked                 bool   `json:"is_locked"`
	FailedAttempts          int64  `json:"failed_attempts"`
	RemainingAttempts        int64  `json:"remaining_attempts"`
	LockoutEndTime           string `json:"lockout_end_time,omitempty"`
	RemainingLockoutSeconds  int    `json:"remaining_lockout_seconds,omitempty"`
}

type BiometricResponse struct {
	Success bool   `json:"success"`
	Message string `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

type BiometricCredentialResponse struct {
	ID          uint   `json:"id"`
	FingerIndex int    `json:"finger_index"`
	DeviceName  string `json:"device_name"`
	DeviceType  string `json:"device_type"`
	IsActive    bool   `json:"is_active"`
	LastUsedAt  string `json:"last_used_at,omitempty"`
	RegisteredAt string `json:"registered_at"`
}

type BiometricDeleteRequest struct {
	FingerIndex int `json:"finger_index" binding:"required,min=1,max=2"`
}