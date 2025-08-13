package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
	"syscall"

	"golang.org/x/crypto/bcrypt"
	"golang.org/x/term"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"usuarios/models"
	"usuarios/utils"
)

func main() {
	fmt.Println("=== Script para Cambio de Contraseña ===")
	
	// Cargar variables de entorno
	utils.LoadEnv()
	
	// Conectar a la base de datos
	db, err := connectToDatabase()
	if err != nil {
		log.Fatalf("Error conectando a la base de datos: %v", err)
	}

	// Mostrar usuarios disponibles
	users, err := listUsers(db)
	if err != nil {
		log.Fatalf("Error listando usuarios: %v", err)
	}

	fmt.Println("\n=== Usuarios Disponibles ===")
	for _, user := range users {
		status := "ACTIVO"
		if user.Activo {
			status = "INACTIVO"
		}
		fmt.Printf("ID: %d | Usuario: %s | Nombre: %s | Email: %s | Estado: %s\n", 
			user.ID, user.Usuario, user.Nombres_Apellidos, user.Correo, status)
	}

	// Solicitar ID del usuario
	fmt.Print("\nIngrese el ID del usuario al cual cambiar la contraseña: ")
	reader := bufio.NewReader(os.Stdin)
	userIDStr, _ := reader.ReadString('\n')
	userIDStr = strings.TrimSpace(userIDStr)
	
	userID, err := strconv.Atoi(userIDStr)
	if err != nil {
		log.Fatalf("ID inválido: %v", err)
	}

	// Verificar que el usuario existe
	var user models.Usuarios
	if err := db.First(&user, userID).Error; err != nil {
		log.Fatalf("Usuario no encontrado: %v", err)
	}

	fmt.Printf("\nUsuario seleccionado: %s (%s)\n", user.Usuario, user.Nombres_Apellidos)

	// Solicitar nueva contraseña (oculta)
	fmt.Print("Ingrese la nueva contraseña (mínimo 12 caracteres): ")
	passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		log.Fatalf("Error leyendo contraseña: %v", err)
	}
	newPassword := string(passwordBytes)
	fmt.Println() // Nueva línea después del input oculto

	// Validar contraseña
	if len(newPassword) < 12 {
		log.Fatalf("La contraseña debe tener al menos 12 caracteres")
	}

	// Confirmar contraseña
	fmt.Print("Confirme la nueva contraseña: ")
	confirmPasswordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		log.Fatalf("Error leyendo confirmación: %v", err)
	}
	confirmPassword := string(confirmPasswordBytes)
	fmt.Println() // Nueva línea después del input oculto

	if newPassword != confirmPassword {
		log.Fatalf("Las contraseñas no coinciden")
	}

	// Hashear la nueva contraseña
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		log.Fatalf("Error hasheando la contraseña: %v", err)
	}

	// Confirmar el cambio
	fmt.Printf("\n¿Está seguro de cambiar la contraseña del usuario '%s'? (y/N): ", user.Usuario)
	confirmation, _ := reader.ReadString('\n')
	confirmation = strings.TrimSpace(strings.ToLower(confirmation))

	if confirmation != "y" && confirmation != "yes" && confirmation != "sí" && confirmation != "si" {
		fmt.Println("Operación cancelada.")
		return
	}

	// Actualizar la contraseña en la base de datos
	if err := db.Model(&user).Update("contrasena", string(hashedPassword)).Error; err != nil {
		log.Fatalf("Error actualizando la contraseña: %v", err)
	}

	// Opcional: Limpiar el historial de contraseñas si quieres que pueda usar esta misma contraseña después
	// db.Where("user_id = ?", userID).Delete(&models.PasswordHistory{})

	fmt.Printf("\n✅ Contraseña actualizada exitosamente para el usuario: %s\n", user.Usuario)
	fmt.Printf("Hash generado: %s\n", string(hashedPassword)[:50] + "...")
}

func connectToDatabase() (*gorm.DB, error) {
	username := os.Getenv("DB_USERNAME")
	password := os.Getenv("DB_PASSWORD")
	host := os.Getenv("DB_HOST")
	port := os.Getenv("DB_PORT")
	database := os.Getenv("DB_NAME")

	if username == "" || password == "" || host == "" || database == "" {
		return nil, fmt.Errorf("faltan variables de entorno de la base de datos")
	}

	if port == "" {
		port = "3306"
	}

	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		username, password, host, port, database)

	return gorm.Open(mysql.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
}

func listUsers(db *gorm.DB) ([]models.Usuarios, error) {
	var users []models.Usuarios
	if err := db.Find(&users).Error; err != nil {
		return nil, err
	}
	return users, nil
}