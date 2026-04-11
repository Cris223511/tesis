package services

import (
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"math/big"
	"strings"
	"time"
	"usuarios/models"
	"usuarios/utils"

	"gorm.io/gorm"
)

type OTPService interface {
	GenerateOTP(userID uint) (*models.OTP, error)
	VerifyOTP(userID uint, code string) error
	ResendOTP(userID uint) error
	CleanupExpiredOTPs() error
	SaveOTP(userID uint, code string) error
	ValidateOTP(userID uint, code string) (bool, error)
	CheckOTP(userID uint, code string) (bool, error)
	InvalidateUserOTPs(userID uint) error
}

type otpService struct {
	db *gorm.DB
}

func NewOTPService(db *gorm.DB) OTPService {
	return &otpService{db: db}
}

const (
	OTP_LENGTH          = 6
	OTP_EXPIRY_MINUTES  = 5
	MAX_OTP_ATTEMPTS    = 5
	RESEND_COOLDOWN     = 5 * time.Minute
	OTP_BLOCK_DURATION  = 30 * time.Minute
	MAX_OTP_LOCK_CYCLES = 2
)

func (s *otpService) GenerateOTP(userID uint) (*models.OTP, error) {
	now := time.Now()

	s.db.Model(&models.OTP{}).
		Where("user_id = ? AND is_used = ? AND expires_at > ?", userID, false, time.Now()).
		Update("is_used", true)

	code := s.generateRandomCode()

	otp := &models.OTP{
		UserID:    userID,
		Code:      code,
		ExpiresAt: now.Add(OTP_EXPIRY_MINUTES * time.Minute),
		IsUsed:    false,
		Attempts:  0,
	}

	if err := s.db.Create(otp).Error; err != nil {
		return nil, errors.New("error al generar OTP")
	}

	var user models.Usuarios
	if err := s.db.First(&user, userID).Error; err != nil {
		return nil, errors.New("usuario no encontrado")
	}

	state, err := s.getOrCreateOTPState(userID)
	if err == nil {
		s.db.Model(state).Updates(map[string]interface{}{
			"last_resend_at": now,
		})
	}

	go utils.SendOTPEmail(user.Correo, code)

	log.Printf("[OTP] Código generado para usuario %d: %s", userID, code)

	return otp, nil
}

func (s *otpService) VerifyOTP(userID uint, code string) error {
	var user models.Usuarios
	if err := s.db.First(&user, userID).Error; err != nil {
		return errors.New("usuario no encontrado")
	}
	if !user.Activo {
		return errors.New("cuenta desactivada")
	}

	state, err := s.getOrCreateOTPState(userID)
	if err != nil {
		return errors.New("error al verificar estado del OTP")
	}

	if state.OTPBlockedUntil != nil && state.OTPBlockedUntil.After(time.Now()) {
		remaining := int(time.Until(*state.OTPBlockedUntil).Minutes())
		if remaining < 1 {
			remaining = 1
		}
		return fmt.Errorf("otp bloqueado, intente en %d minutos", remaining)
	}

	if state.OTPBlockedUntil != nil && state.OTPBlockedUntil.Before(time.Now()) {
		s.db.Model(state).Updates(map[string]interface{}{
			"failed_attempts":   0,
			"otp_blocked_until": nil,
		})
		state.FailedAttempts = 0
		state.OTPBlockedUntil = nil
	}

	var otp models.OTP
	err = s.db.Where("user_id = ? AND is_used = ?", userID, false).
		Order("created_at DESC").
		First(&otp).Error

	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			if _, generateErr := s.GenerateOTP(userID); generateErr != nil {
				return errors.New("código OTP expirado y no se pudo reenviar automáticamente")
			}
			return errors.New("otp expirado, se generó y envió uno nuevo automáticamente")
		}
		return errors.New("error al verificar OTP")
	}

	if otp.ExpiresAt.Before(time.Now()) {
		s.db.Model(&otp).Update("is_used", true)
		if _, generateErr := s.GenerateOTP(userID); generateErr != nil {
			return errors.New("código OTP expirado y no se pudo reenviar automáticamente")
		}
		return errors.New("otp expirado, se generó y envió uno nuevo automáticamente")
	}

	s.db.Model(&otp).Update("attempts", otp.Attempts+1)

	if strings.ToUpper(code) != strings.ToUpper(otp.Code) {
		return s.registerFailedOTPAttempt(&user, state, &otp)
	}

	s.db.Model(&otp).Update("is_used", true)
	s.resetOTPFailures(state)

	log.Printf("[OTP] Código verificado exitosamente para usuario %d", userID)

	return nil
}

