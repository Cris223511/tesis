package controllers

import (
	"bytes"
	"crypto/rand"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"net/smtp"
	"os"
	"strconv"
	"strings"

	"time"

	"usuarios/dto"
	"usuarios/models"
	"usuarios/service"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
)


type UserController struct {
	UserService services.UserService
	OTPService  services.OTPService
	 DeviceIPRepo services.DeviceIPRepo 
}



func NewUserController(userService services.UserService, otpService services.OTPService,  deviceIPRepo services.DeviceIPRepo) *UserController {
    return &UserController{
        UserService: userService,
        OTPService:  otpService,  
		DeviceIPRepo: deviceIPRepo,
    }
}

func (ctrl *UserController) Login(c *gin.Context) {
    var loginRequest struct {
        NombreUsuario string `json:"usuario" binding:"required"`
        Password      string `json:"contrasena" binding:"required"`  
    }
    
    if err := c.ShouldBindJSON(&loginRequest); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "Debe proporcionar nombre de usuario y contraseña"})
        return
    }

    clientIP := getClientIP(c)
    userAgent := c.GetHeader("User-Agent")

    user, err := ctrl.UserService.LoginUser(loginRequest.NombreUsuario, loginRequest.Password, clientIP, userAgent)

    if err != nil {
        errMsg := err.Error()
        
        switch {
        case errMsg == "credenciales inválidas":
            c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario o contraseña incorrectos"})
        case errMsg == "cuenta desactivada":
            c.JSON(http.StatusForbidden, gin.H{"error": "La cuenta se encuentra desactivada"})
        case strings.HasPrefix(errMsg, "cuenta bloqueada, intente en"):
            c.JSON(http.StatusForbidden, gin.H{"error": errMsg})
        case errMsg == "contraseña expirada, debe actualizarla":
            c.JSON(http.StatusUnauthorized, gin.H{"error": "Tu contraseña ha expirado, debes actualizarla"})
        case errMsg == "demasiados intentos, intente más tarde":
            c.JSON(http.StatusTooManyRequests, gin.H{"error": "Demasiados intentos fallidos"})
        case errMsg == "acceso denegado desde esta ubicación":
            c.JSON(http.StatusForbidden, gin.H{"error": "Acceso bloqueado desde esta ubicación"})
        case errMsg == "solicitud inválida":
            c.JSON(http.StatusBadRequest, gin.H{"error": "Solicitud inválida"})
        default:
            c.JSON(http.StatusInternalServerError, gin.H{"error": "Error en el servidor"})
        }
        return
    }

    // Usar OTPService en lugar de otpCache
    _, err = ctrl.OTPService.GenerateOTP(user.ID)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{
            "error": "Error al generar código de verificación",
        })
        return
    }

    c.JSON(http.StatusOK, gin.H{
        "message": "Credenciales válidas. Se envió un código OTP a tu correo.",
        "user": gin.H{
			"user_id":           user.ID,
            "userId":           user.ID,
            "usuario":          user.Usuario,
            "correo":           user.Correo,
            "nombresApellidos": user.Nombres_Apellidos,
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
	if err := ctrl.OTPService.VerifyOTP(req.UserID, req.OTP); err != nil {
		switch {
		case strings.Contains(err.Error(), "expirado"):
			c.JSON(http.StatusUnauthorized, gin.H{"error": "OTP expirado"})
		case strings.Contains(err.Error(), "incorrecto"):
			c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
		case strings.Contains(err.Error(), "bloqueado"):
			c.JSON(http.StatusForbidden, gin.H{"error": err.Error()})
		default:
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		}
		return
	}
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
	ip := getClientIP(c)
	device := c.GetHeader("User-Agent")
	if known, _ := ctrl.DeviceIPRepo.GetIPSet(user.ID, device); known != nil {
		if _, exists := known[ip]; !exists {
			ctrl.DeviceIPRepo.AddIP(user.ID, device, ip)
			ctrl.DeviceIPRepo.TrimOldest(user.ID, device)
		}
	}
	go sendLoginNotificationEmail(user.Correo, user.Nombres_Apellidos, device, ip,(ip))
	var roles []string
	for _, r := range user.Roles {
		roles = append(roles, r.Name)
	}
	c.JSON(http.StatusOK, gin.H{
		"message":       "Autenticación exitosa",
		"user_id":       user.ID,
		"roles":         roles,
		"bearer_token":  token,
		"refresh_token": refreshToken,
		"user": gin.H{
	
			"id":                user.ID,
			"usuario":           user.Usuario,
			"nombresApellidos":  user.Nombres_Apellidos,
			"correo":            user.Correo,
			"telefono":          user.Telefono,
			"tipo_documento":     user.Tipo_Documento,
			"num_documento":   user.Num_Documento,
			"sexo":              user.Sexo,
			"activo":            user.Activo,
			"foto_movil":        user.FotoMovil,
			"fechaNacimiento":   user.FechaNacimiento,
		},
	})
}

func sendLoginNotificationEmail(to, name, device, ip, location string) {
	subject := "✅ Acceso Exitoso - Serious Game"

	// Parsear información del dispositivo
	deviceInfo := parseUserAgent(device)
	locationInfo := getLocationFromIP(ip)

	// Construir información detallada del dispositivo
	deviceDetails := deviceInfo.DeviceName
	if deviceInfo.OSVersion != "" {
		deviceDetails = fmt.Sprintf("%s (%s %s)", deviceInfo.DeviceName, deviceInfo.OS, deviceInfo.OSVersion)
	} else if deviceInfo.OS != "Sistema desconocido" {
		deviceDetails = fmt.Sprintf("%s (%s)", deviceInfo.DeviceName, deviceInfo.OS)
	}

	appVersionText := ""
	if deviceInfo.AppVersion != "" {
		appVersionText = fmt.Sprintf("<div style=\"margin-bottom: 15px; padding: 12px; border-radius: 8px; background-color: #f8fafc;\">
			<strong style=\"color: #374151; display: block; margin-bottom: 5px;\">📲 Versión de la App:</strong>
			<span style=\"color: #6b7280;\">%s</span>
		</div>", deviceInfo.AppVersion)
	}

	body := fmt.Sprintf(`
	<!DOCTYPE html>
	<html lang="es">
	<head>
		<meta charset="UTF-8">
		<meta name="viewport" content="width=device-width, initial-scale=1.0">
		<title>Acceso Exitoso</title>
	</head>
	<body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f8fafc; line-height: 1.6;">
		<div style="max-width: 600px; margin: 0 auto; background-color: #ffffff;">

			<div style="background: linear-gradient(135deg, #10b981 0%%, #059669 100%%); padding: 40px 30px; text-align: center; border-radius: 12px 12px 0 0;">
				<div style="background-color: rgba(255,255,255,0.1); padding: 15px; border-radius: 50%%; display: inline-block; margin-bottom: 20px;">
					<div style="width: 50px; height: 50px; background-color: #ffffff; border-radius: 50%%; display: flex; align-items: center; justify-content: center; margin: 0 auto;">
						<span style="font-size: 24px;">✅</span>
					</div>
				</div>
				<h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 700; letter-spacing: -0.5px;">SERIOUS GAME</h1>
				<p style="color: rgba(255,255,255,0.9); margin: 8px 0 0 0; font-size: 16px;">Plataforma de Terapias Emocionales</p>
			</div>

			<div style="padding: 50px 30px; background-color: #ffffff;">
				<div style="text-align: center; margin-bottom: 40px;">
					<h2 style="color: #1f2937; margin: 0 0 16px 0; font-size: 24px; font-weight: 600;">Acceso Exitoso</h2>
					<p style="color: #6b7280; font-size: 16px; margin: 0; line-height: 1.5;">
						Hola <strong style="color: #1f2937;">%s</strong>, acabas de iniciar sesión exitosamente en tu cuenta.
					</p>
				</div>

				<div style="background-color: #f0f9ff; border-radius: 16px; padding: 30px; margin: 30px 0; border-left: 4px solid #10b981;">
					<h3 style="color: #1f2937; margin: 0 0 20px 0; font-size: 18px; font-weight: 600; display: flex; align-items: center;">
						<span style="margin-right: 10px;">🔐</span>
						Detalles de la Sesión
					</h3>
					<div style="background-color: #ffffff; border-radius: 12px; padding: 20px;">
						<div style="margin-bottom: 15px; padding: 12px; border-radius: 8px; background-color: #f8fafc;">
							<strong style="color: #374151; display: block; margin-bottom: 5px;">📱 Dispositivo:</strong>
							<span style="color: #6b7280;">%s</span>
						</div>
						<div style="margin-bottom: 15px; padding: 12px; border-radius: 8px; background-color: #f8fafc;">
							<strong style="color: #374151; display: block; margin-bottom: 5px;">💻 Sistema Operativo:</strong>
							<span style="color: #6b7280;">%s %s</span>
						</div>
						%s
						<div style="margin-bottom: 15px; padding: 12px; border-radius: 8px; background-color: #f8fafc;">
							<strong style="color: #374151; display: block; margin-bottom: 5px;">🌍 Ubicación:</strong>
							<span style="color: #6b7280;">%s</span>
						</div>
						<div style="margin-bottom: 15px; padding: 12px; border-radius: 8px; background-color: #f8fafc;">
							<strong style="color: #374151; display: block; margin-bottom: 5px;">🌐 Dirección IP:</strong>
							<span style="color: #6b7280;">%s</span>
						</div>
						<div style="padding: 12px; border-radius: 8px; background-color: #f8fafc;">
							<strong style="color: #374151; display: block; margin-bottom: 5px;">⏰ Fecha y Hora:</strong>
							<span style="color: #6b7280;">%s</span>
						</div>
					</div>
				</div>

				<div style="background-color: #fef3c7; border-left: 4px solid #f59e0b; border-radius: 8px; padding: 20px; margin: 30px 0;">
					<div style="display: flex; align-items: flex-start;">
						<span style="font-size: 18px; margin-right: 12px;">⚠️</span>
						<div>
							<h3 style="color: #92400e; margin: 0 0 8px 0; font-size: 16px; font-weight: 600;">Aviso de Seguridad</h3>
							<p style="color: #a16207; margin: 0; font-size: 14px; line-height: 1.5;">
								Si <strong>no reconoces esta actividad</strong>, tu cuenta podría estar comprometida.<br>
								• Cambia tu contraseña inmediatamente<br>
								• Contacta a nuestro equipo de soporte<br>
								• Revisa tu actividad reciente
							</p>
						</div>
					</div>
				</div>

				<div style="text-align: center; margin-top: 40px;">
					<p style="color: #6b7280; font-size: 14px; margin: 0;">
						¿Necesitas ayuda? Contacta a nuestro equipo de soporte<br>
						<a href="mailto:soporte@seriousgame.com" style="color: #10b981; text-decoration: none; font-weight: 500;">soporte@seriousgame.com</a>
					</p>
				</div>
			</div>

			<div style="background-color: #f8fafc; padding: 30px; text-align: center; border-radius: 0 0 12px 12px; border-top: 1px solid #e5e7eb;">
				<div style="margin-bottom: 15px;">
					<p style="color: #9ca3af; font-size: 12px; margin: 0;">
						© %d Serious Game • Plataforma de Terapias Emocionales
					</p>
				</div>
				<div style="border-top: 1px solid #e5e7eb; padding-top: 15px; margin-top: 15px;">
					<p style="color: #9ca3af; font-size: 11px; margin: 0; line-height: 1.4;">
						Este correo fue enviado automáticamente desde una cuenta no monitoreada.<br>
						Por favor, no respondas a este mensaje directamente.
					</p>
				</div>
			</div>
		</div>

		<style>
			@media only screen and (max-width: 600px) {
				.container { width: 100%% !important; }
				.content { padding: 30px 20px !important; }
				.session-details { padding: 20px 15px !important; }
			}
		</style>
	</body>
	</html>`,
		name,
		deviceDetails,
		deviceInfo.OS,
		deviceInfo.OSVersion,
		appVersionText,
		locationInfo,
		ip,
		time.Now().Format("02/01/2006 15:04:05"),
		time.Now().Year(),
	)
	_ = sendEmail(to, subject, body)
}


func (ctrl *UserController) ResendOTP(c *gin.Context) {
    var req struct {
        UserID uint `json:"user_id" binding:"required"`
    }
    
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
        return
    }

    // Verificar que el usuario existe
    user, err := ctrl.UserService.GetUserByID(req.UserID)
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
        return
    }

    // Usar OTPService para reenviar
    err = ctrl.OTPService.ResendOTP(user.ID)
    if err != nil {
        errMsg := err.Error()
        
        switch {
        case strings.Contains(errMsg, "bloqueado"):
            c.JSON(http.StatusForbidden, gin.H{"error": errMsg})
        case strings.Contains(errMsg, "espera"):
            c.JSON(http.StatusTooManyRequests, gin.H{"error": errMsg})
        case strings.Contains(errMsg, "límite"):
            c.JSON(http.StatusForbidden, gin.H{"error": errMsg})
        default:
            c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al reenviar código"})
        }
        return
    }

    // Obtener información de reenvíos restantes
    var resendRecord models.OTPResend
    ctrl.UserService.DB().Where("user_id = ?", user.ID).First(&resendRecord)
    reenviosRestantes := 3 - resendRecord.ResendCount

    c.JSON(http.StatusOK, gin.H{
        "message": "Nuevo código OTP enviado",
        "reenviosRestantes": reenviosRestantes,
    })
}







func (ctrl *UserController) Register(c *gin.Context) {
    var req dto.RegisterRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos: " + err.Error()})
        return
    }

	log.Printf("RegisterRequest recibido: %+v", req)

    // Convertir a models.Usuarios
    user := models.Usuarios{
        Nombres_Apellidos: req.NombresApellidos,
        Tipo_Documento:   req.TipoDocumento,
        Num_Documento:    req.NumDocumento,
        Sexo:             req.Sexo,
        Telefono:         req.Telefono,
        Correo:           req.Correo,
        RoleIDs:          req.RoleIDs,
		
		
    }

	log.Printf("Usuario antes de CreateUser: Nombres_Apellidos='%s'", user.Nombres_Apellidos)
    log.Printf("Usuario completo: %+v", user)

	tempPassword, err := ctrl.UserService.CreateUser(&user)
    if err != nil {
        log.Printf("Error detallado al crear usuario: %v", err)
        c.JSON(http.StatusBadRequest, gin.H{
            "error": err.Error(),
            "debug": fmt.Sprintf("%+v", err), 
        })
        return
    }

    c.JSON(http.StatusCreated, gin.H{
        "message": "Usuario registrado exitosamente",
        "user": gin.H{
            "id":               user.ID,
            "usuario":          user.Usuario,
            "correo":           user.Correo,
            "password_temporal": tempPassword,
        },
    })
}


