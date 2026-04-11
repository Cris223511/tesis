package main

import (
    "fmt"
    "golang.org/x/crypto/bcrypt"
    "bufio"
    "os"
    "strings"
)

func main() {
    reader := bufio.NewReader(os.Stdin)

    fmt.Print("Ingresa la nueva contraseña: ")
    password, _ := reader.ReadString('\n')
    password = strings.TrimSpace(password)

    hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
    if err != nil {
        fmt.Println("Error generando hash:", err)
        return
    }

    fmt.Println("\n=== Hash BCrypt Generado ===")
    fmt.Println(string(hashedPassword))
    fmt.Println("\n=== SQL para actualizar ===")
    fmt.Printf(`UPDATE usuarios
SET
    password = '%s',
    fecha_expiracion_contrasena = DATE_ADD(NOW(), INTERVAL 90 DAY),
    activo = true,
    intentos_fallidos = 0
WHERE usuario = 'pepe.p';
`, string(hashedPassword))
}