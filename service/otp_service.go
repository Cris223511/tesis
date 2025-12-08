package services

import (
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net"
	"strings"
	"time"
	"usuarios/models"
	"usuarios/utils"

	"gorm.io/gorm"
)

type DeviceLoginInfo struct {
	IP             string
	UserAgent      string
	DeviceInfo     string
	AndroidVersion string
	Timestamp      time.Time
}

type OTPService interface {
    GenerateOTP(userID uint) (*models.OTP, error)
    VerifyOTP(userID uint, code string) error
    ResendOTP(userID uint) error
    CleanupExpiredOTPs() error
    SaveOTP(userID uint, code string) error
    ValidateOTP(userID uint, code string) (bool, error)
    CheckOTP(userID uint, code string) (bool, error)
    InvalidateUserOTPs(userID uint) error

    GenerateSecureOTP(userID uint, clientIP, userAgent, purpose string) (*models.OTP, error)
    VerifySecureOTP(userID uint, code, clientIP, userAgent string) error
    VerifySecureOTPWithDevice(userID uint, code, clientIP, userAgent string, deviceInfo *DeviceLoginInfo) error
}

type otpService struct {
    db        *gorm.DB
    secureOTP *utils.SecureOTP
}

func NewOTPService(db *gorm.DB) OTPService {
    return &otpService{
        db:        db,
        secureOTP: utils.GetSecureOTP(),
    }
}

const (
    OTP_LENGTH           = 6
    OTP_EXPIRY_MINUTES   = 5
    MAX_RESEND_ATTEMPTS  = 3
    RESEND_COOLDOWN      = 1 * time.Minute
    RESEND_BLOCK_HOURS   = 24

    MAX_DAILY_OTP_PER_USER = 200
    MAX_HOURLY_OTP_PER_USER = 200
    MAX_DAILY_OTP_PER_IP   = 50
)

func (s *otpService) GenerateOTP(userID uint) (*models.OTP, error) {
    return s.GenerateSecureOTP(userID, "", "", "login")
}

func (s *otpService) VerifyOTP(userID uint, code string) error {
    return s.VerifySecureOTP(userID, code, "", "")
}


