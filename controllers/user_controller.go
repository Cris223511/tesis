package controllers

import (
	"crypto/rand"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"strings"

	"time"

	"usuarios/models"
	"usuarios/service"
	"usuarios/utils"

	"sync"

	"github.com/gin-gonic/gin"
)


type UserController struct {
	UserService services.UserService
}

type otpSession struct {
	UserID         uint
	OTP            string
	FailedAttempts int
	RequestedCount int
	BlockedUntil   time.Time
	ExpiresAt      time.Time
}

var otpCache = struct {
	sync.RWMutex
	data map[uint]*otpSession
}{data: make(map[uint]*otpSession)}

func NewUserController(userService services.UserService) *UserController {
	return &UserController{
		UserService: userService,
	}
}

func (ctrl *UserController) Login(c *gin.Context) {
	var loginRequest struct {
		NombreUsuario string `json:"nombre_usuario" binding:"required"`
		Password      string `json:"password" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&loginRequest); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Debe proporcionar nombre de usuario y contraseña"})
		return
	}

	clientIP := getClientIP(c)
	userAgent := c.GetHeader("User-Agent")

	user, err := ctrl.UserService.LoginUser(loginRequest.NombreUsuario, loginRequest.Password, clientIP, userAgent)
	if err != nil {
		switch err.Error() {
		case "credenciales inválidas":
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario o contraseña incorrectos"})
		case "cuenta desactivada":
			c.JSON(http.StatusForbidden, gin.H{"error": "La cuenta se encuentra desactivada"})
		case "cuenta bloqueada, intente en":
			c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		case "contraseña expirada, debe actualizarla":
			c.JSON(http.StatusUnauthorized, gin.H{"error": "Tu contraseña ha expirado, debes actualizarla"})
		case "demasiados intentos, intente más tarde":
			c.JSON(http.StatusTooManyRequests, gin.H{"error": "Demasiados intentos fallidos"})
		case "acceso denegado desde esta ubicación":
			c.JSON(http.StatusForbidden, gin.H{"error": "Acceso bloqueado desde esta ubicación"})
		case "solicitud inválida":
			c.JSON(http.StatusBadRequest, gin.H{"error": "Solicitud inválida"})
		default:
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error en el servidor"})
		}
		return
	}

	otp := generateOTP(6)
	otpCache.Lock()
	otpCache.data[user.ID] = &otpSession{
		UserID:         user.ID,
		OTP:            otp,
		FailedAttempts: 0,
		RequestedCount: 1,
		BlockedUntil:   time.Time{},
		ExpiresAt:      time.Now().Add(5 * time.Minute),
	}
	otpCache.Unlock()

	go utils.SendOTPEmail(user.Correo, otp)

	c.JSON(http.StatusOK, gin.H{
		"message": "Credenciales válidas. Se envió un código OTP a tu correo.",
		"user": gin.H{
			"user_id":          user.ID,
			"nombre_apellidos": user.Nombre_Apellidos,
			"correo":          user.Correo,
			"roles":           user.Roles,
		},
	})
}

func (ctrl *UserController) ValidateOTP(c *gin.Context) {
	var req struct {
		UserID uint   `json:"user_id" binding:"required"`
		OTP    string `json:"otp" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	otpCache.Lock()
	session, exists := otpCache.data[req.UserID]
	otpCache.Unlock()
	
	if !exists {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No hay sesión OTP activa"})
		return
	}

	now := time.Now()
	if now.Before(session.BlockedUntil) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Cuenta bloqueada temporalmente"})
		return
	}

	if now.After(session.ExpiresAt) {
		otpCache.Lock()
		delete(otpCache.data, req.UserID)
		otpCache.Unlock()
		c.JSON(http.StatusUnauthorized, gin.H{"error": "OTP expirado"})
		return
	}

	if session.OTP != req.OTP {
		session.FailedAttempts++
		if session.FailedAttempts >= 3 {
			session.BlockedUntil = now.Add(30 * time.Minute)
			otpCache.Lock()
			otpCache.data[req.UserID] = session
			otpCache.Unlock()
			
			user, _ := ctrl.UserService.GetUserByID(req.UserID)
			if user != nil {
				go utils.SendSecurityAlert(user.Correo, user.Nombre_Apellidos, 
					"Múltiples intentos fallidos de OTP", getClientIP(c))
			}
			
			c.JSON(http.StatusForbidden, gin.H{"error": "OTP incorrecto. Cuenta bloqueada por 30 minutos"})
			return
		}
		
		otpCache.Lock()
		otpCache.data[req.UserID] = session
		otpCache.Unlock()
		
		remaining := 3 - session.FailedAttempts
		c.JSON(http.StatusUnauthorized, gin.H{
			"error": fmt.Sprintf("OTP incorrecto. %d intento(s) restante(s)", remaining),
		})
		return
	}

	otpCache.Lock()
	delete(otpCache.data, req.UserID)
	otpCache.Unlock()

	user, err := ctrl.UserService.GetUserByID(req.UserID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}

	token, refreshToken, err := utils.GenerateToken(user)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al generar token"})
		return
	}

	var roles []string
	for _, role := range user.Roles {
		roles = append(roles, role.Name)
	}

	c.JSON(http.StatusOK, gin.H{
		"message":       "Autenticación exitosa",
		"user_id":       user.ID,
		"roles":         roles,
		"bearer_token":  token,
		"refresh_token": refreshToken,
	})
}

