package controllers

import (
    "net/http"
    "usuarios/utils"

    "github.com/gin-gonic/gin"
)

type AuthController struct{}

func NewAuthController() *AuthController {
    return &AuthController{}
}


func (ac *AuthController) GetToken(c *gin.Context) {
    token, err := utils.GenerateInitialAuthToken()
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Error generando token inicial"})
        return
    }
    c.JSON(http.StatusOK, gin.H{"chatAuthToken": token})
}
