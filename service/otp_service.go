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

	"gorm.io/gorm"
)

type OTPService interface {
    GenerateOTP(userID uint) (*models.OTP, error)
    VerifyOTP(userID uint, code string) error
    ResendOTP(userID uint) error
    CleanupExpiredOTPs() error
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
    
    // Enviar OTP por email
    go s.sendOTPEmail(user.Correo, user.Nombres_Apellidos, code)
    
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
    subject := "🔐 Código de Verificación - Serious Game"
    
    body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body style="margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background-color:#f5f7fa;">
    <table width="100%%" cellpadding="0" cellspacing="0" style="min-width:320px;background-color:#f5f7fa;">
        <tr>
            <td align="center" style="padding:40px 20px;">
                <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;box-shadow:0 4px 6px rgba(0,0,0,0.07);">
                    <tr>
                        <td style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;border-radius:16px 16px 0 0;text-align:center;">
                            <div style="display:inline-block;width:80px;height:80px;background-color:rgba(255,255,255,0.15);border-radius:50%%;padding:20px;margin-bottom:20px;">
                                <svg width="80" height="80" viewBox="0 0 24 24" fill="none">
                                    <path d="M12 15V12M12 8H12.01M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="white" stroke-width="2" stroke-linecap="round"/>
                                </svg>
                            </div>
                            <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;">Código de Verificación</h1>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:40px 30px;">
                            <p style="margin:0 0 24px;color:#1f2937;font-size:16px;">
                                Hola <strong>%s</strong>,
                            </p>
                            <p style="margin:0 0 32px;color:#4b5563;font-size:15px;line-height:1.6;">
                                Para completar el inicio de sesión en Serious Game, ingresa el siguiente código de verificación:
                            </p>
                            <div style="background:linear-gradient(135deg,#f3f4f6 0%%,#e5e7eb 100%%);border-radius:12px;padding:32px;text-align:center;margin:0 0 32px;">
                                <div style="font-size:36px;font-weight:700;color:#1f2937;letter-spacing:8px;font-family:monospace;">
                                    %s-%s
                                </div>
                                <p style="margin:16px 0 0;color:#6b7280;font-size:14px;">
                                    Este código expira en 1 minutos
                                </p>
                            </div>
                            <div style="background-color:#fef3c7;border:1px solid #fcd34d;border-radius:8px;padding:16px;margin:0 0 24px;">
                                <p style="margin:0;color:#92400e;font-size:14px;">
                                    <strong>⚠️ Importante:</strong> No compartas este código con nadie. El equipo de Serious Game nunca te pedirá este código.
                                </p>
                            </div>
                            <table width="100%%" style="margin:32px 0;">
                                <tr>
                                    <td style="padding:20px;background-color:#f9fafb;border-radius:8px;">
                                        <h3 style="margin:0 0 12px;color:#374151;font-size:16px;">¿No solicitaste este código?</h3>
                                        <p style="margin:0;color:#6b7280;font-size:14px;line-height:1.5;">
                                            Si no intentaste iniciar sesión, alguien podría estar intentando acceder a tu cuenta. 
                                            Te recomendamos cambiar tu contraseña inmediatamente.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td style="background-color:#f9fafb;padding:24px 30px;border-radius:0 0 16px 16px;text-align:center;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;color:#6b7280;font-size:13px;">
                                © %d Serious Game 
                            </p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>`, name, otp[:3], otp[3:], time.Now().Year())
    
    sendEmail(email, subject, body)
}

func (s *otpService) CleanupExpiredOTPs() error {
    return s.db.Where("expires_at < ? OR is_used = ?", time.Now(), true).Delete(&models.OTP{}).Error
}