func (ctrl *UserController) ResendOTP(c *gin.Context) {
	var req struct {
		UserID uint `json:"user_id" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	otpCache.Lock()
	session, exists := otpCache.data[req.UserID]
	otpCache.Unlock()
	
	if !exists {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No hay sesión OTP activa"})
		return
	}

	if time.Now().Before(session.BlockedUntil) {
		c.JSON(http.StatusForbidden, gin.H{"error": "Cuenta bloqueada temporalmente"})
		return
	}

	session.RequestedCount++
	if session.RequestedCount > 3 {
		session.BlockedUntil = time.Now().Add(24 * time.Hour)
		c.JSON(http.StatusForbidden, gin.H{"error": "Límite de reenvíos excedido. Cuenta bloqueada 24 horas"})
		return
	}

	newOTP := generateOTP(8)
	session.OTP = newOTP
	session.ExpiresAt = time.Now().Add(5 * time.Minute)
	session.FailedAttempts = 0
	
	otpCache.Lock()
	otpCache.data[req.UserID] = session
	otpCache.Unlock()

	user, _ := ctrl.UserService.GetUserByID(req.UserID)
	if user != nil {
		go utils.SendOTPEmail(user.Correo, newOTP)
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Nuevo código OTP enviado",
		"reenvios_restantes": 3 - session.RequestedCount,
	})
}

func (ctrl *UserController) Register(c *gin.Context) {
	var user models.User
	if err := c.ShouldBindJSON(&user); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	tempPassword, err := ctrl.UserService.CreateUser(&user)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"message": "Usuario registrado exitosamente",
		"user": gin.H{
			"id":               user.ID,
			"nombre_usuario":   user.NombreUsuario,
			"correo":          user.Correo,
			"password_temporal": tempPassword,
		},
	})
}

func (ctrl *UserController) List(c *gin.Context) {
	users, err := ctrl.UserService.ListUsers()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuarios"})
		return
	}
	c.JSON(http.StatusOK, users)
}

func (ctrl *UserController) GetByID(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	user, err := ctrl.UserService.GetUserByID(id)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}
	
	c.JSON(http.StatusOK, user)
}

func (ctrl *UserController) Update(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var updates map[string]interface{}
	if err := c.ShouldBindJSON(&updates); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	if err := ctrl.UserService.UpdateUser(id, updates); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Usuario actualizado exitosamente"})
}

func (ctrl *UserController) UpdatePassword(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var req struct {
		NewPassword     string `json:"new_password" binding:"required,min=12"`
		ConfirmPassword string `json:"confirm_password" binding:"required,eqfield=NewPassword"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Las contraseñas deben coincidir y tener al menos 12 caracteres"})
		return
	}

	if err := ctrl.UserService.UpdateUserPassword(id, req.NewPassword); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Contraseña actualizada exitosamente"})
}

func (ctrl *UserController) Delete(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	if err := ctrl.UserService.DeleteUser(id); err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Usuario eliminado exitosamente"})
}

func (ctrl *UserController) ChangeAccountStatus(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var req struct {
		Active bool `json:"active"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	if err := ctrl.UserService.SetAccountStatus(id, req.Active); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	status := "desactivada"
	if req.Active {
		status = "activada"
	}
	
	c.JSON(http.StatusOK, gin.H{"message": fmt.Sprintf("Cuenta %s exitosamente", status)})
}

func (ctrl *UserController) UnlockAccount(c *gin.Context) {
	userID, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	adminID := c.GetUint("userID")
	
	if err := ctrl.UserService.UnlockAccount(userID, adminID); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Cuenta desbloqueada exitosamente"})
}

func (ctrl *UserController) GetLoginAttempts(c *gin.Context) {
	userID, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	attempts, lastAttempt, err := ctrl.UserService.GetLoginAttempts(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener intentos"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"attempts":     attempts,
		"last_attempt": lastAttempt,
	})
}

func (ctrl *UserController) RefreshToken(c *gin.Context) {
	var req struct {
		RefreshToken string `json:"refresh_token" binding:"required"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Token de refresco requerido"})
		return
	}

	newToken, newRefreshToken, err := utils.RefreshToken(req.RefreshToken)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"bearer_token":  newToken,
		"refresh_token": newRefreshToken,
	})
}

func (ctrl *UserController) Search(c *gin.Context) {
	term := c.Query("term")
	if term == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Término de búsqueda requerido"})
		return
	}

	users, err := ctrl.UserService.SearchUserByField("", term)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error en la búsqueda"})
		return
	}

	c.JSON(http.StatusOK, users)
}

func (ctrl *UserController) GetCurrentUser(c *gin.Context) {
	userID := c.GetUint("userID")
	
	user, err := ctrl.UserService.GetUserByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}
	
	c.JSON(http.StatusOK, user)
}

// generateOTP genera un código OTP aleatorio de la longitud especificada

func generateOTP(length int) string {
	charset := "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	var otp strings.Builder
	for i := 0; i < length; i++ {
		idx, err := rand.Int(rand.Reader, big.NewInt(int64(len(charset))))
		if err != nil {
			log.Printf("Error generando OTP: %v", err)
			return ""
		}
		otp.WriteByte(charset[idx.Int64()])
	}
	return otp.String()
}

func getClientIP(c *gin.Context) string {
	headers := []string{
		"X-Forwarded-For",
		"X-Real-IP",
		"CF-Connecting-IP",
		"True-Client-IP",
	}
	
	for _, header := range headers {
		ip := c.GetHeader(header)
		if ip != "" {
			ips := strings.Split(ip, ",")
			return strings.TrimSpace(ips[0])
		}
	}
	
	return c.ClientIP()
}