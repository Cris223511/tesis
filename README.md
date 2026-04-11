# App Android - Gestión Terapéutica

Aplicación móvil para gestión de terapias con análisis de emociones, desarrollada en Kotlin con Jetpack Compose.

## Stack Tecnológico

- **Kotlin** con Android SDK 34 (min API 30)
- **Jetpack Compose** + Material Design 3
- **MVVM Architecture** con Repository Pattern
- **Retrofit 2.9.0** + OkHttp para APIs
- **Glide 4.16.0** para imágenes
- **Security Crypto** para encriptación

## Funcionalidades Principales

### Autenticación
- JWT con renovación automática
- Autenticación biométrica (huella/facial)
- OTP por email/SMS
- Control de acceso por roles

### Gestión por Roles
- **Admin**: Gestión completa de usuarios y sistema
- **Terapeuta**: Pacientes asignados y sesiones
- **Padres**: Seguimiento de hijos pacientes

### Características Clave
- Análisis de emociones en tiempo real con ML
- Calificación de terapeutas (1-5 estrellas)
- Reasignación automática por bajas calificaciones
- Videos educativos de YouTube integrados
- Sesiones terapéuticas con calendario
- Reportes de progreso con gráficos

## Estructura del Proyecto

```
app/src/main/java/com/example/serious_game_usil/
├── presentation/ui/
│   ├── administrador/    # Panels admin
│   ├── emotion/         # Análisis emocional
│   ├── login/           # Autenticación
│   ├── padres/          # UI cuidadores
│   ├── terapeuta/       # Dashboard terapeutas
│   └── videos/          # Videos educativos
├── repository/          # Capa de datos
├── interface/          # APIs
├── network/            # Configuración HTTP
└── utils/              # Utilidades
```

## Instalación

### Requisitos
- Android Studio Arctic Fox+
- Android SDK 34
- Dispositivo Android 11+ (API 30)

### Setup
```bash
git clone <repo>
# Abrir en Android Studio
# Sync Gradle dependencies
# Configurar variables en local.properties
# Build & Run
```

## Integración APIs

### Backend Go (Usuarios)
- Autenticación JWT
- Gestión de usuarios y roles
- Sesiones terapéuticas
- Calificaciones y reasignaciones

### ML Service Python
- Análisis de emociones
- Procesamiento de imágenes
- Rate limiting por rol

### YouTube Data API v3
- Videos educativos categorizados
- Búsqueda y filtrado
- Reproductor integrado

## Funcionalidades Avanzadas

### Análisis Emocional
- Captura por cámara/galería
- 5 emociones detectadas (disgustado, neutral, feliz, triste, enojado).
- Reportes visuales (radar/barras)
- Historial por sesión

### Sistema de Calificaciones
- Rating 1-5 estrellas post-sesión
- Reasignación automática (rating ≤3)
- Nueva sesión programada automáticamente
- Notificaciones de cambios

### Videos Educativos
- Categorías: Conducta, Comunicación, Emociones
- Búsqueda en tiempo real
- Paginación (5 videos por carga)
- Safe search habilitado

## Seguridad

- Encriptación local de datos sensibles
- Certificados SSL para comunicaciones
- Tokens JWT con expiración
- Validación de permisos granular
- Bloqueo por intentos fallidos

## Build & Deploy

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```