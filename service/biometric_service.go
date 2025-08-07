package services

import (
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
		Where("user_id = ?", id).
		Find(&user.BiometricCreds).Error

	if err != nil {
		return nil, err
	}

	return user, nil
}

func (s *BioService) ListDevices(userID uint) ([]models.BiometricCredential, error) {
	var creds []models.BiometricCredential
	err := s.DB.Where("user_id = ?", userID).Order("last_used DESC").Find(&creds).Error
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
	result := s.DB.Where("user_id = ? AND credential_id = ?", userID, credentialID).Delete(&models.BiometricCredential{})
	if result.RowsAffected == 0 {
		return errors.New("credencial no encontrada o no pertenece al usuario")
	}
	return result.Error
}


func (s *BioService) RenameDevice(userID uint, credentialID []byte, newName string) error {
	result := s.DB.Model(&models.BiometricCredential{}).
		Where("user_id = ? AND credential_id = ?", userID, credentialID).
		Update("device_name", newName)
		
	if result.RowsAffected == 0 {
		return errors.New("credencial no encontrada o no pertenece al usuario")
	}
	return result.Error
}


func (s *BioService) UpdateLastUsed(credentialID []byte) error {
	return s.DB.Model(&models.BiometricCredential{}).
		Where("credential_id = ?", credentialID).
		Updates(map[string]interface{}{
			"last_used": time.Now(),
			"sign_count": gorm.Expr("sign_count + ?", 1),
		}).Error
}


func (s *BioService) SaveCredential(u *models.Usuarios, cred *webauthn.Credential, deviceName, deviceType, transports string) error {
	// Si no se proporciona un nombre, generar uno automático
	if deviceName == "" {
		var count int64
		s.DB.Model(&models.BiometricCredential{}).Where("user_id = ?", u.ID).Count(&count)
		deviceName = fmt.Sprintf("Dispositivo %d", count+1)
	}
	
	// Inferir el tipo de dispositivo si no se proporciona
	if deviceType == "" {
		deviceType = inferDeviceType(cred, transports)
	}
	
	return s.DB.Create(&models.BiometricCredential{
		UserID:       u.ID,
		CredentialID: cred.ID,
		PublicKey:    cred.PublicKey,
		SignCount:    cred.Authenticator.SignCount,
		DeviceName:   deviceName,
		DeviceType:   deviceType,
		Transports:   transports,
		LastUsed:     time.Now(),
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
	err := s.DB.Model(&models.BiometricCredential{}).Where("user_id = ?", userID).Count(&count).Error
	if err != nil {
		return false, 0, err
	}
	
	remaining := maxDevices - int(count)
	return remaining > 0, remaining, nil
}