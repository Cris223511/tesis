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
	
			<div style="background-color: #1e40af; padding: 40px 30px; text-align: center; border-radius: 12px 12px 0 0;">
				<div style="margin-bottom: 15px;">
					<span style="font-size: 40px;">🔐</span>
				</div>
				<h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 700;">SERIOUS GAME</h1>
				<p style="color: #93c5fd; margin: 8px 0 0 0; font-size: 16px;">Plataforma de Terapia Emocional</p>
			</div>

		
			<div style="padding: 50px 30px; background-color: #ffffff;">
				<div style="text-align: center; margin-bottom: 40px;">
					<h2 style="color: #1f2937; margin: 0 0 16px 0; font-size: 24px; font-weight: 600;">Código de Verificación</h2>
					<p style="color: #6b7280; font-size: 16px; margin: 0; line-height: 1.5;">
						Para acceder a tu cuenta de forma segura, ingresa el siguiente código de verificación:
					</p>
				</div>

			
				<div style="background-color: #fef3c7; border: 2px solid #f59e0b; border-radius: 16px; padding: 30px; margin: 30px 0; text-align: center;">
					<p style="color: #92400e; margin: 0 0 10px 0; font-size: 14px; font-weight: 500;">TU CÓDIGO DE VERIFICACIÓN</p>
					<div style="background-color: #ffffff; border: 2px solid #f59e0b; border-radius: 12px; padding: 20px; margin: 10px 0;">
						<span style="font-size: 36px; font-weight: 900; color: #1e40af; letter-spacing: 8px;">%s</span>
					</div>
					<p style="color: #92400e; margin: 10px 0 0 0; font-size: 14px;">Válido por 1 minuto</p>
				</div>

				
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

				
				<div style="text-align: center; margin-top: 40px;">
					<p style="color: #6b7280; font-size: 14px; margin: 0;">
						¿Necesitas ayuda? Contacta a nuestro equipo de soporte<br>
						<a href="mailto:soporte@seriousgame.com" style="color: #1e40af; text-decoration: none; font-weight: 500;">soporte@seriousgame.com</a>
					</p>
				</div>
			</div>

	
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

func SendOTPResendEmail(toEmail, otp string, resendCount int) error {
	subject := "🔄 Código Reenviado - Serious Game"
	body := fmt.Sprintf(`
	<!DOCTYPE html>
	<html lang="es">
	<head>
		<meta charset="UTF-8">
		<meta name="viewport" content="width=device-width, initial-scale=1.0">
		<title>Código Reenviado</title>
	</head>
	<body style="margin: 0; padding: 0; font-family: 'Segoe UI', Arial, sans-serif; background-color: #f8fafc; line-height: 1.6;">
		<div style="max-width: 600px; margin: 0 auto; background-color: #ffffff;">

			<!-- Header azul igual al OTP original -->
			<div style="background-color: #1e40af; padding: 40px 30px; text-align: center; border-radius: 12px 12px 0 0;">
				<div style="margin-bottom: 15px;">
					<span style="font-size: 40px;">🔄</span>
				</div>
				<h1 style="color: #ffffff; margin: 0; font-size: 28px; font-weight: 700;">SERIOUS GAME</h1>
				<p style="color: #93c5fd; margin: 8px 0 0 0; font-size: 16px;">Código de Verificación Reenviado</p>
			</div>

	
			<div style="padding: 50px 30px; background-color: #ffffff;">

				
				<div style="background-color: #fff7ed; border: 1px solid #fed7aa; border-radius: 12px; padding: 16px 20px; margin-bottom: 30px; text-align: center;">
					<div style="display: inline-flex; align-items: center; gap: 8px;">
						<span style="font-size: 20px;">📨</span>
						<span style="color: #c2410c; font-size: 14px; font-weight: 600;">Reenvío #%d solicitado</span>
					</div>
				</div>

				<div style="text-align: center; margin-bottom: 40px;">
					<h2 style="color: #1f2937; margin: 0 0 16px 0; font-size: 24px; font-weight: 600;">Tu Nuevo Código</h2>
					<p style="color: #6b7280; font-size: 16px; margin: 0; line-height: 1.5;">
						Hemos generado un nuevo código de verificación para ti.
						El código anterior ya no es válido.
					</p>
				</div>

		
				<div style="background-color: #fef3c7; border: 2px solid #f59e0b; border-radius: 16px; padding: 30px; margin: 30px 0; text-align: center;">
					<p style="color: #92400e; margin: 0 0 10px 0; font-size: 14px; font-weight: 500;">NUEVO CÓDIGO DE VERIFICACIÓN</p>
					<div style="background-color: #ffffff; border: 2px solid #f59e0b; border-radius: 12px; padding: 20px; margin: 10px 0;">
						<span style="font-size: 36px; font-weight: 900; color: #d97706; letter-spacing: 8px;">%s</span>
					</div>
					<p style="color: #92400e; margin: 10px 0 0 0; font-size: 14px;">⏱️ Válido por 1 minuto</p>
				</div>

				<div style="background-color: #fef9c3; border-left: 4px solid #eab308; border-radius: 8px; padding: 20px; margin: 30px 0;">
					<div style="display: flex; align-items: flex-start;">
						<span style="font-size: 18px; margin-right: 12px;">⚠️</span>
						<div>
							<h3 style="color: #854d0e; margin: 0 0 8px 0; font-size: 16px; font-weight: 600;">Importante</h3>
							<p style="color: #a16207; margin: 0; font-size: 14px; line-height: 1.5;">
								• El código anterior ha sido <strong>invalidado</strong><br>
								• Solo este nuevo código funcionará<br>
								• Si sigues sin recibir el código, verifica tu bandeja de spam
							</p>
						</div>
					</div>
				</div>

		
				<div style="background-color: #f0f9ff; border-left: 4px solid #0ea5e9; border-radius: 8px; padding: 20px; margin: 30px 0;">
					<div style="display: flex; align-items: flex-start;">
						<span style="font-size: 18px; margin-right: 12px;">🛡️</span>
						<div>
							<h3 style="color: #0c4a6e; margin: 0 0 8px 0; font-size: 16px; font-weight: 600;">Seguridad</h3>
							<p style="color: #0e7490; margin: 0; font-size: 14px; line-height: 1.5;">
								Si no solicitaste este código, alguien podría estar intentando acceder a tu cuenta.
								Te recomendamos cambiar tu contraseña.
							</p>
						</div>
					</div>
				</div>


				<div style="text-align: center; margin-top: 40px;">
					<p style="color: #6b7280; font-size: 14px; margin: 0;">
						¿Problemas para verificar? Contacta a soporte<br>
						<a href="mailto:soporte@seriousgame.com" style="color: #d97706; text-decoration: none; font-weight: 500;">soporte@seriousgame.com</a>
					</p>
				</div>
			</div>

	
			<div style="background-color: #f8fafc; padding: 30px; text-align: center; border-radius: 0 0 12px 12px; border-top: 1px solid #e5e7eb;">
				<div style="margin-bottom: 15px;">
					<p style="color: #9ca3af; font-size: 12px; margin: 0;">
						© %d Serious Game • Plataforma de Terapias Emocionales
					</p>
				</div>
				<div style="border-top: 1px solid #e5e7eb; padding-top: 15px; margin-top: 15px;">
					<p style="color: #9ca3af; font-size: 11px; margin: 0; line-height: 1.4;">
						Este correo fue enviado automáticamente.<br>
						Por favor, no respondas a este mensaje directamente.
					</p>
				</div>
			</div>
		</div>
	</body>
	</html>
	`, resendCount, otp, time.Now().Year())

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