func parseFecha(fechaStr string) sql.NullTime {
    layout := "2006-01-02"
    fecha, err := time.Parse(layout, fechaStr)
    if err != nil {
        return sql.NullTime{Valid: false}
    }
    return sql.NullTime{
        Time:  fecha,
        Valid: true,
    }
}


func (ctrl *UserController) List(c *gin.Context) {
    page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
    perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "5"))  // Cambiado a 5 usuarios por página
    
    // Obtener todos los usuarios para contar
    var total int64
    ctrl.UserService.DB().Model(&models.Usuarios{}).Count(&total)
    
    // Calcular offset
    offset := (page - 1) * perPage
    
    // Obtener usuarios paginados
    var users []models.Usuarios
    err := ctrl.UserService.DB().
        Preload("Roles").
        Limit(perPage).
        Offset(offset).
        Find(&users).Error
        
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuarios"})
        return
    }
    
    totalPages := int(total) / perPage
    if int(total)%perPage > 0 {
        totalPages++
    }
    
    c.JSON(http.StatusOK, gin.H{
        "users":       users,
        "total":       total,
        "page":        page,
        "per_page":    perPage,
        "total_pages": totalPages,
    })
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

	var fullRequest map[string]interface{}
	if err := c.ShouldBindJSON(&fullRequest); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	userClaims, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	claims := userClaims.(*utils.Claims)
	currentUserID := claims.UserID

	roleIDs := []uint{}
	if roleIDsInterface, ok := fullRequest["role_ids"]; ok {
		if roleIDsSlice, ok := roleIDsInterface.([]interface{}); ok {
			for _, roleID := range roleIDsSlice {
				if roleIDFloat, ok := roleID.(float64); ok {
					roleIDs = append(roleIDs, uint(roleIDFloat))
				}
			}
		}
		delete(fullRequest, "role_ids")
	}

	if len(roleIDs) > 0 {
		if err := ctrl.UserService.UpdateUserWithRoles(id, fullRequest, roleIDs, currentUserID); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
	} else {
		if err := ctrl.UserService.UpdateUser(id, fullRequest); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
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

	userClaims, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Usuario no autenticado"})
		return
	}

	claims := userClaims.(*utils.Claims)
	currentUserID := claims.UserID

	if err := ctrl.UserService.DeleteUserWithValidation(id, currentUserID); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Usuario eliminado exitosamente"})
}


