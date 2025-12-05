package controllers

import (
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"time"
	"usuarios/models"
	services "usuarios/service"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type AuthController struct {
	UserService services.UserService
	OTPService  services.OTPService
	db          *gorm.DB
}

func NewAuthController(userService services.UserService, otpService services.OTPService, db *gorm.DB) *AuthController {
	return &AuthController{
		UserService: userService,
		OTPService:  otpService,
		db:          db,
	}
}

type LoginRequest struct {
	Usuario     string `json:"usuario" binding:"required"`
	Contrasena  string `json:"contrasena" binding:"required"`
	RememberMe  bool   `json:"remember_me"`
	DeviceInfo  string `json:"device_info"`
}

type OTPRequest struct {
	UserID       uint   `json:"user_id" binding:"required"`
	Code         string `json:"code" binding:"required,len=6"`
	DeviceInfo   string `json:"device_info"`   // Model, manufacturer, etc
	AndroidVersion string `json:"android_version"`
	UserAgent    string `json:"user_agent"`
}

type ResendOTPRequest struct {
	UserID uint `json:"user_id" binding:"required"`
}

func (ac *AuthController) Login(c *gin.Context) {
	var req LoginRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		ac.logSecurityEvent(0, ac.getClientIP(c), "LOGIN_INVALID_REQUEST", err.Error())
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "Datos de login inválidos",
			"details": err.Error(),
		})
		return
	}

	clientIP := ac.getClientIP(c)
	userAgent := ac.getUserAgent(c)

	log.Printf("[AUTH][LOGIN_ATTEMPT] Usuario: %s, IP: %s", req.Usuario, clientIP)

	if ac.isIPBlocked(clientIP) {
		ac.logSecurityEvent(0, clientIP, "LOGIN_IP_BLOCKED", "IP temporarily blocked")
		c.JSON(http.StatusTooManyRequests, gin.H{
			"error":       "Demasiados intentos desde esta IP",
			"message":     "Intenta más tarde",
			"retry_after": 900,
		})
		return
	}

	user, err := ac.UserService.LoginUser(req.Usuario, req.Contrasena, clientIP, userAgent)
	if err != nil {
		ac.handleLoginError(c, err, req.Usuario, clientIP)
		return
	}

	otpRecord, err := ac.OTPService.GenerateSecureOTP(
		user.ID,
		clientIP,
		userAgent,
		"login",
	)
	if err != nil {
		ac.logSecurityEvent(user.ID, clientIP, "OTP_GENERATION_FAILED", err.Error())

		if strings.Contains(err.Error(), "límite") {
			c.JSON(http.StatusTooManyRequests, gin.H{
				"error":       "Límite de códigos OTP alcanzado",
				"message":     err.Error(),
				"retry_after": 3600,
			})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error":   "Error interno al generar código de verificación",
				"message": "Contacta soporte si persiste",
			})
		}
		return
	}

	ac.logSecurityEvent(user.ID, clientIP, "LOGIN_SUCCESS", fmt.Sprintf("OTP sent, expires: %s", otpRecord.ExpiresAt.Format("15:04")))

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Credenciales válidas. Código OTP enviado.",
		"user": gin.H{
			"user_id":           user.ID,
			"usuario":           user.Usuario,
			"correo":            ac.maskEmail(user.Correo),
			"nombres_apellidos": user.Nombres_Apellidos,
		},
		"otp_info": gin.H{
			"expires_at": otpRecord.ExpiresAt,
			"expires_in": int(time.Until(otpRecord.ExpiresAt).Seconds()),
			"purpose":    otpRecord.Purpose,
		},
	})
}

func (ac *AuthController) VerifyOTP(c *gin.Context) {
	var req OTPRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		ac.logSecurityEvent(0, ac.getClientIP(c), "OTP_INVALID_REQUEST", err.Error())
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "Datos de verificación inválidos",
			"details": err.Error(),
		})
		return
	}

	clientIP := ac.getClientIP(c)
	userAgent := ac.getUserAgent(c)

	log.Printf("[AUTH][OTP_VERIFY_ATTEMPT] UserID: %d, IP: %s, Device: %s", req.UserID, clientIP, req.DeviceInfo)

	deviceInfo := &services.DeviceLoginInfo{
		IP:             clientIP,
		UserAgent:      userAgent,
		DeviceInfo:     req.DeviceInfo,
		AndroidVersion: req.AndroidVersion,
		Timestamp:      time.Now(),
	}

	err := ac.OTPService.VerifySecureOTPWithDevice(req.UserID, req.Code, clientIP, userAgent, deviceInfo)
	if err != nil {
		ac.handleOTPVerificationError(c, err, req.UserID, clientIP)
		return
	}

	user, err := ac.UserService.GetUserByID(req.UserID)
	if err != nil {
		ac.logSecurityEvent(req.UserID, clientIP, "USER_NOT_FOUND", "User lookup failed after OTP verification")
		c.JSON(http.StatusNotFound, gin.H{
			"error":   "Usuario no encontrado",
			"message": "Contacta soporte",
		})
		return
	}

	accessToken, refreshToken, err := utils.GenerateToken(user)
	if err != nil {
		ac.logSecurityEvent(user.ID, clientIP, "TOKEN_GENERATION_FAILED", err.Error())
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "Error al generar tokens de acceso",
			"message": "Intenta nuevamente",
		})
		return
	}

	ac.logSecurityEvent(user.ID, clientIP, "AUTH_COMPLETE_SUCCESS", "Full authentication successful")

	c.JSON(http.StatusOK, gin.H{
		"success":       true,
		"message":       "Autenticación exitosa",
		"access_token":  accessToken,
		"refresh_token": refreshToken,
		"token_type":    "Bearer",
		"expires_in":    7200,
		"user": gin.H{
			"id":               user.ID,
			"usuario":          user.Usuario,
			"correo":           user.Correo,
			"nombres_apellidos": user.Nombres_Apellidos,
			"roles":            user.Roles,
			"activo":           user.Activo,
		},
	})
}