func (s *otpService) ResendOTP(userID uint) error {
	state, err := s.getOrCreateOTPState(userID)
	if err != nil {
		return errors.New("error al verificar límites")
	}

	if state.OTPBlockedUntil != nil && state.OTPBlockedUntil.After(time.Now()) {
		remaining := int(time.Until(*state.OTPBlockedUntil).Minutes())
		if remaining < 1 {
			remaining = 1
		}
		return fmt.Errorf("otp bloqueado, intente en %d minutos", remaining)
	}

	if !state.LastResendAt.IsZero() && time.Since(state.LastResendAt) < RESEND_COOLDOWN {
		remaining := RESEND_COOLDOWN - time.Since(state.LastResendAt)
		return fmt.Errorf("espera %d segundos antes de reenviar", int(remaining.Seconds()))
	}

	_, err = s.GenerateOTP(userID)
	if err != nil {
		return err
	}

	s.db.Model(state).Updates(map[string]interface{}{
		"resend_count":   state.ResendCount + 1,
		"last_resend_at": time.Now(),
		"blocked_until":  nil,
	})

	log.Printf("[OTP] Código reenviado para usuario %d", userID)

	return nil
}

func (s *otpService) generateRandomCode() string {
	const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	code := make([]byte, OTP_LENGTH)

	for i := 0; i < OTP_LENGTH; i++ {
		num, _ := rand.Int(rand.Reader, big.NewInt(int64(len(chars))))
		code[i] = chars[num.Int64()]
	}

	return string(code)
}

func (s *otpService) sendOTPEmail(email, name, otp string) {
	// Usar directamente la función que ya funciona para verificación 2FA
	utils.SendOTPEmail(email, otp)
}

func (s *otpService) CleanupExpiredOTPs() error {
	return s.db.Where("expires_at < ? OR is_used = ?", time.Now(), true).Delete(&models.OTP{}).Error
}

func (s *otpService) SaveOTP(userID uint, code string) error {
	s.db.Model(&models.OTP{}).
		Where("user_id = ? AND is_used = ? AND expires_at > ?", userID, false, time.Now()).
		Update("is_used", true)

	otp := &models.OTP{
		UserID:    userID,
		Code:      code,
		ExpiresAt: time.Now().Add(OTP_EXPIRY_MINUTES * time.Minute),
		IsUsed:    false,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	return s.db.Create(otp).Error
}

func (s *otpService) ValidateOTP(userID uint, code string) (bool, error) {
	var otp models.OTP
	err := s.db.Where("user_id = ? AND code = ? AND is_used = ? AND expires_at > ?",
		userID, code, false, time.Now()).First(&otp).Error

	if err != nil {
		return false, err
	}

	s.db.Model(&otp).Update("is_used", true)
	return true, nil
}

func (s *otpService) CheckOTP(userID uint, code string) (bool, error) {
	var otp models.OTP
	err := s.db.Where("user_id = ? AND code = ? AND is_used = ? AND expires_at > ?",
		userID, code, false, time.Now()).First(&otp).Error

	if err != nil {
		return false, err
	}

	// NO marcar como usado, solo verificar que existe y es válido
	return true, nil
}

func (s *otpService) InvalidateUserOTPs(userID uint) error {
	return s.db.Model(&models.OTP{}).
		Where("user_id = ? AND is_used = ?", userID, false).
		Update("is_used", true).Error
}

func (s *otpService) getOrCreateOTPState(userID uint) (*models.OTPResend, error) {
	var state models.OTPResend
	err := s.db.Where("user_id = ?", userID).First(&state).Error
	if err == nil {
		return &state, nil
	}
	if !errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, err
	}

	state = models.OTPResend{
		UserID:       userID,
		LastResendAt: time.Time{},
	}
	if err := s.db.Create(&state).Error; err != nil {
		return nil, err
	}
	return &state, nil
}

func (s *otpService) resetOTPFailures(state *models.OTPResend) {
	s.db.Model(state).Updates(map[string]interface{}{
		"failed_attempts":   0,
		"otp_blocked_until": nil,
		"lock_cycles":       0,
	})
}

func (s *otpService) registerFailedOTPAttempt(user *models.Usuarios, state *models.OTPResend, otp *models.OTP) error {
	failedAttempts := state.FailedAttempts + 1

	if failedAttempts >= MAX_OTP_ATTEMPTS {
		lockCycles := state.LockCycles + 1
		s.db.Model(otp).Update("is_used", true)
		s.InvalidateUserOTPs(user.ID)

		if lockCycles >= MAX_OTP_LOCK_CYCLES {
			s.db.Model(user).Updates(map[string]interface{}{
				"activo":   false,
				"intentos": 0,
			})
			s.db.Model(state).Updates(map[string]interface{}{
				"failed_attempts":   0,
				"otp_blocked_until": nil,
				"lock_cycles":       lockCycles,
			})
			return errors.New("demasiados intentos fallidos, cuenta deshabilitada")
		}

		blockedUntil := time.Now().Add(OTP_BLOCK_DURATION)
		s.db.Model(state).Updates(map[string]interface{}{
			"failed_attempts":   0,
			"otp_blocked_until": blockedUntil,
			"lock_cycles":       lockCycles,
		})
		return errors.New("demasiados intentos fallidos, cuenta bloqueada por 30 minutos")
	}

	s.db.Model(state).Update("failed_attempts", failedAttempts)
	attemptsLeft := MAX_OTP_ATTEMPTS - failedAttempts
	return fmt.Errorf("código incorrecto. Intentos restantes: %d", attemptsLeft)
}
