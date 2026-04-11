package services

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
	"usuarios/models"

	"github.com/duo-labs/webauthn/webauthn"
	"gorm.io/gorm"
)



type BioService struct {
	DB        *gorm.DB
	Wa        *webauthn.WebAuthn
	UserRepo  UserService
}


func NewBioService(db *gorm.DB, userService UserService, origin, rpID, rpName string) (*BioService, error) {
	w, err := webauthn.New(&webauthn.Config{
		RPDisplayName: rpName,
		RPID:          rpID,
		RPOrigin:      origin,
	})
	if err != nil {
		return nil, err
	}

	return &BioService{
		DB:       db,
		Wa:       w,
		UserRepo: userService,
	}, nil
}
func (s *BioService) GetUserForWebAuthn(id uint) (*models.Usuarios, error) {
	user, err := s.UserRepo.GetUserByID(id)
	if err != nil {
		return nil, err
	}

	err = s.DB.Model(&models.BiometricCredential{}).
		Where("usuarios_id_usuario = ?", id).
		Find(&user.BiometricCreds).Error

	if err != nil {
		return nil, err
	}

	return user, nil
}

func (s *BioService) ListDevices(userID uint) ([]models.BiometricCredential, error) {
	var creds []models.BiometricCredential
	err := s.DB.Where("usuarios_id_usuario = ?", userID).Order("last_used_at DESC").Find(&creds).Error
	if err != nil {
		return nil, err
	}
	
	// No devolver datos sensibles como la clave pública
	for i := range creds {
		creds[i].PublicKey = nil
	}
	
	return creds, nil
}


func (s *BioService) DeleteDevice(userID uint, credentialID []byte) error {
	result := s.DB.Where("usuarios_id_usuario = ? AND credential_id = ?", userID, credentialID).Delete(&models.BiometricCredential{})
	if result.RowsAffected == 0 {
		return errors.New("credencial no encontrada o no pertenece al usuario")
	}
	return result.Error
}


func (s *BioService) RenameDevice(userID uint, credentialID []byte, newName string) error {
	result := s.DB.Model(&models.BiometricCredential{}).
		Where("usuarios_id_usuario = ? AND credential_id = ?", userID, credentialID).
		Update("device_name", newName)

	if result.RowsAffected == 0 {
		return errors.New("credencial no encontrada o no pertenece al usuario")
	}
	return result.Error
}


func (s *BioService) UpdateLastUsed(credentialID []byte) error {
	now := time.Now()
	return s.DB.Model(&models.BiometricCredential{}).
		Where("credential_id = ?", credentialID).
		Updates(map[string]interface{}{
			"last_used_at": &now,
			"sign_count": gorm.Expr("sign_count + ?", 1),
		}).Error
}


func (s *BioService) SaveCredential(u *models.Usuarios, cred *webauthn.Credential, deviceName, deviceType, transports string) error {
	// Si no se proporciona un nombre, generar uno automático
	if deviceName == "" {
		var count int64
		s.DB.Model(&models.BiometricCredential{}).Where("usuarios_id_usuario = ?", u.ID).Count(&count)
		deviceName = fmt.Sprintf("Dispositivo %d", count+1)
	}
	
	// Inferir el tipo de dispositivo si no se proporciona
	if deviceType == "" {
		deviceType = inferDeviceType(cred, transports)
	}
	
	now := time.Now()
	return s.DB.Create(&models.BiometricCredential{
		UserID:       u.ID,
		CredentialID: cred.ID,
		PublicKey:    cred.PublicKey,
		SignCount:    cred.Authenticator.SignCount,
		DeviceName:   deviceName,
		DeviceType:   deviceType,
		Transports:   transports,
		LastUsedAt:   &now,
		RegisteredAt: now,
	}).Error
}

func inferDeviceType(cred *webauthn.Credential, transports string) string {
	if strings.Contains(strings.ToLower(transports), "internal") {
		return "platform"
	} else if strings.Contains(strings.ToLower(transports), "usb") {
		return "security_key"
	}
	return "unknown"
}


func (s *BioService) CanRegisterMoreDevices(userID uint, maxDevices int) (bool, int, error) {
	var count int64
	err := s.DB.Model(&models.BiometricCredential{}).Where("usuarios_id_usuario = ?", userID).Count(&count).Error
	if err != nil {
		return false, 0, err
	}

	remaining := maxDevices - int(count)
	return remaining > 0, remaining, nil
}

const (
	MaxAttempts        = 5
	LockoutDuration    = 30 * time.Minute
	AttemptWindow      = 15 * time.Minute
	MaxFingerprints    = 2
)

