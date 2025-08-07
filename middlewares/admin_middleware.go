package middlewares

import (
    "net/http"
    "strings"
    "usuarios/utils"

    "github.com/gin-gonic/gin"
)

// AdminMiddleware revisa si el token contiene un rol "Administrador".
func AdminMiddleware() gin.HandlerFunc {
    return func(c *gin.Context) {
        // Obtener el header Authorization
        authHeader := c.GetHeader("Authorization")
        if authHeader == "" {
            c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "No se proporcionó el token"})
            return
        }

        // Se espera "Bearer <token>"
        parts := strings.Split(authHeader, " ")
        if len(parts) != 2 || parts[0] != "Bearer" {
            c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Formato de token inválido"})
            return
        }
        tokenString := parts[1]

        // Validar el token con la función de utils
        claims, err := utils.ValidateToken(tokenString)
        if err != nil {
            c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Token inválido o expirado"})
            return
        }

        // Dividir la cadena de roles por comas: "Administrador,Docentes" => ["Administrador", "Docentes"]
        rolesSlice := strings.Split(claims.Roles, ",")

        // Verificar si "Administrador" está presente en rolesSlice
        if !contains(rolesSlice, "administrador") {
            c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "Acceso denegado: se requiere rol Administrador"})
            return
        }

        // Si todo va bien, continúa
        c.Next()
    }
}

// contains revisa si un slice de strings contiene un valor específico
func contains(slice []string, val string) bool {
    for _, item := range slice {
        if strings.TrimSpace(item) == val {
            return true
        }
    }
    return false
}
