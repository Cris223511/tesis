package controllers

import (
	"crypto/rand"
	"database/sql"
	"fmt"
	"log"
	"math/big"
	"net/http"
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
}



func NewUserController(userService services.UserService, otpService services.OTPService) *UserController {
    return &UserController{
        UserService: userService,
        OTPService:  otpService,  
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

    // Usar OTPService para verificar
    err := ctrl.OTPService.VerifyOTP(req.UserID, req.OTP)
    if err != nil {
        errMsg := err.Error()
        
        switch {
        case strings.Contains(errMsg, "expirado"):
            c.JSON(http.StatusUnauthorized, gin.H{"error": "OTP expirado"})
        case strings.Contains(errMsg, "incorrecto"):
            c.JSON(http.StatusUnauthorized, gin.H{"error": errMsg})
        case strings.Contains(errMsg, "bloqueado"):
            c.JSON(http.StatusForbidden, gin.H{"error": errMsg})
        default:
            c.JSON(http.StatusBadRequest, gin.H{"error": errMsg})
        }
        return
    }

    // Obtener usuario para generar tokens
    user, err := ctrl.UserService.GetUserByID(req.UserID)
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "Usuario no encontrado"})
        return
    }

    // Generar tokens
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
        "user": gin.H{
			"user_id":           user.ID,
            "id":               user.ID,
            "usuario":          user.Usuario,
            "nombresApellidos": user.Nombres_Apellidos,
            "correo":           user.Correo,
            "telefono":         user.Telefono,
            "tipoDocumento":    user.Tipo_Documento,
            "numeroDocumento":  user.Num_Documento,
            "sexo":             user.Sexo,
            "activo":           user.Activo,
			"foto":            user.Foto,
			"fechaNacimiento": user.FechaNacimiento,

        },
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