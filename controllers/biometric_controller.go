package controllers

import (
	"bytes"
	"encoding/base64"
	"encoding/json"

	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"

	"usuarios/service"
	"usuarios/utils"

	"github.com/duo-labs/webauthn/webauthn"
	"github.com/gin-gonic/gin"
)

type BioController struct {
    svc             *services.BioService
    maxDevicesPerUser int
}

func NewBioController(svc *services.BioService) *BioController {
    return &BioController{
        svc: svc,
        maxDevicesPerUser: 5, 
    }
}


func (bc *BioController) BeginRegister(c *gin.Context) {
    uid := c.MustGet("uid").(uint)
    
    // Usar el método correcto para obtener el usuario con sus credenciales
    user, err := bc.svc.GetUserForWebAuthn(uid)
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "usuario no encontrado"})
        return
    }

    opts, sessionData, err := bc.svc.Wa.BeginRegistration(user)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error iniciando registro biométrico"})
        return
    }

    sessionDataJSON, err := json.Marshal(sessionData)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error serializando sesión"})
        return
    }
    
    c.SetCookie("register_session", string(sessionDataJSON), 300, "/", "", false, true)
    
    c.JSON(http.StatusOK, opts)
}

func (bc *BioController) CheckBiometricCapabilities(c *gin.Context) {
    userID := c.MustGet("uid").(uint)
    
    canRegister, remaining, err := bc.svc.CanRegisterMoreDevices(userID, bc.maxDevicesPerUser)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al verificar dispositivos"})
        return
    }
    
    devices, err := bc.svc.ListDevices(userID)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al listar dispositivos"})
        return
    }
    
    c.JSON(http.StatusOK, gin.H{
        "biometric_enabled": true, 
        "can_register_more": canRegister,
        "max_devices": bc.maxDevicesPerUser,
        "remaining_slots": remaining,
        "registered_devices": len(devices),
        "devices": devices,
        "supported_methods": []string{"fingerprint", "face_recognition", "security_key"},
    })
}


func (bc *BioController) FinishRegister(c *gin.Context) {
    uid := c.MustGet("uid").(uint)
    
    // Usar el método correcto para obtener el usuario con sus credenciales
    user, err := bc.svc.GetUserForWebAuthn(uid)
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "usuario no encontrado"})
        return
    }
    
    // Verificar si puede registrar más dispositivos
    canRegister, _, err := bc.svc.CanRegisterMoreDevices(uid, bc.maxDevicesPerUser)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al verificar límites"})
        return
    }
    
    if !canRegister {
        c.JSON(http.StatusForbidden, gin.H{
            "error": fmt.Sprintf("Límite de %d dispositivos alcanzado", bc.maxDevicesPerUser),
        })
        return
    }

    sessionData, err := c.Cookie("register_session")
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "sesión faltante"})
        return
    }

    var sessionDataObj webauthn.SessionData
    if err := json.Unmarshal([]byte(sessionData), &sessionDataObj); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "sesión inválida"})
        return
    }
    
    // Obtener metadatos del dispositivo
    var req struct {
        DeviceName string `json:"device_name"`
        DeviceType string `json:"device_type"`
    }
    if err := c.ShouldBindJSON(&req); err != nil {
        req.DeviceName = ""
        req.DeviceType = ""
    }

    cred, err := bc.svc.Wa.FinishRegistration(user, sessionDataObj, c.Request)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "registro fallido: " + err.Error()})
        return
    }

    // Obtener el valor de transports de la credencial
    var credResp struct {
        Response struct {
            Transports []string `json:"transports"`
        } `json:"response"`
    }
    
    transportStr := ""
    if c.Request.Body != nil {
        body, _ := io.ReadAll(c.Request.Body)
        c.Request.Body = io.NopCloser(bytes.NewBuffer(body))
        
        if err := json.Unmarshal(body, &credResp); err == nil && len(credResp.Response.Transports) > 0 {
            transportStr = strings.Join(credResp.Response.Transports, ",")
        }
    }

    if err := bc.svc.SaveCredential(user, cred, req.DeviceName, req.DeviceType, transportStr); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "no se pudo guardar credencial"})
        return
    }
    

    c.SetCookie("register_session", "", -1, "/", "", false, true)

    c.JSON(http.StatusCreated, gin.H{
        "message": "biometría registrada correctamente",
        "credential_id": base64.StdEncoding.EncodeToString(cred.ID),
    })
}


