# Backend Usuarios

Sistema de autenticación y gestión de usuarios desarrollado en Go con Gin Framework. Proporciona autenticación JWT, gestión de roles y middleware de seguridad para el ecosistema de análisis de emociones.

## 🚀 Características

- **Autenticación JWT** con tokens de acceso y refresh
- **Sistema de roles** (Padre/Admin) con permisos diferenciados
- **Rate limiting** personalizable por usuario
- **Middleware de seguridad** (CORS, Auth, Admin)
- **Base de datos MySQL** con ORM
- **Validación OTP** para autenticación de dos factores
- **Autenticación biométrica** (WebAuthn)
- **API RESTful** completa para gestión de usuarios

## 📋 Requisitos

- Go 1.21+
- MySQL 8.0+
- Redis (opcional, para rate limiting)

## 🛠️ Instalación

1. **Clonar el repositorio**
```bash
git clone <repo-url>
cd backend_usuarios
```

2. **Instalar dependencias**
```bash
go mod download
```

3. **Configurar variables de entorno**
Crear archivo `.env`:
```env
# Base de datos
DB_HOST=localhost
DB_PORT=3306
DB_NAME=usuarios_db
DB_USER=root
DB_PASSWORD=password

# JWT
JWT_SECRET=mi_chiquete

# Servidor
PORT=8080
GIN_MODE=release

# Rate Limiting (opcional)
REDIS_HOST=localhost
REDIS_PORT=6379
```

4. **Ejecutar migraciones**
```bash
go run main.go migrate
```

## 🚀 Ejecutar el servicio

```bash
# Desarrollo
go run main.go

# Compilar y ejecutar
go build -o backend_usuarios
./backend_usuarios
```

El servicio estará disponible en: `http://localhost:8080`

## 📡 API Endpoints

### Autenticación
- `POST /api/login` - Iniciar sesión
- `POST /api/logout` - Cerrar sesión
- `POST /api/refresh-token` - Renovar token JWT

### Gestión de usuarios
- `GET /api/users` - Listar usuarios (admin)
- `GET /api/users/:id` - Obtener usuario por ID
- `POST /api/users` - Crear usuario (admin)
- `PUT /api/users/:id` - Actualizar usuario
- `DELETE /api/users/:id` - Eliminar usuario (admin)

### OTP y Verificación
- `POST /api/otp/validate` - Validar código OTP
- `POST /api/otp/resend` - Reenviar código OTP

### Autenticación Biométrica
- `POST /api/biometric/login/begin` - Iniciar login biométrico
- `POST /api/biometric/login/finish` - Completar login biométrico

### Endpoints sin autenticación
- `GET /health` - Estado del servicio
- `GET /un/*` - Recursos públicos

## 🔐 Sistema de autenticación

### Roles disponibles

| Rol | Código | Descripción |
|-----|--------|-------------|
| **Admin** | AD | Acceso completo al sistema |
| **Padre** | PD | Acceso limitado a funciones de usuario |

### Estructura del token JWT

```json
{
  "user_id": 123,
  "roles": "PD,AD",
  "exp": 1234567890
}
```

### Headers requeridos

```bash
Authorization: Bearer <jwt_token>
```

## 🛡️ Middleware de seguridad

### AuthMiddleware
- Valida tokens JWT en todas las rutas protegidas
- Excluye rutas públicas (`/un/*`, `/api/login`, etc.)
- Inyecta información del usuario en el contexto

### AdminMiddleware
- Requiere rol de administrador (AD)
- Protege endpoints administrativos

### RateLimiter
- Límite configurable por IP y usuario
- 50 peticiones por día por defecto
- Headers informativos de límite

## 📊 Estructura de la base de datos

### Tabla: usuarios
```sql
CREATE TABLE usuarios (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  nombre VARCHAR(255),
  apellido VARCHAR(255),
  telefono VARCHAR(20),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Tabla: roles
```sql
CREATE TABLE roles (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) UNIQUE NOT NULL,
  description TEXT
);
```

### Tabla: usuario_roles
```sql
CREATE TABLE usuario_roles (
  usuario_id INT,
  role_id INT,
  PRIMARY KEY (usuario_id, role_id),
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  FOREIGN KEY (role_id) REFERENCES roles(id)
);
```

## 🐳 Docker

### Construir imagen
```bash
docker build -t backend-usuarios .
```

### Ejecutar contenedor
```bash
docker run -p 8080:8080 --env-file .env backend-usuarios
```

### Con docker-compose
```bash
# Desde directorio raíz del proyecto
docker-compose up backend-usuarios
```

## 🔧 Desarrollo

### Estructura del proyecto
```
backend_usuarios/
├── controllers/     # Controladores de endpoints
├── middlewares/     # Middleware de autenticación y seguridad
├── models/         # Modelos de datos
├── routes/         # Definición de rutas
├── service/        # Lógica de negocio
├── utils/          # Utilidades (JWT, validaciones)
├── config/         # Configuración de la aplicación
├── public/         # Archivos estáticos
├── main.go         # Punto de entrada
├── go.mod          # Dependencias
└── .env            # Variables de entorno
```

### Dependencias principales
```go
require (
    github.com/gin-gonic/gin
    github.com/golang-jwt/jwt/v4
    github.com/joho/godotenv
    gorm.io/gorm
    gorm.io/driver/mysql
    golang.org/x/crypto/bcrypt
)
```

### Tests
```bash
go test ./...
```

## 🔄 Integración con otros servicios

### Emotion ML Service
Este backend genera tokens JWT que son validados por el servicio de análisis de emociones:

```bash
# Ejemplo de integración
curl -H "Authorization: Bearer <jwt_token>" \
     -X POST http://localhost:5000/api/v1/analyze-emotion \
     -d '{"image_base64": "..."}'
```

### Configuración compartida
- **JWT_SECRET**: `mi_chiquete` (mismo en ambos servicios)
- **Redis**: Compartido para rate limiting
- **Roles**: PD y AD reconocidos por ambos servicios

## 📈 Monitoreo

### Health check
```bash
curl http://localhost:8080/health
```

### Métricas disponibles
- Tiempo de respuesta
- Rate limit status (headers `X-RateLimit-*`)
- Estado de conexión a base de datos

## 🚨 Seguridad

### Buenas prácticas implementadas
- Contraseñas hasheadas con bcrypt
- Tokens JWT con expiración
- Rate limiting por IP y usuario
- Validación de entrada en todos los endpoints
- CORS configurado
- Headers de seguridad


