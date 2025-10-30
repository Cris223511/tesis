package utils

import (
	"fmt"
	"log"
	"net/smtp"
	"os"
	"time"
)

func getSMTPConfig() (host, port, email, password string) {
	host = os.Getenv("SMTP_HOST")
	if host == "" {
		host = "smtp.gmail.com"
	}

	port = os.Getenv("SMTP_PORT")
	if port == "" {
		port = "587"
	}

	email = os.Getenv("SMTP_EMAIL")
	if email == "" {
		email = "jafcnepamace24@gmail.com"
	}

	password = os.Getenv("SMTP_PASSWORD")
	if password == "" {
		password = "gumwkmvmkqbczusm"
	}

	return host, port, email, password
}

func SendOTPEmail(toEmail, otp string) error {
	subject := "Código de Verificación - Portal Universitario"
	body := fmt.Sprintf(`
	<html>
	<head>
		<meta charset="UTF-8" />
	</head>
	<body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #eef2f7;">
		<table align="center" border="0" cellpadding="0" cellspacing="0" width="600" style="background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); margin-top: 30px;">
			<tr>
				<td style="background-color: #004165; color: #fff; padding: 20px; text-align: center;">
					<h1 style="margin: 0; font-size: 24px;">SERIOUS GAME </h1>
			
				</td>
			</tr>
			<tr>
				<td style="padding: 30px; color: #333;">
					<h2 style="margin-top: 0; font-size: 20px; color: #004165;">Código OTP</h2>
					<p style="font-size: 15px; line-height: 1.5;">
						Para completar tu inicio de sesión, utiliza el siguiente código:
					</p>
					<div style="text-align: center; margin: 20px 0;">
						<span style="font-size: 28px; font-weight: bold; color: #ff6600;">%s</span>
					</div>
					<p style="font-size: 14px; color: #666;">
						El código expirará en 5 minutos. Si no has solicitado este código, comunícate de inmediato con soporte.
					</p>
				</td>
			</tr>
			<tr>
				<td style="background-color: #f2f2f2; text-align: center; padding: 15px; font-size: 12px; color: #888;">
					© %d SERIOUS GAME - Todos los derechos reservados.
				</td>
			</tr>
		</table>
	</body>
	</html>
	`, otp, time.Now().Year())

	return sendEmailWithTLS(toEmail, subject, body)
}

func SendSecurityAlert(toEmail, name, reason, ip string) error {
	subject := "Alerta de Seguridad - SERIOUS GAME"
	body := fmt.Sprintf(`
	<html>
	<body>
		<h2>Alerta de Seguridad</h2>
		<p>Hola %s,</p>
		<p>Detectamos actividad inusual en tu cuenta:</p>
		<p><strong>Razón:</strong> %s<br>
		<strong>IP:</strong> %s<br>
		<strong>Fecha:</strong> %s</p>
		<p>Si no fuiste tú, cambia tu contraseña inmediatamente.</p>
	</body>
	</html>`, name, reason, ip, time.Now().Format("02/01/2006 15:04"))

	return sendEmailWithTLS(toEmail, subject, body)
}

func sendEmailWithTLS(toEmail, subject, body string) error {
	smtpHost, smtpPort, senderEmail, senderPassword := getSMTPConfig()

	log.Printf("[EMAIL][DEBUG] Iniciando envío a: %s", toEmail)
	log.Printf("[EMAIL][DEBUG] SMTP Config - Host: %s, Port: %s, User: %s", smtpHost, smtpPort, senderEmail)

	msg := []byte("From: " + senderEmail + "\r\n" +
		"To: " + toEmail + "\r\n" +
		"Subject: " + subject + "\r\n" +
		"MIME-Version: 1.0\r\n" +
		"Content-Type: text/html; charset=UTF-8\r\n\r\n" +
		body)

	addr := smtpHost + ":" + smtpPort
	auth := smtp.PlainAuth("", senderEmail, senderPassword, smtpHost)

	log.Printf("[EMAIL][DEBUG] Conectando a %s...", addr)

	err := smtp.SendMail(addr, auth, senderEmail, []string{toEmail}, msg)
	if err != nil {
		log.Printf("[EMAIL][ERROR] Error detallado: %v", err)
		return fmt.Errorf("error enviando email: %v", err)
	}

	log.Printf("[EMAIL][SUCCESS] Email enviado correctamente a %s", toEmail)
	return nil
}