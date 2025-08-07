package utils

import (
	"errors"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
	"time"
	"usuarios/models"

	"github.com/golang-jwt/jwt/v4"
	"github.com/joho/godotenv"
)

func LoadEnv() {
	// Esto NO rompe en producción, solo loguea.
	err := godotenv.Load()
	if err != nil {
		log.Println("No se encontró .env, usando variables de entorno del sistema")
	}
}


var jwtKey = []byte(os.Getenv("JWT_SECRET"))

// Claims personalizados para el token "normal"
type Claims struct {
	UserID uint   `json:"user_id"`
	Roles  string `json:"roles"`
	jwt.StandardClaims
}

// GenerateToken genera el token principal (20 min) y el refresh token (7 días)
// Se asigna el userID en el campo Id y los roles en el Subject del refresh token.
func GenerateToken(user *models.Usuarios) (string, string, error) {
	// Convertir roles a una cadena separada por comas
	var roleNames []string
	for _, role := range user.Roles {
		roleNames = append(roleNames, role.Name)
	}
	rolesAsString := strings.Join(roleNames, ",")

	// Token principal (expira en 20 min)
	expirationTime := time.Now().Add(20 * time.Minute)
	claims := &Claims{
		UserID: user.ID,
		Roles:  rolesAsString,
		StandardClaims: jwt.StandardClaims{
			ExpiresAt: expirationTime.Unix(),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString(jwtKey)
	if err != nil {
		return "", "", err
	}

	// Refresh Token (expira en 7 días)
	refreshExpiration := time.Now().Add(7 * 24 * time.Hour)
	// Asignamos el userID al campo Id y los roles en Subject
	refreshClaims := &jwt.StandardClaims{
		ExpiresAt: refreshExpiration.Unix(),
		Id:        strconv.FormatUint(uint64(user.ID), 10),
		Subject:   rolesAsString,
	}
	refreshToken := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims)
	refreshTokenString, err := refreshToken.SignedString(jwtKey)
	if err != nil {
		return "", "", err
	}

	return tokenString, refreshTokenString, nil
}


func RefreshToken(refreshTokenString string) (string, string, error) {
	claims := &jwt.StandardClaims{}

	// Validar el refresh token
	token, err := jwt.ParseWithClaims(refreshTokenString, claims, func(token *jwt.Token) (interface{}, error) {
		return jwtKey, nil
	})
	if err != nil || !token.Valid {
		return "", "", errors.New("refresh token inválido o expirado")
	}

	// Convertir el userID que se guardó en claims.Id
	userID, err := strconv.ParseUint(claims.Id, 10, 32)
	if err != nil {
		return "", "", errors.New("ID de usuario inválido en los claims")
	}

	// Generar un nuevo access token (expira en 20 minutos)
	expirationTime := time.Now().Add(20 * time.Minute)
	newClaims := &Claims{
		UserID: uint(userID),
		// Los roles se recuperan del campo Subject del refresh token
		Roles: claims.Subject,
		StandardClaims: jwt.StandardClaims{
			ExpiresAt: expirationTime.Unix(),
		},
	}

	newToken := jwt.NewWithClaims(jwt.SigningMethodHS256, newClaims)
	newTokenString, err := newToken.SignedString(jwtKey)
	if err != nil {
		return "", "", err
	}

	// Generar un nuevo refresh token (expira en 7 días)
	refreshExpiration := time.Now().Add(7 * 24 * time.Hour)
	refreshClaims := &jwt.StandardClaims{
		ExpiresAt: refreshExpiration.Unix(),
		Id:        strconv.FormatUint(userID, 10),
		Subject:   claims.Subject,
	}
	newRefreshToken := jwt.NewWithClaims(jwt.SigningMethodHS256, refreshClaims)
	newRefreshTokenString, err := newRefreshToken.SignedString(jwtKey)
	if err != nil {
		return "", "", err
	}

	return newTokenString, newRefreshTokenString, nil
}

// ValidateToken valida un token "normal" y retorna los claims personalizados.
func ValidateToken(tokenString string) (*Claims, error) {
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(tokenString, claims, func(token *jwt.Token) (interface{}, error) {
		return jwtKey, nil
	})
	if err != nil || !token.Valid {
		return nil, err
	}
	return claims, nil
}

func GenerateInitialAuthToken() (string, error) {
	expirationTime := time.Now().Add(24 * time.Hour)
	claims := &Claims{
		UserID: 1,
		Roles:  "AD",
		StandardClaims: jwt.StandardClaims{
			ExpiresAt: expirationTime.Unix(),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString(jwtKey)
	
	if err != nil {
		return "", err
	}
	
	fmt.Println("Generated token (first 20 chars):", tokenString[:20])
	return tokenString, err
}