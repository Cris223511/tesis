# 📱 Serious Game USIL - Aplicación Android

Sistema integral de terapia con análisis de emociones para la gestión de pacientes, sesiones terapéuticas y seguimiento de progreso. Esta aplicación forma parte de un proyecto de tesis universitaria que integra machine learning, autenticación biométrica y gestión terapéutica avanzada.

## 🎯 Descripción del Proyecto

La aplicación **Serious Game USIL** es una plataforma completa diseñada para profesionales de la salud mental, terapeutas y cuidadores que trabajan con pacientes adultos (18-60 años). Combina gestión terapéutica tradicional con análisis de emociones mediante inteligencia artificial, proporcionando herramientas avanzadas para el seguimiento del progreso terapéutico.

## ✨ Características Principales

### 🔐 Sistema de Autenticación Avanzado
- **Login tradicional** con usuario/contraseña
- **Autenticación biométrica** (huella dactilar, reconocimiento facial)
- **JWT Tokens** con refresh automático
- **Registro de usuarios** con validación OTP por email/SMS
- **Recuperación de contraseña** mediante OTP seguro
- **Gestión de sesiones** con expiración automática
- **Rate limiting** para prevenir ataques de fuerza bruta

### 👥 Gestión de Roles
- **Administrador**: Gestión completa del sistema
- **Terapeuta**: Manejo de pacientes y sesiones
- **Padres/Cuidadores**: Seguimiento de sus hijos pacientes

### 🏥 Funcionalidades por Rol

#### Administrador
- Dashboard administrativo completo
- **Estadísticas globales diferenciadas por rol** 🆕
- Gestión de usuarios y roles
- Creación y edición de usuarios
- Gestión de pacientes
- **Navegación completa: Cuidadores → Pacientes → Sesiones → Calificar** 🆕
- **Calificación de terapeutas en nombre de cualquier cuidador** 🆕
- Análisis de progreso terapéutico
- Acceso a información del sistema

#### Terapeuta
- Dashboard especializado
- **Estadísticas personalizadas de pacientes propios** 🆕
- Gestión de pacientes asignados
- Seguimiento de sesiones terapéuticas
- **Visualización de calificaciones recibidas** 🆕
- Análisis de emociones en tiempo real
- Historial de progreso

#### Padres/Cuidadores
- Dashboard familiar
- Visualización de pacientes asignados (hijos)
- Seguimiento de sesiones terapéuticas
- **Calificación de terapeutas tras sesiones** 🆕
- **Visualización de calificaciones realizadas** 🆕
- Estadísticas de progreso
- Comparaciones de progreso mensual

### 🎭 Análisis de Emociones
- Captura de emociones mediante cámara
- Procesamiento con IA (conecta con emotion-ml-service)
- Análisis en tiempo real
- Historial de emociones
- Reportes visuales

### 📊 Dashboard y Estadísticas
- Gráficos de progreso terapéutico
- Comparaciones temporales (3 meses)
- Estadísticas detalladas
- Visualización de datos intuitiva

## 🛠 Tecnologías Utilizadas

### Lenguaje y Framework
- **Kotlin** - Lenguaje de programación principal
- **Android SDK 34** (Target SDK 34, Min SDK 30)
- **Jetpack Compose** - UI moderna declarativa
- **View Binding** - Binding de vistas tradicional

### Arquitectura y Patrones
- **MVVM** (Model-View-ViewModel)
- **Repository Pattern** - Abstracción de datos
- **Clean Architecture** - Separación de capas

### Librerías Principales

#### Networking
- **Retrofit 2.9.0** - Cliente HTTP
- **OkHttp 3** - Interceptores y logging
- **Gson** - Serialización JSON

#### UI y Material Design
- **Material Design 3** - Diseño moderno
- **Jetpack Compose BOM** - Componentes modernos
- **Constraint Layout** - Layouts flexibles
- **Swipe Refresh Layout** - Pull to refresh
- **RecyclerView** - Listas eficientes

