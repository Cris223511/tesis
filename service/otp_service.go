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
    OTP_LENGTH           = 6
    OTP_EXPIRY_MINUTES   = 5
    MAX_OTP_ATTEMPTS     = 3
    MAX_RESEND_ATTEMPTS  = 3
    RESEND_COOLDOWN      = 1 * time.Minute
    RESEND_BLOCK_HOURS   = 24
)

func (s *otpService) GenerateOTP(userID uint) (*models.OTP, error) {
    // Invalidar OTPs anteriores
    s.db.Model(&models.OTP{}).
        Where("user_id = ? AND is_used = ? AND expires_at > ?", userID, false, time.Now()).
        Update("is_used", true)
    
    // Generar código OTP
    code := s.generateRandomCode()
    
    // Crear OTP
    otp := &models.OTP{
        UserID:    userID,
        Code:      code,
        ExpiresAt: time.Now().Add(OTP_EXPIRY_MINUTES * time.Minute),
        IsUsed:    false,
        Attempts:  0,
    }
    
    if err := s.db.Create(otp).Error; err != nil {
        return nil, errors.New("error al generar OTP")
    }
    
    // Obtener usuario para enviar email
    var user models.Usuarios
    if err := s.db.First(&user, userID).Error; err != nil {
        return nil, errors.New("usuario no encontrado")
    }
    
    // Enviar OTP por email para 2FA (verificación de doble factor)
    go utils.SendOTPEmail(user.Correo, code)
    
    log.Printf("[OTP] Código generado para usuario %d: %s", userID, code)
    
    return otp, nil
}

func (s *otpService) VerifyOTP(userID uint, code string) error {
    var otp models.OTP
    
    // Buscar OTP válido
    err := s.db.Where("user_id = ? AND is_used = ? AND expires_at > ?", 
        userID, false, time.Now()).
        Order("created_at DESC").
        First(&otp).Error
    
    if err != nil {
        if errors.Is(err, gorm.ErrRecordNotFound) {
            return errors.New("código OTP no válido o expirado")
        }
        return errors.New("error al verificar OTP")
    }
    
    // Verificar intentos
    if otp.Attempts >= MAX_OTP_ATTEMPTS {
        s.db.Model(&otp).Update("is_used", true)
        return errors.New("demasiados intentos fallidos")
    }
    
    // Incrementar intentos
    s.db.Model(&otp).Update("attempts", otp.Attempts + 1)
    
    // Comparar código (case insensitive)
    if strings.ToUpper(code) != strings.ToUpper(otp.Code) {
        if otp.Attempts + 1 >= MAX_OTP_ATTEMPTS {
            s.db.Model(&otp).Update("is_used", true)
            return errors.New("código incorrecto, OTP bloqueado")
        }
        return errors.New("código incorrecto")
    }
    
    // Marcar como usado
    s.db.Model(&otp).Update("is_used", true)
    
    log.Printf("[OTP] Código verificado exitosamente para usuario %d", userID)
    
    return nil
}


func (s *otpService) ResendOTP(userID uint) error {

    var resendRecord models.OTPResend
    err := s.db.Where("user_id = ?", userID).First(&resendRecord).Error
    
    if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
        return errors.New("error al verificar límites")
    }
    

    if errors.Is(err, gorm.ErrRecordNotFound) {
        resendRecord = models.OTPResend{
            UserID:       userID,
            ResendCount:  0,
            LastResendAt: time.Now(),
        }
        s.db.Create(&resendRecord)
    }
    
    if resendRecord.BlockedUntil != nil && resendRecord.BlockedUntil.After(time.Now()) {
        return fmt.Errorf("reenvío bloqueado hasta %s", resendRecord.BlockedUntil.Format("15:04"))
    }

    if time.Since(resendRecord.LastResendAt) < RESEND_COOLDOWN {
        remaining := RESEND_COOLDOWN - time.Since(resendRecord.LastResendAt)
        return fmt.Errorf("espera %d segundos antes de reenviar", int(remaining.Seconds()))
    }
 
    if resendRecord.ResendCount >= MAX_RESEND_ATTEMPTS {
  
        blockedUntil := time.Now().Add(RESEND_BLOCK_HOURS * time.Hour)
        s.db.Model(&resendRecord).Updates(map[string]interface{}{
            "blocked_until": blockedUntil,
            "resend_count": 0,
        })
        return errors.New("límite de reenvíos alcanzado, bloqueado por 24 horas")
    }
    
   
    _, err = s.GenerateOTP(userID) 
    if err != nil {
        return err
    }
  
    s.db.Model(&resendRecord).Updates(map[string]interface{}{
        "resend_count":  resendRecord.ResendCount + 1,
        "last_resend_at": time.Now(),
    })
    
    reenviosRestantes := MAX_RESEND_ATTEMPTS - (resendRecord.ResendCount + 1)
    log.Printf("[OTP] Código reenviado para usuario %d. Reenvíos restantes: %d", userID, reenviosRestantes)
    
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
        ExpiresAt: time.Now().Add(1 * time.Minute),
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