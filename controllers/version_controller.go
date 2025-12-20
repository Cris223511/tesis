package controllers

import (
	"net/http"
	"os"
	"strconv"

	"github.com/gin-gonic/gin"
)

type VersionController struct{}

func NewVersionController() *VersionController {
	return &VersionController{}
}

type AppVersionResponse struct {
	MinVersionCode int    `json:"min_version_code"`
	MinVersionName string `json:"min_version_name"`
	CurrentVersion string `json:"current_version"`
	ForceUpdate    bool   `json:"force_update"`
	StoreURL       string `json:"store_url"`
	Message        string `json:"message"`
}

func (vc *VersionController) CheckVersion(c *gin.Context) {

	minVersionCode := getEnvAsInt("MIN_APP_VERSION_CODE", 11)
	minVersionName := getEnvOrDefault("MIN_APP_VERSION_NAME", "2.0")
	currentVersion := getEnvOrDefault("CURRENT_APP_VERSION", "2.0")
	storeURL := getEnvOrDefault("PLAY_STORE_URL", "https://play.google.com/store/apps/details?id=com.usil.jhafet.seriousgame2024")
	updateMessage := getEnvOrDefault("UPDATE_MESSAGE", "Hay una nueva versión disponible. Por favor actualiza para continuar usando la aplicación.")

	// Obtener versión del cliente (opcional)
	clientVersionCode, _ := strconv.Atoi(c.Query("version_code"))

	// Determinar si necesita actualización forzada
	forceUpdate := clientVersionCode > 0 && clientVersionCode < minVersionCode

	c.JSON(http.StatusOK, AppVersionResponse{
		MinVersionCode: minVersionCode,
		MinVersionName: minVersionName,
		CurrentVersion: currentVersion,
		ForceUpdate:    forceUpdate,
		StoreURL:       storeURL,
		Message:        updateMessage,
	})
}

func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvAsInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intVal, err := strconv.Atoi(value); err == nil {
			return intVal
		}
	}
	return defaultValue
}