#### Multimedia y Cámara
- **Glide 4.16.0** - Carga de imágenes
- **OpenCV** (indirecto) - Procesamiento de imágenes

#### Seguridad
- **Security Crypto** - Encriptación de datos sensibles
- **JWT** - Autenticación con tokens

#### Otros
- **CircleImageView** - Imágenes circulares
- **Lifecycle Components** - Manejo del ciclo de vida

## 📱 Estructura del Proyecto

```
app/src/main/java/com/example/serious_game_usil/
├── presentation/ui/           # Capas de presentación
│   ├── administrador/         # UI para administradores
│   ├── emotion/              # Análisis de emociones
│   ├── login/                # Autenticación
│   ├── main/                 # Actividad principal
│   ├── padres/               # UI para padres/cuidadores
│   ├── password/             # Gestión de contraseñas
│   ├── patients/             # Gestión de pacientes
│   ├── progress/             # Seguimiento de progreso
│   ├── register/             # Registro de usuarios
│   ├── splash/               # Pantalla de carga
│   └── terapeuta/            # UI para terapeutas
├── repository/               # Capa de datos
├── interface/                # APIs y servicios
├── network/                  # Configuración de red
├── utils/                    # Utilidades y adaptadores
└── ui/theme/                 # Temas y diseño
```

### Recursos
```
app/src/main/res/
├── drawable/                 # +99 recursos gráficos
├── layout/                   # +54 layouts XML
├── menu/                     # Menús de navegación
├── values/                   # Strings, colores, dimensiones
└── xml/                      # Configuraciones XML
```

## 🚀 Configuración y Instalación

### Prerrequisitos
- **Android Studio** Flamingo o superior
- **JDK 11** o superior
- **Android SDK 34**
- **Gradle 8.0+**

### Variables de Entorno
La app está configurada para conectarse a los servicios backend:

**Desarrollo:**
```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
buildConfigField("String", "RP_ORIGIN", "\"http://10.0.2.2:8080\"")
```

**Producción:**
```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://api.production.com\"")
buildConfigField("String", "RP_ORIGIN", "\"https://api.production.com\"")
```

### Pasos de Instalación

1. **Clonar el repositorio**
```bash
git clone <repository-url>
cd android/
```

2. **Abrir en Android Studio**
```bash
# Abrir Android Studio y seleccionar la carpeta android/
```

3. **Sincronizar dependencias**
```bash
# Android Studio sincronizará automáticamente las dependencias
# O usar: ./gradlew build
```

4. **Configurar emulador/dispositivo**
- Crear un AVD con API 30+
- O conectar dispositivo físico con USB debugging

5. **Ejecutar la aplicación**
```bash
./gradlew installDebug
# O usar el botón Run en Android Studio
```

## 🔗 Integración con Servicios Backend

### Backend de Usuarios (Go - Puerto 8080)
- Autenticación y autorización
- Gestión de usuarios y roles
- Gestión de pacientes
- APIs REST para CRUD operations

### Emotion ML Service (Python - Puerto 5000)
- Análisis de emociones con TensorFlow
- Procesamiento de imágenes
- APIs protegidas con JWT

### Redis (Puerto 6379)
- Cache de datos
- Rate limiting
- Gestión de sesiones

## 📋 Funcionalidades Detalladas

### 🔑 Autenticación
- **LoginActivity**: Login principal con validación
- **RegisterActivity**: Registro de nuevos usuarios
- **OtpVerificationActivity**: Verificación OTP
- **ResetPasswordActivity**: Recuperación de contraseña
- **ValidateEmailActivity**: Validación de email

### 👑 Panel de Administrador
- **DashboardActivity**: Dashboard principal
- **ListUserActivity**: Lista de usuarios
- **CreateUserActivity**: Crear usuarios
- **EditUserActivity**: Editar usuarios
- **ListRoles**: Gestión de roles
- **InfoActivity**: Información del sistema

