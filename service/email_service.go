package services

import (
	"bytes"
	"fmt"
	"html/template"
	"net/smtp"
	"os"
	"time"
	"usuarios/models"
)

type EmailService struct{}

func NewEmailService() *EmailService {
	return &EmailService{}
}

func (e *EmailService) SendSessionCreatedEmail(session *models.TherapySession, paciente *models.Patient, cuidador *models.Usuarios) error {
	subject := "Nueva Sesión Terapéutica Programada"
	templateData := map[string]interface{}{
		"PacienteNombre":   paciente.NombresApellidos,
		"CuidadorNombre":   cuidador.Nombres_Apellidos,
		"TerapeutaNombre":  session.Terapeuta.Nombres_Apellidos,
		"TerapeutaEmail":   session.Terapeuta.Correo,
		"TerapeutaTelefono": session.Terapeuta.Telefono,
		"FechaSesion":      session.FechaSesion.Format("02/01/2006"),
		"HoraInicio":       session.HoraInicio,
		"HoraFin":          session.HoraFin,
		"Ubicacion":        session.Ubicacion,
		"Direccion":        session.Direccion,
		"Descripcion":      session.Descripcion,
		"TipoSesion":       session.TipoSesion,
		"Modalidad":        session.Modalidad,
		"Objetivos":        []string(session.Objetivos),
		"Materiales":       []string(session.Materiales),
		"Year":             time.Now().Year(),
	}

	htmlBody, err := e.parseTemplate("session_created", templateData)
	if err != nil {
		return err
	}

	return e.sendEmail(cuidador.Correo, subject, htmlBody)
}

func (e *EmailService) SendSessionRescheduledEmail(session *models.TherapySession, paciente *models.Patient, cuidador *models.Usuarios, motivo string) error {
	subject := "Sesión Terapéutica Reprogramada"
	templateData := map[string]interface{}{
		"PacienteNombre":   paciente.NombresApellidos,
		"CuidadorNombre":   cuidador.Nombres_Apellidos,
		"TerapeutaNombre":  session.Terapeuta.Nombres_Apellidos,
		"TerapeutaEmail":   session.Terapeuta.Correo,
		"TerapeutaTelefono": session.Terapeuta.Telefono,
		"FechaSesion":      session.FechaSesion.Format("02/01/2006"),
		"HoraInicio":       session.HoraInicio,
		"HoraFin":          session.HoraFin,
		"Ubicacion":        session.Ubicacion,
		"Direccion":        session.Direccion,
		"Motivo":           motivo,
		"Year":             time.Now().Year(),
	}

	htmlBody, err := e.parseTemplate("session_rescheduled", templateData)
	if err != nil {
		return err
	}

	return e.sendEmail(cuidador.Correo, subject, htmlBody)
}

func (e *EmailService) SendSessionCancelledEmail(session *models.TherapySession, paciente *models.Patient, cuidador *models.Usuarios, motivo string) error {
	subject := "Sesión Terapéutica Cancelada"
	templateData := map[string]interface{}{
		"PacienteNombre":   paciente.NombresApellidos,
		"CuidadorNombre":   cuidador.Nombres_Apellidos,
		"TerapeutaNombre":  session.Terapeuta.Nombres_Apellidos,
		"FechaSesion":      session.FechaSesion.Format("02/01/2006"),
		"HoraInicio":       session.HoraInicio,
		"HoraFin":          session.HoraFin,
		"Motivo":           motivo,
		"Year":             time.Now().Year(),
	}

	htmlBody, err := e.parseTemplate("session_cancelled", templateData)
	if err != nil {
		return err
	}

	return e.sendEmail(cuidador.Correo, subject, htmlBody)
}

func (e *EmailService) SendSessionReminderEmail(session *models.TherapySession, paciente *models.Patient, cuidador *models.Usuarios, reminderType string) error {
	var subject string
	var templateName string

	if reminderType == "day_before" {
		subject = "Recordatorio: Sesión Terapéutica Mañana"
		templateName = "session_reminder_day"
	} else {
		subject = "Recordatorio: Sesión Terapéutica en 20 minutos"
		templateName = "session_reminder_20min"
	}

	templateData := map[string]interface{}{
		"PacienteNombre":   paciente.NombresApellidos,
		"CuidadorNombre":   cuidador.Nombres_Apellidos,
		"TerapeutaNombre":  session.Terapeuta.Nombres_Apellidos,
		"TerapeutaTelefono": session.Terapeuta.Telefono,
		"FechaSesion":      session.FechaSesion.Format("02/01/2006"),
		"HoraInicio":       session.HoraInicio,
		"HoraFin":          session.HoraFin,
		"Ubicacion":        session.Ubicacion,
		"Direccion":        session.Direccion,
		"Year":             time.Now().Year(),
	}

	htmlBody, err := e.parseTemplate(templateName, templateData)
	if err != nil {
		return err
	}

	return e.sendEmail(cuidador.Correo, subject, htmlBody)
}