func (ctrl *UserController) UploadPhoto(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token no proporcionado"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
		return
	}

	var req struct {
		Photo string `json:"photo"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	if req.Photo != "" && len(req.Photo) > 5*1024*1024 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "La imagen excede el tamaño máximo de 5MB"})
		return
	}

	changeCount, err := ctrl.UserService.GetPhotoChangeCount(claims.UserID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al verificar cambios de foto"})
		return
	}

	if changeCount >= 2 {
		c.JSON(http.StatusForbidden, gin.H{"error": "Has alcanzado el límite máximo de 2 cambios de foto"})
		return
	}

	log.Printf("Actualizando foto para usuario %d, tamaño de datos: %d bytes", claims.UserID, len(req.Photo))
	
	if err := ctrl.UserService.UpdateUserPhoto(claims.UserID, req.Photo); err != nil {
		log.Printf("Error al actualizar foto: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al actualizar la foto"})
		return
	}

	log.Printf("Foto actualizada exitosamente para usuario %d", claims.UserID)
	
	c.JSON(http.StatusOK, gin.H{
		"message": "Foto actualizada correctamente",
		"changes_remaining": 2 - (changeCount + 1),
	})
}

func (ctrl *UserController) UploadBanner(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token no proporcionado"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
		return
	}

	var req struct {
		Banner string `json:"banner_movil"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	if req.Banner != "" && len(req.Banner) > 5*1024*1024 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "La imagen excede el tamaño máximo de 5MB"})
		return
	}

	changeCount, err := ctrl.UserService.GetBannerChangeCount(claims.UserID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al verificar cambios de banner"})
		return
	}

	if changeCount >= 2 {
		c.JSON(http.StatusForbidden, gin.H{"error": "Has alcanzado el límite máximo de 2 cambios de banner"})
		return
	}

	log.Printf("Actualizando banner para usuario %d, tamaño de datos: %d bytes", claims.UserID, len(req.Banner))
	
	if err := ctrl.UserService.UpdateUserBanner(claims.UserID, req.Banner); err != nil {
		log.Printf("Error al actualizar banner: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al actualizar el banner"})
		return
	}

	log.Printf("Banner actualizado exitosamente para usuario %d", claims.UserID)
	
	c.JSON(http.StatusOK, gin.H{
		"message": "Banner actualizado correctamente",
		"changes_remaining": 2 - (changeCount + 1),
	})
}