### 👨‍⚕️ Panel de Terapeuta
- **TerapeutaDrawerActivity**: Dashboard con navegación
- Gestión completa de pacientes
- Análisis de emociones
- Seguimiento de sesiones

### 👨‍👩‍👧‍👦 Panel de Padres
- **PadresDashboardActivity**: Dashboard familiar
- **MyPatientsActivity**: Mis hijos pacientes
- **MySessionsActivity**: Sesiones terapéuticas
- **SessionDetailActivity**: Detalles de sesiones
- **RateTherapistActivity**: Calificación de terapeutas 🆕
- **SimpleTherapySessionsActivity**: Sesiones filtradas por paciente 🆕

### 🎭 Análisis de Emociones
- **EmotionAnalysisActivity**: Captura y análisis
- Integración con cámara
- Procesamiento en tiempo real
- Visualización de resultados

### 📊 Gestión de Pacientes
- **PatientsListActivity**: Lista de pacientes
- **CreateEditPatientActivity**: Crear/editar pacientes
- **PatientDetailActivity**: Detalles del paciente
- **ProgressDetailActivity**: Progreso terapéutico

## 🔒 Seguridad

- **JWT Tokens** para autenticación
- **Security Crypto** para datos sensibles
- **Network Security Config** personalizada
- **Rate Limiting** en APIs
- **Validación** de permisos por rol

## 📱 Permisos Requeridos

- `INTERNET` - Conexión a internet
- `ACCESS_NETWORK_STATE` - Estado de la red
- `ACCESS_WIFI_STATE` - Estado del WiFi
- `CAMERA` - Acceso a cámara (para análisis de emociones)
- `READ_EXTERNAL_STORAGE` - Lectura de archivos
- `WRITE_EXTERNAL_STORAGE` - Escritura de archivos (API ≤28)
- `VIBRATE` - Feedback háptico

## 🎨 Diseño y UX

- **Material Design 3** - Diseño moderno y consistente
- **Orientación Portrait** - Todas las pantallas
- **Temas personalizados** - Dark/Light mode compatible
- **Navegación intuitiva** - Drawer navigation y back stack
- **Responsive design** - Adaptable a diferentes pantallas

## 🧪 Testing

El proyecto incluye configuración para:
- **Unit Tests** con JUnit
- **Integration Tests** con AndroidJUnit
- **UI Tests** con Espresso
- **Compose Tests** con UI Test JUnit4

```bash
# Ejecutar tests
./gradlew test
./gradlew connectedAndroidTest
```

## 🚀 Build y Deploy

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### APK Location
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## 📚 Documentación Adicional

- **Swagger API Docs**: Disponible en el backend de usuarios
- **Figma Design**: [Enlace al diseño si existe]
- **Arquitectura**: Documentación técnica detallada
- **Testing Strategy**: Guía de pruebas

## 🤝 Contribución

1. Fork el proyecto
2. Crear rama feature (`git checkout -b feature/AmazingFeature`)
3. Commit cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abrir Pull Request

## 📄 Licencia

Este proyecto es parte de una tesis académica de la Universidad San Ignacio de Loyola (USIL).

## 📞 Contacto

**Proyecto**: Serious Game USIL - Sistema de Análisis de Emociones
**Universidad**: Universidad San Ignacio de Loyola (USIL)
**Tipo**: Proyecto de Tesis

## 🆕 Nuevas Funcionalidades Implementadas

### Sistema de Calificaciones de Terapeutas
- **Interfaz de calificación**: `RateTherapistActivity` permite calificar terapeutas del 1-5 estrellas
- **Comentarios opcionales**: Los usuarios pueden agregar comentarios detallados sobre la sesión
- **Verificación automática**: El botón de calificación se deshabilita automáticamente si la sesión ya fue calificada
- **Visualización dinámica**: Las calificaciones existentes se muestran con estrellas y comentarios en `SessionDetailActivity`
- **Permisos por rol**: Los administradores pueden calificar en nombre de cualquier cuidador