func (ac *AuthController) ResendOTP(c *gin.Context) {
	var req ResendOTPRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		ac.logSecurityEvent(0, ac.getClientIP(c), "RESEND_INVALID_REQUEST", err.Error())
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "ID de usuario requerido",
		})
		return
	}

	clientIP := ac.getClientIP(c)

	log.Printf("[AUTH][OTP_RESEND_ATTEMPT] UserID: %d, IP: %s", req.UserID, clientIP)

	err := ac.OTPService.ResendOTP(req.UserID)
	if err != nil {
		ac.logSecurityEvent(req.UserID, clientIP, "OTP_RESEND_FAILED", err.Error())

		if strings.Contains(err.Error(), "bloqueado") {
			c.JSON(http.StatusTooManyRequests, gin.H{
				"error":       "Reenvío bloqueado temporalmente",
				"message":     err.Error(),
				"retry_after": 86400,
			})
		} else if strings.Contains(err.Error(), "espera") {
			c.JSON(http.StatusTooManyRequests, gin.H{
				"error":       "Debe esperar antes de reenviar",
				"message":     err.Error(),
				"retry_after": 60,
			})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error":   "Error al reenviar código",
				"message": "Intenta más tarde",
			})
		}
		return
	}

	ac.logSecurityEvent(req.UserID, clientIP, "OTP_RESEND_SUCCESS", "OTP resent successfully")

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "Nuevo código OTP enviado exitosamente",
	})
}

func (ac *AuthController) GetToken(c *gin.Context) {
	token, err := utils.GenerateInitialAuthToken()
	if err != nil {
		ac.logSecurityEvent(0, ac.getClientIP(c), "INITIAL_TOKEN_ERROR", err.Error())
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error generando token inicial",
		})
		return
	}

	ac.logSecurityEvent(0, ac.getClientIP(c), "INITIAL_TOKEN_GENERATED", "")
	c.JSON(http.StatusOK, gin.H{
		"token": token,
	})
}

func (ac *AuthController) RefreshToken(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
		c.JSON(http.StatusUnauthorized, gin.H{
			"error": "Token de refresh requerido",
		})
		return
	}

	refreshToken := strings.TrimPrefix(authHeader, "Bearer ")

	claims, err := utils.ValidateToken(refreshToken)
	if err != nil {
		ac.logSecurityEvent(0, ac.getClientIP(c), "REFRESH_TOKEN_INVALID", err.Error())
		c.JSON(http.StatusUnauthorized, gin.H{
			"error":   "Token de refresh inválido",
			"message": "Inicia sesión nuevamente",
		})
		return
	}

	var user models.Usuarios
	if err := ac.db.First(&user, claims.UserID).Error; err != nil {
		ac.logSecurityEvent(uint(claims.UserID), ac.getClientIP(c), "USER_NOT_FOUND_REFRESH", "User not found during refresh")
		c.JSON(http.StatusUnauthorized, gin.H{
			"error": "Usuario no encontrado",
		})
		return
	}

	accessToken, newRefreshToken, err := utils.GenerateToken(&user)
	if err != nil {
		ac.logSecurityEvent(uint(claims.UserID), ac.getClientIP(c), "TOKEN_REFRESH_FAILED", err.Error())
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Error renovando tokens",
		})
		return
	}

	ac.logSecurityEvent(uint(claims.UserID), ac.getClientIP(c), "TOKEN_REFRESHED", "")

	c.JSON(http.StatusOK, gin.H{
		"access_token":  accessToken,
		"refresh_token": newRefreshToken,
		"token_type":    "Bearer",
		"expires_in":    7200,
	})
}