func (ctrl *UserController) GetBannerChanges(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token no proporcionado"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
		return
	}

	count, err := ctrl.UserService.GetBannerChangeCount(claims.UserID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener información"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"changes_used": count,
		"changes_remaining": 2 - count,
		"max_changes": 2,
	})
}

func (ctrl *UserController) GetPhotoChanges(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token no proporcionado"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
		return
	}

	count, err := ctrl.UserService.GetPhotoChangeCount(claims.UserID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener información"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"changes_used": count,
		"changes_remaining": 2 - count,
		"max_changes": 2,
	})
}

func (ctrl *UserController) ChangeAccountStatus(c *gin.Context) {
	id, err := utils.ParseID(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
		return
	}

	var req struct {
		Activo bool `json:"activo"`
	}
	
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos: " + err.Error()})
		return
	}

	// Log para debugging
	log.Printf("Cambiando estado de usuario %d a %v", id, req.Activo)

	if err := ctrl.UserService.SetAccountStatus(id, req.Activo); err != nil {
		log.Printf("Error en SetAccountStatus: %v", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	status := "desactivada"
	if req.Activo {
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
    // Cambiar de "search" a "q"
    q := c.Query("q")
    limit, _ := strconv.Atoi(c.DefaultQuery("limit", "5"))  // Cambiado a 5 usuarios por búsqueda

    var users []models.Usuarios
    query := ctrl.UserService.DB().Preload("Roles")

    if q != "" {
        // Si hay término de búsqueda, filtrar
        searchPattern := "%" + q + "%"
        query = query.Where("nombres_apellidos LIKE ? OR num_documento LIKE ? OR correo LIKE ?",
            searchPattern, searchPattern, searchPattern)
    }

    err := query.
        Limit(limit).
        Order("created_at DESC").
        Find(&users).Error
        
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al buscar usuarios"})
        return
    }
    
    // IMPORTANTE: Devolver solo el array de usuarios, no un objeto
    c.JSON(http.StatusOK, users)
}




func (ctrl *UserController) GetUserProfile(c *gin.Context) {
	var userID uint
	var err error
	
	if idParam := c.Param("id"); idParam != "" {
		userID, err = utils.ParseID(idParam)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "ID inválido"})
			return
		}
		
		claimsInterface, exists := c.Get("user")
		if !exists {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "No autorizado"})
			return
		}
		
		claims, ok := claimsInterface.(*utils.Claims)
		if !ok {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al procesar token"})
			return
		}
		
		requesterID := claims.UserID
		roles := strings.Split(claims.Roles, ",")
		isAdmin := false
		for _, role := range roles {
			if role == "administrador" || role == "AD" {
				isAdmin = true
				break
			}
		}
		
		if requesterID != userID && !isAdmin {
			c.JSON(http.StatusForbidden, gin.H{"error": "No tienes permisos para ver este perfil"})
			return
		}
	} else {
		claimsInterface, exists := c.Get("user")
		if !exists {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "No autorizado"})
			return
		}
		
		claims, ok := claimsInterface.(*utils.Claims)
		if !ok {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al procesar token"})
			return
		}
		
		userID = claims.UserID
		fmt.Printf("[DEBUG] GetUserProfile: userID extraído del token: %d\n", userID)
	}
	
	fmt.Printf("[DEBUG] GetUserProfile: Buscando usuario con ID: %d\n", userID)
	var user models.Usuarios
	err = ctrl.UserService.DB().
		Preload("Roles").
		First(&user, userID).Error
	
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}
	
	var childrenCount int64
	var parentInfo []gin.H
	var roleNames []string
	
	for _, role := range user.Roles {
		roleNames = append(roleNames, role.Name)
		
		if role.Name == "Padre" {
			ctrl.UserService.DB().
				Model(&models.UserRelationship{}).
				Where("parent_id = ?", userID).
				Count(&childrenCount)
		}
		
		if role.Name == "Hijo" {
			var relationships []models.UserRelationship
			ctrl.UserService.DB().
				Preload("Parent").
				Where("child_id = ?", userID).
				Find(&relationships)
			
			for _, rel := range relationships {
				parentInfo = append(parentInfo, gin.H{
					"id":                rel.Parent.ID,
					"nombres_apellidos": rel.Parent.Nombres_Apellidos,
					"correo":            rel.Parent.Correo,
					"telefono":          rel.Parent.Telefono,
				})
			}
		}
	}
	
	fotoSize := 0
	if user.FotoMovil != "" {
		fotoSize = len(user.FotoMovil)
	}
	log.Printf("GetUserProfile - Usuario %d: tiene foto? %v (tamaño: %d bytes)", user.ID, user.FotoMovil != "", fotoSize)
	
	profileData := gin.H{
		"id":                user.ID,
		"usuario":           user.Usuario,
		"nombres_apellidos": user.Nombres_Apellidos,
		"correo":            user.Correo,
		"telefono":          user.Telefono,
		"tipo_documento":    user.Tipo_Documento,
		"num_documento":     user.Num_Documento,
		"sexo":              user.Sexo,
		"fecha_nacimiento":  user.FechaNacimiento,
		"descripcion":       user.Descripcion,
		"foto_movil":        user.FotoMovil,
		"banner_movil":      user.BannerMovil,
		"activo":            user.Activo,
		"children_count":    childrenCount,
		"parent_info":       parentInfo,
		"created_at":        user.CreatedAt,
		"last_login_at":     user.LastLoginAt,
		"roles":             roleNames,
	}
	
	c.JSON(http.StatusOK, profileData)
}



