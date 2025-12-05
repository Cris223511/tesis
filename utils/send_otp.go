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
	subject := "🔐 Código de Verificación - Serious Game"
	body := fmt.Sprintf(`
	<!DOCTYPE html>
	<html lang="es">
	<head>
		<meta charset="UTF-8">
		<meta name="viewport" content="width=device-width, initial-scale=1.0">
		<title>Código de Verificación</title>
	</head>
	<body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f8fafc; line-height: 1.6;">
		<div style="max-width: 600px; margin: 0 auto; background-color: #ffffff;">
			<!-- Header -->
			<div style="background: linear-gradient(135deg, #1e40af 0%%, #3b82f6 100%%); padding: 40px 30px; text-align: center; border-radius: 12px 12px 0 0;">
				<div style="background-color: rgba(255,255,255,0.1); padding: 15px; border-radius: 50%%; display: inline-block; margin-bottom: 20px;">
					<div style="width: 50px; height: 50px; background-color: #ffffff; border-radius: 50%%; display: flex; align-items: center; justify-content: center; margin: 0 auto;">
						<span style="font-size: 24px;">🔐</span>
					</div>
				</div>
				<h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 700; letter-spacing: -0.5px;">SERIOUS GAME</h1>
				<p style="color: rgba(255,255,255,0.9); margin: 8px 0 0 0; font-size: 16px;">Plataforma de Terapias Emocionales</p>
			</div>

			<!-- Main Content -->
			<div style="padding: 50px 30px; background-color: #ffffff;">
				<div style="text-align: center; margin-bottom: 40px;">
					<h2 style="color: #1f2937; margin: 0 0 16px 0; font-size: 24px; font-weight: 600;">Código de Verificación</h2>
					<p style="color: #6b7280; font-size: 16px; margin: 0; line-height: 1.5;">
						Para acceder a tu cuenta de forma segura, ingresa el siguiente código de verificación:
					</p>
				</div>

				<!-- OTP Code Box -->
				<div style="background: linear-gradient(135deg, #f59e0b 0%%, #f97316 100%%); border-radius: 16px; padding: 30px; margin: 30px 0; text-align: center; box-shadow: 0 10px 25px rgba(245, 158, 11, 0.3);">
					<p style="color: #ffffff; margin: 0 0 10px 0; font-size: 14px; font-weight: 500; opacity: 0.9;">TU CÓDIGO DE VERIFICACIÓN</p>
					<div style="background-color: rgba(255,255,255,0.15); border-radius: 12px; padding: 20px; margin: 10px 0;">
						<span style="font-size: 36px; font-weight: 900; color: #ffffff; letter-spacing: 8px; text-shadow: 0 2px 4px rgba(0,0,0,0.2);">%s</span>
					</div>
					<p style="color: rgba(255,255,255,0.9); margin: 10px 0 0 0; font-size: 14px;">Válido por 1 minuto</p>
				</div>

				<!-- Security Notice -->
				<div style="background-color: #f0f9ff; border-left: 4px solid #0ea5e9; border-radius: 8px; padding: 20px; margin: 30px 0;">
					<div style="display: flex; align-items: flex-start;">
						<span style="font-size: 18px; margin-right: 12px;">🛡️</span>
						<div>
							<h3 style="color: #0c4a6e; margin: 0 0 8px 0; font-size: 16px; font-weight: 600;">Aviso de Seguridad</h3>
							<p style="color: #0e7490; margin: 0; font-size: 14px; line-height: 1.5;">
								• Este código es <strong>personal e intransferible</strong><br>
								• Nunca compartas tu código con terceros<br>
								• Si no solicitaste este código, ignora este correo
							</p>
						</div>
					</div>
				</div>

				<!-- Support Info -->
				<div style="text-align: center; margin-top: 40px;">
					<p style="color: #6b7280; font-size: 14px; margin: 0;">
						¿Necesitas ayuda? Contacta a nuestro equipo de soporte<br>
						<a href="mailto:soporte@seriousgame.com" style="color: #1e40af; text-decoration: none; font-weight: 500;">soporte@seriousgame.com</a>
					</p>
				</div>
			</div>

			<!-- Footer -->
			<div style="background-color: #f8fafc; padding: 30px; text-align: center; border-radius: 0 0 12px 12px; border-top: 1px solid #e5e7eb;">
				<div style="margin-bottom: 15px;">
					<p style="color: #9ca3af; font-size: 12px; margin: 0;">
						© %d Serious Game • Plataforma de Terapias Emocionales
					</p>
				</div>
				<div style="border-top: 1px solid #e5e7eb; padding-top: 15px; margin-top: 15px;">
					<p style="color: #9ca3af; font-size: 11px; margin: 0; line-height: 1.4;">
						Este correo fue enviado automáticamente desde una cuenta no monitoreada.<br>
						Por favor, no respondas a este mensaje directamente.
					</p>
				</div>
			</div>
		</div>

		<!-- Mobile Responsiveness -->
		<style>
			@media only screen and (max-width: 600px) {
				.container { width: 100%% !important; }
				.content { padding: 30px 20px !important; }
				.otp-code { font-size: 32px !important; letter-spacing: 6px !important; }
			}
		</style>
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

func SendSecurityNotificationEmail(toEmail, subject, body string) error {
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