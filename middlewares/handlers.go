package middlewares

import (
	"net/http"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
)

type refreshReq struct {
	RefreshToken string `json:"refresh_token"`
}

func RefreshToken(c *gin.Context) {
	var req refreshReq
	if err := c.ShouldBindJSON(&req); err != nil || req.RefreshToken == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "refresh_token faltante"})
		return
	}

	access, refresh, err := utils.RefreshToken(req.RefreshToken)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"bearer_token":  access,
		"refresh_token": refresh,
	})
}