func (ctrl *UserController) GetCurrentUser(c *gin.Context) {
	claimsInterface, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "No autorizado"})
		return
	}
	
	claims, ok := claimsInterface.(*utils.Claims)
	if !ok {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al procesar token"})
		return
	}
	
	userID := claims.UserID
	
	user, err := ctrl.UserService.GetUserByID(userID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}
	
	c.JSON(http.StatusOK, user)
}

func (ctrl *UserController) GetUserChildren(c *gin.Context) {
	claimsInterface, exists := c.Get("user")
	if !exists {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "No autorizado"})
		return
	}
	
	claims, ok := claimsInterface.(*utils.Claims)
	if !ok {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al procesar token"})
		return
	}
	
	userID := claims.UserID
	
	var relationships []models.UserRelationship
	err := ctrl.UserService.DB().
		Preload("Child").
		Preload("Child.Roles").
		Where("parent_id = ?", userID).
		Find(&relationships).Error
	
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener hijos"})
		return
	}
	
	var children []gin.H
	for _, rel := range relationships {
		var roleNames []string
		for _, role := range rel.Child.Roles {
			roleNames = append(roleNames, role.Name)
		}
		
		children = append(children, gin.H{
			"id":                rel.Child.ID,
			"usuario":           rel.Child.Usuario,
			"nombres_apellidos": rel.Child.Nombres_Apellidos,
			"correo":            rel.Child.Correo,
			"telefono":          rel.Child.Telefono,
			"sexo":              rel.Child.Sexo,
			"fecha_nacimiento":  rel.Child.FechaNacimiento,
			"activo":            rel.Child.Activo,
			"roles":             roleNames,
		})
	}
	
	c.JSON(http.StatusOK, gin.H{
		"total":    len(children),
		"children": children,
	})
}

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


