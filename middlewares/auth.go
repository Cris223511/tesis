package middlewares

import (
	"log"
	"net/http"
	"os"
	"strings"
	"usuarios/utils"

	"github.com/gin-gonic/gin"
)

var jwtKey = []byte(os.Getenv("JWT_SECRET"))

func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		path := c.FullPath()
		if path == "/api/otp/validate" || strings.Contains(path, "otp") {
			log.Printf("[AUTH_MIDDLEWARE] FullPath: %s, RequestPath: %s", path, c.Request.URL.Path)
		}

		if strings.HasPrefix(path, "/un/") {
			c.Next()
			return
		}
		switch path {
		case "/api/login", "/api/otp/validate", "/api/otp/resend", "/api/refresh-token",
			"/api/biometric/login/begin", "/api/biometric/login/finish":
			c.Next()
			return
		}

		// Bearer <token>
		auth := c.GetHeader("Authorization")
		parts := strings.Split(auth, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Token malformado"})
			return
		}

		claims, err := utils.ValidateToken(parts[1])
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
			return
		}

		c.Set("user", claims)
		c.Next()
	}
}