func (s *otpService) ResendOTP(userID uint) error {

	_, err := s.GenerateSecureOTP(userID, "", "", "resend")
	if err != nil {
		return err
	}

	log.Printf("[OTP] Código reenviado para usuario %d sin restricciones", userID)
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

func (s *otpService) sendOTPEmail(userID uint, code string) error {
    return s.sendOTPEmailWithType(userID, code, false, 0)
}

func (s *otpService) sendOTPEmailWithType(userID uint, code string, isResend bool, resendCount int) error {
    var user models.Usuarios
    if err := s.db.First(&user, userID).Error; err != nil {
        return fmt.Errorf("usuario no encontrado: %w", err)
    }

    emailType := "OTP"
    if isResend {
        emailType = "OTP_RESEND"
    }
    log.Printf("[%s][EMAIL] Enviando código a usuario %d, email: %s, código: %s", emailType, userID, user.Correo, code)

    go func() {
        var err error
        if isResend {
            err = utils.SendOTPResendEmail(user.Correo, code, resendCount)
        } else {
            err = utils.SendOTPEmail(user.Correo, code)
        }

        if err != nil {
            log.Printf("[%s][EMAIL][ERROR] Error enviando email a %s: %v", emailType, user.Correo, err)
        } else {
            log.Printf("[%s][EMAIL][SUCCESS] Email enviado exitosamente a %s", emailType, user.Correo)
        }
    }()

    return nil
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
    err := s.db.Where("user_id = ? AND is_used = ? AND expires_at > ?",
        userID, false, time.Now()).
        Order("created_at DESC").
        First(&otp).Error

    if err != nil {
        return false, err
    }

    var isValid bool
    if otp.IsEncrypted() {
        isValid = s.secureOTP.VerifyOTP(code, otp.CodeHash, otp.Salt)
    } else {
        isValid = otp.VerifyLegacy(code)
    }

    return isValid, nil
}

func (s *otpService) InvalidateUserOTPs(userID uint) error {
    return s.db.Model(&models.OTP{}).
        Where("user_id = ? AND is_used = ?", userID, false).
        Update("is_used", true).Error
}

func (s *otpService) GenerateSecureOTP(userID uint, clientIP, userAgent, purpose string) (*models.OTP, error) {

    var resendCount int64
    today := time.Now().Truncate(24 * time.Hour)
    s.db.Model(&models.OTP{}).Where(
        "user_id = ? AND created_at >= ?", userID, today,
    ).Count(&resendCount)

    s.db.Model(&models.OTP{}).
        Where("user_id = ? AND is_used = ? AND expires_at > ?", userID, false, time.Now()).
        Update("is_used", true)

    code := s.generateRandomCode()

    encryptedCode, hash, salt, err := s.secureOTP.EncryptOTP(code)
    if err != nil {
        s.logSecurityEvent(userID, clientIP, "OTP_ENCRYPTION_ERROR", err.Error())
        return nil, fmt.Errorf("error interno al generar OTP")
    }

    otp := &models.OTP{
        UserID:        userID,
        Code:          "",
        EncryptedCode: encryptedCode,
        CodeHash:      hash,
        Salt:          salt,
        ExpiresAt:     time.Now().Add(OTP_EXPIRY_MINUTES * time.Minute),
        IsUsed:        false,
        Attempts:      0,
        ClientIP:      s.normalizeIP(clientIP),
        UserAgent:     s.sanitizeUserAgent(userAgent),
        Purpose:       purpose,
    }

    if err := s.db.Create(otp).Error; err != nil {
        s.logSecurityEvent(userID, clientIP, "OTP_SAVE_ERROR", err.Error())
        return nil, errors.New("error al generar OTP")
    }

    // Enviar email: si es reenvío usa template especial, sino template normal
    isResend := purpose == "resend"
    if err := s.sendOTPEmailWithType(userID, code, isResend, int(resendCount)+1); err != nil {
        log.Printf("[OTP][WARNING] Error enviando email a usuario %d: %v", userID, err)
    }

    s.logSecurityEvent(userID, clientIP, "OTP_GENERATED", fmt.Sprintf("Purpose: %s", purpose))

    return otp, nil
}

func (s *otpService) VerifySecureOTP(userID uint, code, clientIP, userAgent string) error {
    if err := s.validateOTPInput(code); err != nil {
        s.logSecurityEvent(userID, clientIP, "OTP_INVALID_INPUT", err.Error())
        return err
    }

    var otp models.OTP
    err := s.db.Where(
        "user_id = ? AND is_used = ? AND expires_at > ?",
        userID, false, time.Now(),
    ).Order("created_at DESC").First(&otp).Error

    if err != nil {
        if errors.Is(err, gorm.ErrRecordNotFound) {
            s.logSecurityEvent(userID, clientIP, "OTP_NOT_FOUND", "No valid OTP")
            return errors.New("código OTP no válido o expirado")
        }
        s.logSecurityEvent(userID, clientIP, "OTP_DB_ERROR", err.Error())
        return errors.New("error al verificar OTP")
    }

    var isValid bool
    if otp.IsEncrypted() {
        isValid = s.secureOTP.VerifyOTP(code, otp.CodeHash, otp.Salt)
    } else {
        isValid = otp.VerifyLegacy(code)
    }

    if !isValid {
        s.logSecurityEvent(userID, clientIP, "OTP_INVALID_CODE",
            fmt.Sprintf("OTP ID: %d", otp.ID))
        return errors.New("código incorrecto")
    }

    s.db.Model(&otp).Update("is_used", true)
    s.logSecurityEvent(userID, clientIP, "OTP_VERIFIED", fmt.Sprintf("OTP ID: %d", otp.ID))

    return nil
}

func (s *otpService) checkRateLimit(userID uint, clientIP string) error {
    now := time.Now()
    today := now.Truncate(24 * time.Hour)
    hourAgo := now.Add(-1 * time.Hour)

    var dailyUserCount int64
    s.db.Model(&models.OTP{}).Where(
        "user_id = ? AND created_at >= ?", userID, today,
    ).Count(&dailyUserCount)

    if dailyUserCount >= MAX_DAILY_OTP_PER_USER {
        return fmt.Errorf("límite diario de usuario excedido (%d/%d)", dailyUserCount, MAX_DAILY_OTP_PER_USER)
    }

    var hourlyUserCount int64
    s.db.Model(&models.OTP{}).Where(
        "user_id = ? AND created_at >= ?", userID, hourAgo,
    ).Count(&hourlyUserCount)

    if hourlyUserCount >= MAX_HOURLY_OTP_PER_USER {
        return fmt.Errorf("límite por hora excedido (%d/%d)", hourlyUserCount, MAX_HOURLY_OTP_PER_USER)
    }

    if clientIP != "" && clientIP != "unknown" {
        var ipCount int64
        s.db.Model(&models.OTP{}).Where(
            "client_ip = ? AND created_at >= ?", clientIP, today,
        ).Count(&ipCount)

        if ipCount >= MAX_DAILY_OTP_PER_IP {
            return fmt.Errorf("límite diario por IP excedido (%d/%d)", ipCount, MAX_DAILY_OTP_PER_IP)
        }
    }

    return nil
}

func (s *otpService) validateOTPInput(code string) error {
    if len(code) != OTP_LENGTH {
        return errors.New("código debe tener 6 caracteres")
    }

    for _, char := range strings.ToUpper(code) {
        if !((char >= '0' && char <= '9') || (char >= 'A' && char <= 'Z')) {
            return errors.New("código contiene caracteres inválidos")
        }
    }

    return nil
}

func (s *otpService) normalizeIP(ip string) string {
    if ip == "" {
        return "unknown"
    }

    if strings.HasPrefix(ip, "::ffff:") {
        ip = strings.TrimPrefix(ip, "::ffff:")
    }

    if net.ParseIP(ip) == nil {
        return "invalid"
    }

    return ip
}

func (s *otpService) sanitizeUserAgent(ua string) string {
    if len(ua) > 500 {
        ua = ua[:500]
    }
    return strings.TrimSpace(ua)
}

func (s *otpService) VerifySecureOTPWithDevice(userID uint, code, clientIP, userAgent string, deviceInfo *DeviceLoginInfo) error {

	if err := s.VerifySecureOTP(userID, code, clientIP, userAgent); err != nil {
		return err
	}

	
	go s.sendSecurityNotificationEmail(userID, deviceInfo)

	return nil
}

func (s *otpService) sendSecurityNotificationEmail(userID uint, deviceInfo *DeviceLoginInfo) {
	var user models.Usuarios
	if err := s.db.First(&user, userID).Error; err != nil {
		log.Printf("[SECURITY][EMAIL] Error obteniendo usuario %d: %v", userID, err)
		return
	}


	subject := "Nuevo acceso a tu cuenta - Notificación de Seguridad"

	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background-color: #2196F3; color: white; padding: 20px; border-radius: 5px 5px 0 0; }
        .content { background-color: #f9f9f9; padding: 20px; border-radius: 0 0 5px 5px; }
        .device-info { background-color: #fff; border-left: 4px solid #2196F3; padding: 15px; margin: 15px 0; }
        .info-row { margin: 10px 0; }
        .label { font-weight: bold; color: #2196F3; }
        .warning { color: #F44336; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h2>Notificación de Acceso a tu Cuenta</h2>
        </div>
        <div class="content">
            <p>Hola <strong>%s</strong>,</p>

            <p>Se ha detectado un nuevo acceso a tu cuenta. Si fuiste tú, puedes ignorar este mensaje. Si no reconoces este acceso, <span class="warning">cambia tu contraseña inmediatamente</span>.</p>

            <div class="device-info">
                <h3>Información del Dispositivo</h3>
                <div class="info-row">
                    <span class="label">Dispositivo:</span> %s
                </div>
                <div class="info-row">
                    <span class="label">Sistema Operativo:</span> Android %s
                </div>
                <div class="info-row">
                    <span class="label">Dirección IP:</span> %s
                </div>
                <div class="info-row">
                    <span class="label">Fecha y Hora:</span> %s
                </div>
                <div class="info-row">
                    <span class="label">User Agent:</span> %s
                </div>
            </div>

            <p><strong>¿Cómo proteger tu cuenta?</strong></p>
            <ul>
                <li>Usa contraseñas fuertes y únicas</li>
                <li>Habilita la autenticación de dos factores</li>
                <li>Revisa regularmente tus accesos recientes</li>
                <li>No compartas tu código OTP con nadie</li>
            </ul>

            <p>Si tienes dudas sobre tu seguridad, contacta al equipo de soporte.</p>

            <p style="color: #999; font-size: 12px; margin-top: 30px; border-top: 1px solid #ddd; padding-top: 20px;">
                Este es un correo automático. No respondas a este mensaje.
            </p>
        </div>
    </div>
</body>
</html>`,
		user.Nombres_Apellidos,
		deviceInfo.DeviceInfo,
		deviceInfo.AndroidVersion,
		deviceInfo.IP,
		deviceInfo.Timestamp.Format("02/01/2006 15:04:05"),
		deviceInfo.UserAgent,
	)


	if err := utils.SendSecurityNotificationEmail(user.Correo, subject, body); err != nil {
		log.Printf("[SECURITY][EMAIL] Error enviando notificación de seguridad a %s: %v", user.Correo, err)
	} else {
		log.Printf("[SECURITY][EMAIL] Notificación de seguridad enviada exitosamente a %s", user.Correo)
	}
}

func (s *otpService) logSecurityEvent(userID uint, clientIP, eventType, details string) {
    log.Printf("[SECURITY][%s] UserID: %d, IP: %s, Details: %s",
        eventType, userID, clientIP, details)
}