func (e *EmailService) parseTemplate(templateName string, data map[string]interface{}) (string, error) {
	templates := map[string]string{
		"session_created": `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nueva Sesión Programada</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px 20px; text-align: center; }
        .content { padding: 30px; }
        .session-card { background: #f8f9ff; border-left: 4px solid #667eea; padding: 20px; margin: 20px 0; border-radius: 5px; }
        .info-row { display: flex; margin: 10px 0; }
        .info-label { font-weight: bold; width: 120px; color: #555; }
        .info-value { color: #333; }
        .objectives { background: #e8f5e8; padding: 15px; border-radius: 5px; margin: 15px 0; }
        .materials { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 15px 0; }
        .footer { background: #f1f1f1; padding: 20px; text-align: center; color: #666; font-size: 12px; }
        .btn { background: #667eea; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; }
        ul { margin: 0; padding-left: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🎯 Nueva Sesión Programada</h1>
            <p>Sistema de Gestión Terapéutica USIL</p>
        </div>
        <div class="content">
            <p>Estimado/a <strong>{{.CuidadorNombre}}</strong>,</p>
            <p>Se ha programado una nueva sesión terapéutica para <strong>{{.PacienteNombre}}</strong>.</p>

            <div class="session-card">
                <h3>📅 Detalles de la Sesión</h3>
                <div class="info-row">
                    <span class="info-label">Fecha:</span>
                    <span class="info-value">{{.FechaSesion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Horario:</span>
                    <span class="info-value">{{.HoraInicio}} - {{.HoraFin}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Ubicación:</span>
                    <span class="info-value">{{.Ubicacion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Dirección:</span>
                    <span class="info-value">{{.Direccion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Tipo:</span>
                    <span class="info-value">{{.TipoSesion}} - {{.Modalidad}}</span>
                </div>
            </div>

            <div class="session-card">
                <h3>👨‍⚕️ Terapeuta Asignado</h3>
                <div class="info-row">
                    <span class="info-label">Nombre:</span>
                    <span class="info-value">{{.TerapeutaNombre}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Email:</span>
                    <span class="info-value">{{.TerapeutaEmail}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Teléfono:</span>
                    <span class="info-value">{{.TerapeutaTelefono}}</span>
                </div>
            </div>

            <div class="objectives">
                <h4>🎯 Objetivos de la Sesión:</h4>
                <ul>
                    {{range .Objetivos}}<li>{{.}}</li>{{end}}
                </ul>
            </div>

            <div class="materials">
                <h4>🎒 Materiales Necesarios:</h4>
                <ul>
                    {{range .Materiales}}<li>{{.}}</li>{{end}}
                </ul>
            </div>

            <p><strong>Descripción:</strong> {{.Descripcion}}</p>

            <p>Recibirá recordatorios automáticos 1 día antes y 20 minutos antes de la sesión.</p>
        </div>
        <div class="footer">
            <p>Este es un mensaje automático generado por el Sistema de Gestión Terapéutica USIL</p>
            <p>© {{.Year}} Universidad San Ignacio de Loyola</p>
        </div>
    </div>
</body>
</html>`,

		"session_rescheduled": `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sesión Reprogramada</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #f39c12 0%, #f1c40f 100%); color: white; padding: 30px 20px; text-align: center; }
        .content { padding: 30px; }
        .session-card { background: #fff8dc; border-left: 4px solid #f39c12; padding: 20px; margin: 20px 0; border-radius: 5px; }
        .info-row { display: flex; margin: 10px 0; }
        .info-label { font-weight: bold; width: 120px; color: #555; }
        .info-value { color: #333; }
        .alert { background: #ffeaa7; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #fdcb6e; }
        .footer { background: #f1f1f1; padding: 20px; text-align: center; color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📅 Sesión Reprogramada</h1>
            <p>Sistema de Gestión Terapéutica USIL</p>
        </div>
        <div class="content">
            <p>Estimado/a <strong>{{.CuidadorNombre}}</strong>,</p>
            <p>La sesión terapéutica de <strong>{{.PacienteNombre}}</strong> ha sido reprogramada.</p>

            <div class="alert">
                <h4>📋 Motivo de la Reprogramación:</h4>
                <p>{{.Motivo}}</p>
            </div>

            <div class="session-card">
                <h3>📅 Nueva Fecha y Hora</h3>
                <div class="info-row">
                    <span class="info-label">Fecha:</span>
                    <span class="info-value">{{.FechaSesion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Horario:</span>
                    <span class="info-value">{{.HoraInicio}} - {{.HoraFin}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Ubicación:</span>
                    <span class="info-value">{{.Ubicacion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Dirección:</span>
                    <span class="info-value">{{.Direccion}}</span>
                </div>
            </div>

            <div class="session-card">
                <h3>👨‍⚕️ Terapeuta</h3>
                <div class="info-row">
                    <span class="info-label">Nombre:</span>
                    <span class="info-value">{{.TerapeutaNombre}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Email:</span>
                    <span class="info-value">{{.TerapeutaEmail}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Teléfono:</span>
                    <span class="info-value">{{.TerapeutaTelefono}}</span>
                </div>
            </div>

            <p>Lamentamos cualquier inconveniente que esto pueda causar. Recibirá nuevos recordatorios según la nueva programación.</p>
        </div>
        <div class="footer">
            <p>Este es un mensaje automático generado por el Sistema de Gestión Terapéutica USIL</p>
            <p>© {{.Year}} Universidad San Ignacio de Loyola</p>
        </div>
    </div>
</body>
</html>`,

		"session_cancelled": `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sesión Cancelada</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%); color: white; padding: 30px 20px; text-align: center; }
        .content { padding: 30px; }
        .session-card { background: #ffeaea; border-left: 4px solid #e74c3c; padding: 20px; margin: 20px 0; border-radius: 5px; }
        .info-row { display: flex; margin: 10px 0; }
        .info-label { font-weight: bold; width: 120px; color: #555; }
        .info-value { color: #333; }
        .alert { background: #ffcccb; padding: 15px; border-radius: 5px; margin: 15px 0; border-left: 4px solid #e74c3c; }
        .footer { background: #f1f1f1; padding: 20px; text-align: center; color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>❌ Sesión Cancelada</h1>
            <p>Sistema de Gestión Terapéutica USIL</p>
        </div>
        <div class="content">
            <p>Estimado/a <strong>{{.CuidadorNombre}}</strong>,</p>
            <p>Lamentamos informarle que la sesión terapéutica de <strong>{{.PacienteNombre}}</strong> ha sido cancelada.</p>

            <div class="alert">
                <h4>📋 Motivo de la Cancelación:</h4>
                <p>{{.Motivo}}</p>
            </div>

            <div class="session-card">
                <h3>📅 Sesión Cancelada</h3>
                <div class="info-row">
                    <span class="info-label">Fecha:</span>
                    <span class="info-value">{{.FechaSesion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Horario:</span>
                    <span class="info-value">{{.HoraInicio}} - {{.HoraFin}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Terapeuta:</span>
                    <span class="info-value">{{.TerapeutaNombre}}</span>
                </div>
            </div>

            <p>Para reprogramar o consultar disponibilidad para una nueva sesión, puede contactar directamente con el centro terapéutico.</p>
        </div>
        <div class="footer">
            <p>Este es un mensaje automático generado por el Sistema de Gestión Terapéutica USIL</p>
            <p>© {{.Year}} Universidad San Ignacio de Loyola</p>
        </div>
    </div>
</body>
</html>`,

		"session_reminder_day": `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Recordatorio de Sesión</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #27ae60 0%, #2ecc71 100%); color: white; padding: 30px 20px; text-align: center; }
        .content { padding: 30px; }
        .session-card { background: #f0fff0; border-left: 4px solid #27ae60; padding: 20px; margin: 20px 0; border-radius: 5px; }
        .info-row { display: flex; margin: 10px 0; }
        .info-label { font-weight: bold; width: 120px; color: #555; }
        .info-value { color: #333; }
        .footer { background: #f1f1f1; padding: 20px; text-align: center; color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔔 Recordatorio de Sesión</h1>
            <p>Mañana tienes una sesión programada</p>
        </div>
        <div class="content">
            <p>Estimado/a <strong>{{.CuidadorNombre}}</strong>,</p>
            <p>Le recordamos que <strong>{{.PacienteNombre}}</strong> tiene una sesión terapéutica programada para mañana.</p>

            <div class="session-card">
                <h3>📅 Detalles de la Sesión</h3>
                <div class="info-row">
                    <span class="info-label">Fecha:</span>
                    <span class="info-value">{{.FechaSesion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Horario:</span>
                    <span class="info-value">{{.HoraInicio}} - {{.HoraFin}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Ubicación:</span>
                    <span class="info-value">{{.Ubicacion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Dirección:</span>
                    <span class="info-value">{{.Direccion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Terapeuta:</span>
                    <span class="info-value">{{.TerapeutaNombre}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Teléfono:</span>
                    <span class="info-value">{{.TerapeutaTelefono}}</span>
                </div>
            </div>

            <p>Por favor, asegúrese de llegar 10 minutos antes de la hora programada.</p>
            <p>Recibirá otro recordatorio 20 minutos antes del inicio de la sesión.</p>
        </div>
        <div class="footer">
            <p>Este es un mensaje automático generado por el Sistema de Gestión Terapéutica USIL</p>
            <p>© {{.Year}} Universidad San Ignacio de Loyola</p>
        </div>
    </div>
</body>
</html>`,

		"session_reminder_20min": `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>¡Sesión en 20 minutos!</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%); color: white; padding: 30px 20px; text-align: center; }
        .content { padding: 30px; }
        .urgent-card { background: #fff5f5; border-left: 4px solid #ff6b6b; padding: 20px; margin: 20px 0; border-radius: 5px; }
        .info-row { display: flex; margin: 10px 0; }
        .info-label { font-weight: bold; width: 120px; color: #555; }
        .info-value { color: #333; }
        .footer { background: #f1f1f1; padding: 20px; text-align: center; color: #666; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>⏰ ¡Sesión en 20 minutos!</h1>
            <p>Es hora de prepararse</p>
        </div>
        <div class="content">
            <p>Estimado/a <strong>{{.CuidadorNombre}}</strong>,</p>
            <p>La sesión terapéutica de <strong>{{.PacienteNombre}}</strong> comenzará en <strong>20 minutos</strong>.</p>

            <div class="urgent-card">
                <h3>⏰ Información Urgente</h3>
                <div class="info-row">
                    <span class="info-label">Hora de inicio:</span>
                    <span class="info-value">{{.HoraInicio}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Ubicación:</span>
                    <span class="info-value">{{.Ubicacion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Dirección:</span>
                    <span class="info-value">{{.Direccion}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Terapeuta:</span>
                    <span class="info-value">{{.TerapeutaNombre}}</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Contacto:</span>
                    <span class="info-value">{{.TerapeutaTelefono}}</span>
                </div>
            </div>

            <p><strong>¡Es momento de dirigirse al lugar de la sesión!</strong></p>
            <p>Si tiene algún inconveniente, contacte directamente al terapeuta.</p>
        </div>
        <div class="footer">
            <p>Este es un mensaje automático generado por el Sistema de Gestión Terapéutica USIL</p>
            <p>© {{.Year}} Universidad San Ignacio de Loyola</p>
        </div>
    </div>
</body>
</html>`,
	}

	tmpl, err := template.New(templateName).Parse(templates[templateName])
	if err != nil {
		return "", err
	}

	var buf bytes.Buffer
	err = tmpl.Execute(&buf, data)
	if err != nil {
		return "", err
	}

	return buf.String(), nil
}

func (e *EmailService) sendEmail(to, subject, htmlBody string) error {
	from := os.Getenv("SMTP_EMAIL")
	password := os.Getenv("SMTP_PASSWORD")
	smtpHost := os.Getenv("SMTP_HOST")
	smtpPort := os.Getenv("SMTP_PORT")

	if from == "" || password == "" || smtpHost == "" || smtpPort == "" {
		return fmt.Errorf("configuración SMTP incompleta")
	}

	message := fmt.Sprintf("To: %s\r\n"+
		"Subject: %s\r\n"+
		"MIME-Version: 1.0\r\n"+
		"Content-Type: text/html; charset=UTF-8\r\n"+
		"\r\n"+
		"%s\r\n", to, subject, htmlBody)

	auth := smtp.PlainAuth("", from, password, smtpHost)
	return smtp.SendMail(smtpHost+":"+smtpPort, auth, from, []string{to}, []byte(message))
}