func SendLoginNotificationEmail(to, name, device, ip string) error {
	subject := "Inicio de Sesión Exitoso - Serious Game"
	body := fmt.Sprintf(`
<!DOCTYPE html>
<html>
<body style="font-family:Arial,sans-serif;background-color:#f5f5f5;padding:20px;">
  <div style="max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 6px rgba(0,0,0,0.1);">
    <div style="background:#004165;color:#fff;padding:20px;text-align:center;">
      <h1 style="margin:0;font-size:24px;">Inicio de Sesión Exitoso</h1>
    </div>
    <div style="padding:30px;color:#333;">
      <p>Hola <strong>%s</strong>,</p>
      <p>Acabas de iniciar sesión en Serious Game:</p>
      <ul>
        <li><strong>Dispositivo:</strong> %s</li>
        <li><strong>Ubicación:</strong> %s</li>
        <li><strong>Dirección IP:</strong> %s</li>
        <li><strong>Fecha y Hora:</strong> %s</li>
      </ul>
      <p style="color:#d32f2f;font-weight:bold;">
        Si no reconoces esta actividad, contacta con soporte inmediatamente.
      </p>
    </div>
  </div>
</body>
</html>`,
		name,
		device,
		getLocationFromIP(ip),
		ip,
		time.Now().Format("02/01/2006 15:04:05"),
	)
	return sendEmail(to, subject, body)
}

func sendEmail(to, subject, body string) error {
	host := getEnv("SMTP_HOST", "smtp.gmail.com")
	port := getEnv("SMTP_PORT", "587")
	user := os.Getenv("SMTP_EMAIL")
	pass := os.Getenv("SMTP_PASSWORD")
	auth := smtp.PlainAuth("", user, pass, host)

	headers := map[string]string{
		"From":         fmt.Sprintf("Serious Game <%s>", user),
		"To":           to,
		"Subject":      subject,
		"MIME-Version": "1.0",
		"Content-Type": "text/html; charset=UTF-8",
	}
	var msg bytes.Buffer
	for k, v := range headers {
		msg.WriteString(fmt.Sprintf("%s: %s\r\n", k, v))
	}
	msg.WriteString("\r\n")
	msg.WriteString(body)

	return smtp.SendMail(host+":"+port, auth, user, []string{to}, msg.Bytes())
}

type LocationResponse struct {
	City        string  `json:"city"`
	Region      string  `json:"region"`
	Country     string  `json:"country"`
	CountryName string  `json:"country_name"`
	Postal      string  `json:"postal"`
	Latitude    float64 `json:"latitude"`
	Longitude   float64 `json:"longitude"`
	Timezone    string  `json:"timezone"`
	Org         string  `json:"org"`
	Error       bool    `json:"error"`
	Reason      string  `json:"reason"`
}

func getLocationFromIP(ip string) string {

	if ip == "127.0.0.1" || ip == "::1" {
		return "Servidor local"
	}
	if strings.HasPrefix(ip, "192.168.") || strings.HasPrefix(ip, "10.") || strings.HasPrefix(ip, "172.") {
		return "Red local"
	}

	
	url := fmt.Sprintf("https://ipapi.co/%s/json/", ip)

	client := &http.Client{
		Timeout: 5 * time.Second, 
	}

	resp, err := client.Get(url)
	if err != nil {
		log.Printf("Error al consultar ubicación para IP %s: %v", ip, err)
		return "Ubicación desconocida"
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf("Error HTTP al consultar ubicación para IP %s: %d", ip, resp.StatusCode)
		return "Ubicación desconocida"
	}

	var location LocationResponse
	if err := json.NewDecoder(resp.Body).Decode(&location); err != nil {
		log.Printf("Error al decodificar respuesta de ubicación para IP %s: %v", ip, err)
		return "Ubicación desconocida"
	}


	if location.Error {
		log.Printf("Error en API de ubicación para IP %s: %s", ip, location.Reason)
		return "Ubicación desconocida"
	}


	var locationParts []string

	if location.City != "" {
		locationParts = append(locationParts, location.City)
	}

	if location.Region != "" && location.Region != location.City {
		locationParts = append(locationParts, location.Region)
	}

	if location.CountryName != "" {
		locationParts = append(locationParts, location.CountryName)
	} else if location.Country != "" {
		locationParts = append(locationParts, location.Country)
	}


	ispInfo := ""
	if location.Org != "" {
	
		org := strings.TrimSpace(location.Org)
		if strings.HasPrefix(org, "AS") {
			if spaceIndex := strings.Index(org, " "); spaceIndex > 0 {
				org = strings.TrimSpace(org[spaceIndex:])
			}
		}
		ispInfo = fmt.Sprintf(" (%s)", org)
	}

	if len(locationParts) > 0 {
		result := strings.Join(locationParts, ", ") + ispInfo
		log.Printf("Ubicación detectada para IP %s: %s", ip, result)
		return result
	}

	return "Ubicación desconocida"
}

type DeviceInfo struct {
	DeviceName string
	OS         string
	OSVersion  string
	AppVersion string
}