### Dashboard Diferenciado por Rol
- **`TerapeutaDrawerActivity`** con estadísticas personalizadas según el rol del usuario:
  - **Administradores**: Estadísticas globales (total de terapeutas, pacientes, sesiones, calificaciones)
  - **Terapeutas**: Estadísticas propias (pacientes asignados, sesiones programadas, calificaciones recibidas)
- **Carga asíncrona**: Las estadísticas se cargan dinámicamente desde múltiples endpoints
- **UI adaptativa**: La interfaz se adapta según los permisos del usuario

### Navegación Administrativa Completa
- **Flujo de navegación mejorado**: Admin puede navegar desde Cuidadores → Pacientes → Sesiones específicas
- **`PatientDetailActivity`** con botón "Ver Sesiones" que filtra sesiones por paciente
- **`SimpleTherapySessionsActivity`** optimizada para mostrar sesiones filtradas
- **Integración completa**: Desde cualquier punto se puede acceder a la funcionalidad de calificación

### Mejoras en la UI/UX
- **Botón dinámico de calificación**: Cambia su estado visual cuando una sesión ya fue calificada
- **Iconografía mejorada**: Cambio del ícono "Gestionar Sesiones" por uno más apropiado (`ic_menu_agenda`)
- **Feedback visual**: Indicadores claros del estado de las calificaciones con colores y transparencias
- **Manejo de errores**: Validación robusta de permisos y datos antes de permitir calificaciones

### Nuevas Actividades y Componentes
- **`RateTherapistActivity.kt`**: Interfaz completa para calificar terapeutas con validación
- **Adaptadores mejorados**: `TherapySessionAdapter` con mejor manejo de datos y navegación
- **ViewModels actualizados**: `TherapySessionViewModel` con nuevas funcionalidades de calificación
- **Integración con APIs**: Nuevos endpoints para verificar y crear calificaciones

### Mejoras en Autenticación y Seguridad
- **`AuthManager`** mejorado para manejo de roles y permisos granulares
- **Validación de permisos**: Verificación dinámica de roles antes de permitir acciones sensibles
- **Manejo de tokens**: Configuración automática de tokens JWT para nuevas APIs de calificación

## 🐛 Correcciones Recientes

### Interfaz de Usuario Optimizada
- **FAB Compacto**: Reemplazo del `ExtendedFloatingActionButton` con texto "NUEVO USUARIO" por un `FloatingActionButton` compacto con solo ícono
- **Iconos actualizados**: Cambio del ícono de paciente infantil por adulto en el dashboard del terapeuta
- **Formato de fechas mejorado**: Las fechas en listas se muestran en formato corto (dd/MM/yy) en lugar del timestamp completo

### Gestión de Cuidadores
- **Visualización de fotos**: Corrección del procesamiento de imágenes base64 con prefijos data URI
- **Estados activos/inactivos**: Inversión correcta de la lógica booleana (BD: 0=activo, 1=inactivo)
- **Conteo de pacientes**: Texto simplificado de "X pacientes asignados" a "X pacientes"
- **Endpoint corregido**: Cambio de `/api/users` inexistente a `/api/users/search` funcional

### Creación de Sesiones Terapéuticas
- **Búsqueda de terapeutas**: Corrección de consultas SQL usando IDs de roles en lugar de nombres
- **Visibilidad condicional**: El campo de terapeuta se oculta automáticamente cuando el usuario es terapeuta
- **Observadores duplicados**: Eliminación de múltiples observadores de Flow que causaban conflictos

### Procesamiento de Imágenes
- **Base64 con prefijos**: Eliminación automática de prefijos "data:image/jpeg;base64," antes de decodificar
- **Validación mejorada**: Verificación de longitud mínima de string base64 antes de procesamiento
- **Manejo de errores**: Fallback a placeholder cuando la decodificación falla

---