func (ac *AuthController) handleLoginError(c *gin.Context, err error, username, clientIP string) {
	errMsg := err.Error()

	ac.logSecurityEvent(0, clientIP, "LOGIN_FAILED", fmt.Sprintf("User: %s, Error: %s", username, errMsg))

	switch {
	case errMsg == "credenciales inválidas":
		c.JSON(http.StatusUnauthorized, gin.H{
			"error": "Usuario o contraseña incorrectos",
			"code":  "INVALID_CREDENTIALS",
		})
	case errMsg == "cuenta desactivada":
		c.JSON(http.StatusForbidden, gin.H{
			"error":   "La cuenta se encuentra desactivada",
			"code":    "ACCOUNT_DISABLED",
			"message": "Contacta administrador",
		})
	case strings.HasPrefix(errMsg, "cuenta bloqueada"):
		c.JSON(http.StatusForbidden, gin.H{
			"error":       errMsg,
			"code":        "ACCOUNT_LOCKED",
			"retry_after": 1800,
		})
	case errMsg == "contraseña expirada, debe actualizarla":
		c.JSON(http.StatusUnauthorized, gin.H{
			"error":   "Contraseña expirada",
			"code":    "PASSWORD_EXPIRED",
			"message": "Debe actualizar su contraseña",
		})
	case errMsg == "demasiados intentos, intente más tarde":
		c.JSON(http.StatusTooManyRequests, gin.H{
			"error":       "Demasiados intentos fallidos",
			"code":        "TOO_MANY_ATTEMPTS",
			"retry_after": 900,
		})
	case errMsg == "acceso denegado desde esta ubicación":
		c.JSON(http.StatusForbidden, gin.H{
			"error": "Acceso bloqueado desde esta ubicación",
			"code":  "LOCATION_BLOCKED",
		})
	default:
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "Error interno del servidor",
			"code":    "INTERNAL_ERROR",
			"message": "Intenta más tarde",
		})
	}
}

func (ac *AuthController) handleOTPVerificationError(c *gin.Context, err error, userID uint, clientIP string) {
	errMsg := err.Error()

	ac.logSecurityEvent(userID, clientIP, "OTP_VERIFICATION_FAILED", errMsg)

	switch {
	case strings.Contains(errMsg, "expirado"):
		c.JSON(http.StatusGone, gin.H{
			"error":   "Código OTP expirado",
			"code":    "OTP_EXPIRED",
			"message": "Solicita un nuevo código",
		})
	case strings.Contains(errMsg, "no válido"):
		c.JSON(http.StatusNotFound, gin.H{
			"error":   "Código OTP no encontrado",
			"code":    "OTP_NOT_FOUND",
			"message": "Verifica que el código sea correcto",
		})
	case strings.Contains(errMsg, "incorrecto"):
		c.JSON(http.StatusUnauthorized, gin.H{
			"error":   "Código OTP incorrecto",
			"code":    "OTP_INVALID",
			"message": errMsg,
		})
	default:
		c.JSON(http.StatusInternalServerError, gin.H{
			"error":   "Error verificando código OTP",
			"code":    "OTP_VERIFICATION_ERROR",
			"message": "Intenta nuevamente",
		})
	}
}

func (ac *AuthController) getClientIP(c *gin.Context) string {
	forwarded := c.GetHeader("X-Forwarded-For")
	if forwarded != "" {
		ips := strings.Split(forwarded, ",")
		if len(ips) > 0 {
			return strings.TrimSpace(ips[0])
		}
	}

	realIP := c.GetHeader("X-Real-IP")
	if realIP != "" {
		return realIP
	}

	cfIP := c.GetHeader("CF-Connecting-IP")
	if cfIP != "" {
		return cfIP
	}

	return c.ClientIP()
}

func (ac *AuthController) getUserAgent(c *gin.Context) string {
	ua := c.GetHeader("User-Agent")
	if len(ua) > 500 {
		ua = ua[:500]
	}
	return ua
}

func (ac *AuthController) maskEmail(email string) string {
	if email == "" {
		return ""
	}

	parts := strings.Split(email, "@")
	if len(parts) != 2 {
		return email
	}

	username := parts[0]
	domain := parts[1]

	if len(username) <= 2 {
		return email
	}

	masked := string(username[0]) + strings.Repeat("*", len(username)-2) + string(username[len(username)-1])
	return masked + "@" + domain
}

func (ac *AuthController) isIPBlocked(ip string) bool {
	if ip == "" || ip == "unknown" {
		return false
	}

	if net.ParseIP(ip) == nil {
		return true
	}

	return false
}

func (ac *AuthController) logSecurityEvent(userID uint, clientIP, eventType, details string) {
	timestamp := time.Now().Format("2006-01-02 15:04:05")

	log.Printf("[SECURITY][%s] Time: %s, UserID: %d, IP: %s, Details: %s",
		eventType, timestamp, userID, clientIP, details)
}