func (bc *BioController) BeginLogin(c *gin.Context) {
    var req struct {
        NombreUsuario string `json:"nombre_usuario" binding:"required"`
    }
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "datos inválidos"})
        return
    }

    users, err := bc.svc.UserRepo.SearchUserByField("nombre_usuario", req.NombreUsuario)
    if err != nil || len(users) == 0 {
        c.JSON(http.StatusNotFound, gin.H{"error": "usuario no encontrado"})
        return
    }
    

    user := users[0]
    
    userWithCreds, err := bc.svc.GetUserForWebAuthn(user.ID)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error al obtener credenciales"})
        return
    }

    opts, sessionData, err := bc.svc.Wa.BeginLogin(userWithCreds)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error iniciando login biométrico"})
        return
    }

    sessionDataJSON, err := json.Marshal(sessionData)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error serializando sesión"})
        return
    }
    
    c.SetCookie("login_session", string(sessionDataJSON), 300, "/", "", false, true)
    c.SetCookie("login_uid", fmt.Sprint(user.ID), 300, "/", "", false, true)
    
    c.JSON(http.StatusOK, opts)
}

func (bc *BioController) FinishLogin(c *gin.Context) {
    uidStr, err := c.Cookie("login_uid")
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "sesión faltante"})
        return
    }
    uid, _ := strconv.ParseUint(uidStr, 10, 32)
    
    user, err := bc.svc.GetUserForWebAuthn(uint(uid))
    if err != nil {
        c.JSON(http.StatusNotFound, gin.H{"error": "usuario no encontrado"})
        return
    }

    sessionData, err := c.Cookie("login_session")
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "sesión faltante"})
        return
    }

    var sessionDataObj webauthn.SessionData
    if err := json.Unmarshal([]byte(sessionData), &sessionDataObj); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "sesión inválida"})
        return
    }

    cred, err := bc.svc.Wa.FinishLogin(user, sessionDataObj, c.Request)
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": "autenticación fallida: " + err.Error()})
        return
    }

    if err := bc.svc.UpdateLastUsed(cred.ID); err != nil {
        log.Printf("Error actualizando último uso: %v", err)
    }
    c.SetCookie("login_session", "", -1, "/", "", false, true)
    c.SetCookie("login_uid", "", -1, "/", "", false, true)

    token, refresh, err := utils.GenerateToken(user)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "error generando token"})
        return
    }

    c.JSON(http.StatusOK, gin.H{
        "bearer_token": token,
        "refresh_token": refresh,
        "user": gin.H{
            "id": user.ID,
            "nombre_aellidos": user.Nombre_Apellidos,
            
        },
    })
}


func (bc *BioController) ListDevices(c *gin.Context) {
    userID := c.MustGet("uid").(uint)
    
    devices, err := bc.svc.ListDevices(userID)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error al listar dispositivos"})
        return
    }
    
    c.JSON(http.StatusOK, gin.H{
        "devices": devices,
        "count": len(devices),
        "max_allowed": bc.maxDevicesPerUser,
    })
}


func (bc *BioController) DeleteDevice(c *gin.Context) {
    userID := c.MustGet("uid").(uint)
    
    var req struct {
        CredentialID string `json:"credential_id" binding:"required"`
    }
    
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "ID de credencial requerido"})
        return
    }
    
    credID, err := base64.StdEncoding.DecodeString(req.CredentialID)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "ID de credencial inválido"})
        return
    }
    
    if err := bc.svc.DeleteDevice(userID, credID); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    
    c.JSON(http.StatusOK, gin.H{"message": "Dispositivo eliminado correctamente"})
}


func (bc *BioController) RenameDevice(c *gin.Context) {
    userID := c.MustGet("uid").(uint)
    
    var req struct {
        CredentialID string `json:"credential_id" binding:"required"`
        Name         string `json:"name" binding:"required"`
    }
    
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "Datos incompletos"})
        return
    }
    
    credID, err := base64.StdEncoding.DecodeString(req.CredentialID)
    if err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "ID de credencial inválido"})
        return
    }
    
    if err := bc.svc.RenameDevice(userID, credID, req.Name); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    
    c.JSON(http.StatusOK, gin.H{"message": "Dispositivo renombrado correctamente"})
}