func (s *BioService) RegisterFingerprint(userID uint, fingerprintData string, deviceID string, deviceName string, fingerIndex int) error {
	isLocked, _, err := models.IsUserLocked(s.DB, userID)
	if err != nil {
		return err
	}
	if isLocked {
		return errors.New("usuario bloqueado temporalmente")
	}

	credentials, err := models.GetActiveFingerprints(s.DB, userID)
	if err != nil {
		return err
	}

	if len(credentials) >= MaxFingerprints {
		return errors.New("maximo de huellas registradas alcanzado")
	}

	for _, cred := range credentials {
		if cred.FingerIndex == fingerIndex {
			return errors.New("indice de huella ya registrado")
		}
	}

	hash := hashFingerprint(fingerprintData)

	var existingCred models.BiometricCredential
	if err := s.DB.Where("fingerprint_hash = ?", hash).First(&existingCred).Error; err == nil {
		return errors.New("huella ya registrada en el sistema")
	}

	credential := models.BiometricCredential{
		UserID:          userID,
		FingerprintHash: hash,
		DeviceID:        deviceID,
		DeviceName:      deviceName,
		DeviceType:      "fingerprint",
		FingerIndex:     fingerIndex,
		IsActive:        true,
		RegisteredAt:    time.Now(),
	}

	return s.DB.Create(&credential).Error
}

func (s *BioService) AuthenticateFingerprint(userID uint, fingerprintData string, deviceID string, ipAddress string) (bool, error) {
	isLocked, lockout, err := models.IsUserLocked(s.DB, userID)
	if err != nil {
		return false, err
	}

	if isLocked {
		remainingTime := time.Until(lockout.LockoutEnd)
		return false, fmt.Errorf("cuenta bloqueada por %v", remainingTime.Round(time.Minute))
	}

	failedAttempts, err := models.GetRecentFailedAttempts(s.DB, userID, AttemptWindow)
	if err != nil {
		return false, err
	}

	if failedAttempts >= MaxAttempts {
		lockout := models.BiometricLockout{
			UserID:       userID,
			LockoutStart: time.Now(),
			LockoutEnd:   time.Now().Add(LockoutDuration),
			AttemptCount: int(failedAttempts),
		}
		s.DB.Create(&lockout)
		return false, errors.New("maximo de intentos excedido, cuenta bloqueada")
	}

	hash := hashFingerprint(fingerprintData)

	var credential models.BiometricCredential
	err = s.DB.Where("usuarios_id_usuario = ? AND fingerprint_hash = ? AND is_active = ?",
		userID, hash, true).First(&credential).Error

	attempt := models.BiometricAttempt{
		UserID:      userID,
		DeviceID:    deviceID,
		AttemptTime: time.Now(),
		IPAddress:   ipAddress,
		Success:     err == nil,
	}

	if err != nil {
		if err == gorm.ErrRecordNotFound {
			attempt.FailureReason = "huella no reconocida"
		} else {
			attempt.FailureReason = "error de autenticacion"
		}
		s.DB.Create(&attempt)
		return false, errors.New(attempt.FailureReason)
	}

	s.DB.Create(&attempt)

	now := time.Now()
	credential.LastUsedAt = &now
	s.DB.Save(&credential)

	return true, nil
}

func (s *BioService) GetUserBiometricStatus(userID uint) (map[string]interface{}, error) {
	credentials, err := models.GetActiveFingerprints(s.DB, userID)
	if err != nil {
		return nil, err
	}

	isLocked, lockout, err := models.IsUserLocked(s.DB, userID)
	if err != nil {
		return nil, err
	}

	failedAttempts, err := models.GetRecentFailedAttempts(s.DB, userID, AttemptWindow)
	if err != nil {
		return nil, err
	}

	status := map[string]interface{}{
		"registered_fingerprints": len(credentials),
		"can_register_more":      len(credentials) < MaxFingerprints,
		"is_locked":             isLocked,
		"failed_attempts":       failedAttempts,
		"remaining_attempts":    MaxAttempts - failedAttempts,
	}

	if isLocked && lockout != nil {
		status["lockout_end_time"] = lockout.LockoutEnd
		status["remaining_lockout_seconds"] = int(time.Until(lockout.LockoutEnd).Seconds())
	}

	return status, nil
}

func (s *BioService) UnlockUser(userID uint) error {
	return s.DB.Model(&models.BiometricLockout{}).
		Where("usuarios_id_usuario = ?", userID).
		Update("unlocked_manually", true).Error
}

func hashFingerprint(data string) string {
	hash := sha256.Sum256([]byte(data))
	return hex.EncodeToString(hash[:])
}