func parseUserAgent(userAgent string) DeviceInfo {
	info := DeviceInfo{
		DeviceName: "Dispositivo desconocido",
		OS:         "Sistema desconocido",
		OSVersion:  "",
		AppVersion: "",
	}

	userAgent = strings.ToLower(userAgent)

	// Detectar Android
	if strings.Contains(userAgent, "android") {
		info.OS = "Android"

		// Extraer versión de Android
		if idx := strings.Index(userAgent, "android "); idx != -1 {
			start := idx + 8
			end := start
			for end < len(userAgent) && (userAgent[end] >= '0' && userAgent[end] <= '9' || userAgent[end] == '.') {
				end++
			}
			if end > start {
				info.OSVersion = userAgent[start:end]
			}
		}

		// Detectar modelo de dispositivo Android
		if strings.Contains(userAgent, "samsung") {
			if strings.Contains(userAgent, "sm-g") {
				if strings.Contains(userAgent, "sm-g998") {
					info.DeviceName = "Samsung Galaxy S21 Ultra"
				} else if strings.Contains(userAgent, "sm-g996") {
					info.DeviceName = "Samsung Galaxy S21+"
				} else if strings.Contains(userAgent, "sm-g991") {
					info.DeviceName = "Samsung Galaxy S21"
				} else {
					info.DeviceName = "Samsung Galaxy"
				}
			} else {
				info.DeviceName = "Samsung"
			}
		} else if strings.Contains(userAgent, "huawei") {
			info.DeviceName = "Huawei"
		} else if strings.Contains(userAgent, "xiaomi") {
			info.DeviceName = "Xiaomi"
		} else if strings.Contains(userAgent, "oneplus") {
			info.DeviceName = "OnePlus"
		} else if strings.Contains(userAgent, "pixel") {
			info.DeviceName = "Google Pixel"
		} else {
			info.DeviceName = "Android"
		}
	}

	// Detectar iOS
	if strings.Contains(userAgent, "iphone") || strings.Contains(userAgent, "ios") {
		info.OS = "iOS"
		info.DeviceName = "iPhone"

		// Extraer versión de iOS
		if idx := strings.Index(userAgent, "os "); idx != -1 {
			start := idx + 3
			end := start
			for end < len(userAgent) && (userAgent[end] >= '0' && userAgent[end] <= '9' || userAgent[end] == '_' || userAgent[end] == '.') {
				end++
			}
			if end > start {
				version := strings.Replace(userAgent[start:end], "_", ".", -1)
				info.OSVersion = version
			}
		}
	}

	// Detectar versión de la app (si está en el User-Agent)
	if strings.Contains(userAgent, "serious_game_app") {
		if idx := strings.Index(userAgent, "serious_game_app/"); idx != -1 {
			start := idx + 17
			end := start
			for end < len(userAgent) && userAgent[end] != ' ' && userAgent[end] != ')' {
				end++
			}
			if end > start {
				info.AppVersion = userAgent[start:end]
			}
		}
	}

	return info
}

func getEnv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func (ctrl *UserController) ValidateEmailForPasswordChange(c *gin.Context) {
	var request struct {
		Email string `json:"correo" binding:"required,email"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Correo electrónico requerido"})
		return
	}

	user, err := ctrl.UserService.GetUserByEmail(request.Email)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "No existe una cuenta asociada a este correo electrónico"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Correo válido",
		"user_id": user.ID,
	})
}

func (ctrl *UserController) SendPasswordChangeOTP(c *gin.Context) {
	var request struct {
		Email string `json:"correo" binding:"required,email"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Correo electrónico requerido"})
		return
	}

	user, err := ctrl.UserService.GetUserByEmail(request.Email)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "No existe una cuenta asociada a este correo electrónico"})
		return
	}

	otp := generateOTP(6)
	err = ctrl.OTPService.SaveOTP(user.ID, otp)
	if err != nil {
		log.Printf("Error al guardar OTP: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error interno del servidor"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Código de verificación generado",
		"otp_code": otp,
	})
}

