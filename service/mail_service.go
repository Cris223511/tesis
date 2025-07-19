package services

import (
	"crypto/rand"
	"crypto/tls"
	"fmt"
	"log"
	"math/big"
	"net/smtp"
	"strings"
)

// generateOTP genera un código OTP alfanumérico de 8 caracteres.
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

func sendEmailNotification(toEmail string) error {

	smtpHost := "smtp.gmail.com"
	smtpPort := "587"
	senderEmail := "jafcnepamace24@gmail.com"

	password := "gumwkmvmkqbczusm"


	otp := generateOTP(6)

	subject := "Datos Personales, Código OTP y Bloqueo de Cuenta"
	body := fmt.Sprintf(
		"Se han enviado tus datos personales.\n\n"+
			"Se ha generado un código OTP para iniciar sesión: %s\n"+
			"Utiliza este código para acceder a tu cuenta.\n\n"+
			"Si se superan los intentos permitidos, tu cuenta quedará bloqueada durante 48 horas.",
		otp,
	)

	msg := []byte("From: " + senderEmail + "\r\n" +
		"To: " + toEmail + "\r\n" +
		"Subject: " + subject + "\r\n" +
		"\r\n" +
		body + "\r\n")

	addr := smtpHost + ":" + smtpPort
	auth := smtp.PlainAuth("", senderEmail, password, smtpHost)


	conn, err := tls.Dial("tcp", addr, &tls.Config{
		InsecureSkipVerify: true,
		ServerName:         smtpHost,
	})
	if err != nil {
		return fmt.Errorf("error al conectar vía TLS: %v", err)
	}
	client, err := smtp.NewClient(conn, smtpHost)
	if err != nil {
		return fmt.Errorf("error al crear cliente SMTP: %v", err)
	}
	defer client.Close()

	if err = client.Auth(auth); err != nil {
		return fmt.Errorf("error en autenticación SMTP: %v", err)
	}

	if err = client.Mail(senderEmail); err != nil {
		return fmt.Errorf("error al establecer remitente: %v", err)
	}
	if err = client.Rcpt(toEmail); err != nil {
		return fmt.Errorf("error al establecer destinatario: %v", err)
	}

	wc, err := client.Data()
	if err != nil {
		return fmt.Errorf("error al iniciar envío de datos: %v", err)
	}
	_, err = wc.Write(msg)
	if err != nil {
		return fmt.Errorf("error al escribir mensaje: %v", err)
	}
	err = wc.Close()
	if err != nil {
		return fmt.Errorf("error al cerrar conexión de datos: %v", err)
	}


	log.Printf("OTP generado y enviado a %s: %s", toEmail, otp)
	return nil
}

func main() {
	toEmail := "destinatario@gmail.com"
	if err := sendEmailNotification(toEmail); err != nil {
		log.Fatalf("Error al enviar correo: %v", err)
	}
	fmt.Println("Correo enviado correctamente")
}
