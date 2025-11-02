# 🔒 Backend Usuarios - API REST con Go + Gin

Sistema completo de autenticación, gestión de usuarios y sesiones terapéuticas. APIs REST seguras con JWT, autenticación biométrica, gestión de roles y análisis de estadísticas.

## 🚀 Instalación

```bash
git clone <repository-url> && cd backend_usuarios/
go mod download && go mod tidy
cp .env.example .env  # Configurar variables de entorno
go run main.go
```

## 🔧 Configuración (.env)

```bash
# Base de datos
DB_HOST=localhost
DB_PORT=3306
DB_NAME=usuarios_db
DB_USER=api_user
DB_PASSWORD=secure_password

# JWT & Seguridad
JWT_SECRET=mi_secret_super_seguro_2024
JWT_REFRESH_SECRET=mi_refresh_secret_2024

# Servidor
PORT=8080
GIN_MODE=release

# Rate Limiting
RATE_LIMIT_REQUESTS_PER_DAY=50
RATE_LIMIT_MODULE_LIMIT=20

# ML Service
ML_SERVICE_URL=http://localhost:8000
```

## 📡 API Endpoints Principales

### 🔓 Públicos
```http
POST /api/login                    # Autenticación
POST /api/refresh-token            # Renovar token
POST /otp/validate                 # Validar OTP
POST /password/send-otp            # Recuperar contraseña
```

### 🔒 Protegidos
```http
# Usuarios
GET    /api/users                  # Listar usuarios
POST   /api/register               # Registrar usuario
GET    /api/profile                # Perfil actual

# Pacientes
GET    /api/patients               # Listar pacientes
POST   /api/patients               # Crear paciente
GET    /api/patients/{id}/stats    # Estadísticas

# Sesiones
GET    /api/sessions/paginated     # Sesiones paginadas
POST   /api/sessions               # Crear sesión
PUT    /api/sessions/{id}          # Actualizar sesión
DELETE /api/sessions/{id}          # Eliminar sesión

# Calificaciones
POST   /api/therapist-ratings      # Calificar terapeuta
GET    /api/therapist-ratings/{id} # Ver calificaciones
```

## 👥 Roles y Permisos

### 🛡️ Administrador (AD)
- Gestión completa de usuarios y sistema
- Dashboard con estadísticas globales
- Calificación de terapeutas en nombre de cuidadores

### 👨‍⚕️ Terapeuta (TR)
- Gestión de pacientes asignados
- Creación de sesiones terapéuticas
- Dashboard con estadísticas propias

### 👨‍👩‍👧‍👦 Padres/Cuidadores (PD)
- Vista de pacientes asignados (hijos)
- Calificación de terapeutas tras sesiones
- Seguimiento de progreso

## 🛠️ Tecnologías

- **Go 1.21+** con **Gin Framework**
- **GORM** + **MySQL 8.0+**
- **JWT** + **WebAuthn** (autenticación biométrica)
- **Redis** (cache y rate limiting)
- **bcrypt** (encriptación)

## 🏗️ Arquitectura

```
┌─── Controllers ───┐
│  HTTP Handlers    │
├─── Services ──────┤
│  Business Logic   │
├─── Models ────────┤
│  GORM/Database    │
└───────────────────┘
```

## 🆕 Funcionalidades Destacadas

### Sistema de Calificaciones
- Calificación 1-5 estrellas por sesión completada
- Comentarios opcionales y verificación anti-duplicados
- Reasignación automática con calificaciones bajas (≤3)

### Reasignación Inteligente
- Detección automática de terapeutas con calificaciones bajas
- Búsqueda de terapeuta disponible (< 20 pacientes, activo)
- Actualización automática de sesiones futuras
- Creación de sesión de seguimiento si no hay futuras

### Dashboard Dinámico
- Estadísticas diferenciadas por rol
- Métricas en tiempo real
- Navegación completa admin → cuidadores → pacientes → sesiones

---
**Desarrollado por Jhafet Cánepa - Octubre 2025**