func (ctrl *UserController) VerifyPasswordOTP(c *gin.Context) {
	var request struct {
		Email   string `json:"correo" binding:"required,email"`
		OTPCode string `json:"codigo_otp" binding:"required"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Email y código OTP requeridos"})
		return
	}

	user, err := ctrl.UserService.GetUserByEmail(request.Email)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}

	isValid, err := ctrl.OTPService.CheckOTP(user.ID, request.OTPCode)
	if err != nil || !isValid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Código de verificación inválido o expirado"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Código verificado correctamente",
	})
}

func (ctrl *UserController) ChangePasswordWithOTP(c *gin.Context) {
	var request struct {
		Email           string `json:"correo" binding:"required,email"`
		OTPCode         string `json:"codigo_otp" binding:"required,len=6"`
		NewPassword     string `json:"nueva_contrasena" binding:"required,min=8"`
		ConfirmPassword string `json:"confirmar_contrasena" binding:"required"`
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Todos los campos son requeridos"})
		return
	}

	if request.NewPassword != request.ConfirmPassword {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Las contraseñas no coinciden"})
		return
	}

	if !isValidPassword(request.NewPassword) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "La contraseña debe tener al menos 8 caracteres, incluir mayúsculas, minúsculas, números y caracteres especiales"})
		return
	}

	user, err := ctrl.UserService.GetUserByEmail(request.Email)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
		return
	}

	isValid, err := ctrl.OTPService.ValidateOTP(user.ID, request.OTPCode)
	if err != nil || !isValid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Código de verificación inválido o expirado"})
		return
	}

	recentPasswords, err := ctrl.UserService.GetRecentPasswords(user.ID, 5)
	if err != nil {
		log.Printf("Error al obtener contraseñas recientes: %v", err)
	}

	for _, passHistory := range recentPasswords {
		if passHistory.CheckPassword(request.NewPassword) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "No puedes usar una de tus últimas 5 contraseñas"})
			return
		}
	}

	clientIP := c.ClientIP()
	err = ctrl.UserService.ChangePassword(user.ID, request.NewPassword, "password_reset", clientIP)
	if err != nil {
		log.Printf("Error al cambiar contraseña: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al cambiar la contraseña"})
		return
	}

	err = ctrl.OTPService.InvalidateUserOTPs(user.ID)
	if err != nil {
		log.Printf("Error al invalidar OTPs: %v", err)
	}

	c.JSON(http.StatusOK, gin.H{
		"message": "Contraseña cambiada exitosamente",
	})
}

func sendPasswordChangeOTPEmail(to, name, otp string) error {
	user := getEnv("EMAIL_USER", "")
	password := getEnv("EMAIL_PASSWORD", "")
	host := getEnv("EMAIL_HOST", "smtp.gmail.com")
	port := getEnv("EMAIL_PORT", "587")

	subject := "Código de verificación para cambio de contraseña"
	body := fmt.Sprintf(`
Hola %s,

Has solicitado cambiar tu contraseña. Tu código de verificación es:

%s

Este código expira en 1 minuto.

Si no solicitaste este cambio, ignora este correo.

Saludos,
Equipo de Seguridad
`, name, otp)

	auth := smtp.PlainAuth("", user, password, host)

	var msg bytes.Buffer
	msg.WriteString("To: " + to + "\r\n")
	msg.WriteString("Subject: " + subject + "\r\n")
	msg.WriteString("Content-Type: text/plain; charset=UTF-8\r\n")
	msg.WriteString("\r\n")
	msg.WriteString(body)

	return smtp.SendMail(host+":"+port, auth, user, []string{to}, msg.Bytes())
}

func isValidPassword(password string) bool {
	if len(password) < 8 {
		return false
	}

	hasUpper := false
	hasLower := false
	hasNumber := false
	hasSpecial := false

	for _, char := range password {
		switch {
		case char >= 'A' && char <= 'Z':
			hasUpper = true
		case char >= 'a' && char <= 'z':
			hasLower = true
		case char >= '0' && char <= '9':
			hasNumber = true
		case strings.ContainsRune("!@#$%^&*()_+-=[]{}|;:,.<>?", char):
			hasSpecial = true
		}
	}

	return hasUpper && hasLower && hasNumber && hasSpecial
}

func (ctrl *UserController) UpdateProfile(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token de autorización requerido"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	userID := claims.UserID

	// Verificar límite de cambios
	changesUsed, err := ctrl.UserService.GetProfileChangeCount(userID)
	if err != nil {
		log.Printf("Error al verificar cambios de perfil: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error interno del servidor"})
		return
	}

	maxChanges := 2
	if changesUsed >= maxChanges {
		c.JSON(http.StatusForbidden, gin.H{"error": "Has alcanzado el límite máximo de 2 cambios de perfil"})
		return
	}

	var request struct {
		Correo   string `json:"correo" binding:"required,email"`
		Telefono string `json:"celular"` // Android envía "celular" pero lo mapeamos a Telefono
	}

	if err := c.ShouldBindJSON(&request); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Datos inválidos"})
		return
	}

	// Obtener datos actuales del usuario para comparar
	currentUser, err := ctrl.UserService.GetUserByID(userID)
	if err != nil {
		log.Printf("Error al obtener usuario actual: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener usuario"})
		return
	}

	// Determinar tipo de cambio
	changeType := ""
	if currentUser.Correo != request.Correo && currentUser.Telefono != request.Telefono {
		changeType = "both"
	} else if currentUser.Correo != request.Correo {
		changeType = "email"
	} else if currentUser.Telefono != request.Telefono {
		changeType = "phone"
	} else {
		c.JSON(http.StatusBadRequest, gin.H{"error": "No se detectaron cambios"})
		return
	}

	updates := map[string]interface{}{
		"correo":  request.Correo,
		"celular": request.Telefono, // Mapear telefono a celular para el service
	}

	err = ctrl.UserService.UpdateUser(userID, updates)
	if err != nil {
		log.Printf("Error al actualizar usuario: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al actualizar el perfil"})
		return
	}

	// Registrar el cambio
	err = ctrl.UserService.RecordProfileChange(userID, currentUser.Correo, request.Correo, currentUser.Telefono, request.Telefono, changeType)
	if err != nil {
		log.Printf("Error al registrar cambio de perfil: %v", err)
		// No fallar por esto, el update ya se realizó
	}

	updatedUser, err := ctrl.UserService.GetUserByID(userID)
	if err != nil {
		log.Printf("Error al obtener usuario actualizado: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al obtener perfil actualizado"})
		return
	}

	var roles []string
	for _, role := range updatedUser.Roles {
		roles = append(roles, role.Name)
	}

	c.JSON(http.StatusOK, gin.H{
		"id":                 updatedUser.ID,
		"usuario":            updatedUser.Usuario,
		"nombres_apellidos":  updatedUser.Nombres_Apellidos,
		"correo":             updatedUser.Correo,
		"telefono":           updatedUser.Telefono,
		"tipo_documento":     updatedUser.Tipo_Documento,
		"num_documento":      updatedUser.Num_Documento,
		"fecha_nacimiento":   updatedUser.FechaNacimiento,
		"sexo":               updatedUser.Sexo,
		"activo":             updatedUser.Activo,
		"foto_movil":         updatedUser.FotoMovil,
		"banner_movil":       updatedUser.BannerMovil,
		"descripcion":        updatedUser.Descripcion,
		"roles":              roles,
	})
}

func (ctrl *UserController) GetProfileChanges(c *gin.Context) {
	authHeader := c.GetHeader("Authorization")
	if authHeader == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token de autorización requerido"})
		return
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || parts[0] != "Bearer" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
		return
	}

	claims, err := utils.ValidateToken(parts[1])
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token inválido"})
		return
	}

	userID := claims.UserID

	changesUsed, err := ctrl.UserService.GetProfileChangeCount(userID)
	if err != nil {
		log.Printf("Error al obtener cambios de perfil: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Error interno del servidor"})
		return
	}

	maxChanges := 2
	changesRemaining := maxChanges - changesUsed
	if changesRemaining < 0 {
		changesRemaining = 0
	}

	c.JSON(http.StatusOK, gin.H{
		"changes_used":      changesUsed,
		"changes_remaining": changesRemaining,
		"max_changes":       maxChanges,
	})
}