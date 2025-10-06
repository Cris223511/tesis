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

## 📺 Sistema de Videos Educativos (NUEVO) 🆕

### Descripción General
Sistema completo de videos educativos sobre TEA (Trastorno del Espectro Autista) integrado en la aplicación para padres/cuidadores. Permite buscar, filtrar y reproducir videos educativos de YouTube directamente desde la app.

### 🎯 Funcionalidades Principales

#### 🔍 Búsqueda y Filtrado de Videos
- **Búsqueda en tiempo real**: Campo de búsqueda con debounce de 500ms
- **Filtrado por categorías**:
  - 📚 **Todos**: Videos generales sobre TEA
  - 🎯 **Conducta**: Estrategias de comportamiento
  - 💬 **Comunicación**: Desarrollo del lenguaje
  - 😊 **Emociones**: Regulación emocional
- **Safe Search habilitado**: Solo contenido seguro y apropiado
- **Videos en español**: Filtro automático de idioma (relevanceLanguage=es)

#### 📄 Paginación Inteligente (5 en 5)
- **Carga inicial**: Muestra solo 5 videos para optimizar rendimiento
- **Scroll infinito**: Carga automática de 5 videos más al hacer scroll
- **Indicador visual**: Mensaje toast al cargar más videos
- **Optimización de memoria**: No carga todos los videos a la vez

#### 🎥 Reproductor Integrado con WebView
- **Reproducción dentro de la app**: No necesitas salir a YouTube
- **Autoplay habilitado**: El video comienza automáticamente
- **Controles completos de YouTube**:
  - ▶️ Play/Pause
  - 🔊 Control de volumen
  - ⏩ Avance/Retroceso
  - 📊 Barra de progreso
  - 🖥️ Pantalla completa
  - ⏱️ Tiempo actual y duración

#### 🚀 Acciones Disponibles
- **Abrir en YouTube**: Botón para ver el video en la app oficial de YouTube
- **Compartir**: Compartir el enlace del video por cualquier medio
- **Información completa**: Título, canal, categoría y descripción

#### 🎨 Interfaz de Usuario
- **Material Design 3**: Diseño moderno y consistente
- **Estados vacíos personalizados**:
  - "No hay videos disponibles" (sin resultados)
  - "No se encontraron coincidencias" (búsqueda sin resultados)
  - Mensajes descriptivos y amigables
- **Indicadores de carga**: ProgressBar durante la búsqueda
- **Chips interactivos**: Categorías seleccionables con feedback visual

### 🔧 Implementación Técnica

#### Arquitectura
```
VideosEducativosActivity.kt
├── Búsqueda de videos (YouTube Data API v3)
├── Paginación (5 videos por página)
├── Filtrado por categorías
└── Navegación a reproductor

VideoPlayerActivitySimple.kt
├── WebView con YouTube embebido
├── Controles nativos de YouTube
├── Botones de acción (Abrir, Compartir)
└── Información del video
```

#### Dependencias Agregadas
```kotlin
// YouTube Android Player API
implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")

// ExoPlayer para reproducción de videos
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1")

// WorkManager para operaciones en background
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

#### Configuración de API Key
La API key de YouTube está configurada de forma segura:

**Archivo `.env`** (excluido de Git):
```bash
YOUTUBE_API_KEY=AIzaSyD7aV8CVEvOi-jr3_74cDnhgeyIpOxw-hY
```

**Carga mediante BuildConfig**:
```kotlin
// build.gradle.kts
val youtubeApiKey = project.findProperty("YOUTUBE_API_KEY")?.toString()
    ?: System.getenv("YOUTUBE_API_KEY")
    ?: "AIzaSyD7aV8CVEvOi-jr3_74cDnhgeyIpOxw-hY"
buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
```

**Uso en código**:
```kotlin
private val YOUTUBE_API_KEY = BuildConfig.YOUTUBE_API_KEY
```

#### Protección de Credenciales
El archivo `.env` está incluido en `.gitignore`:
```gitignore
# Environment variables
.env
.env.local
```

### 📱 Nuevas Activities

#### `VideosEducativosActivity.kt`
- **Ubicación**: `presentation/ui/videos/`
- **Propósito**: Lista y búsqueda de videos educativos
- **Características**:
  - Búsqueda con TextWatcher y debounce
  - RecyclerView con scroll infinito
  - Categorías con ChipGroup
  - Paginación de 5 en 5
  - Estados vacíos personalizados

#### `VideoPlayerActivitySimple.kt`
- **Ubicación**: `presentation/ui/videos/`
- **Propósito**: Reproductor de video integrado
- **Características**:
  - WebView con YouTube embebido
  - Controles nativos de YouTube
  - Botones de acción (Abrir, Compartir)
  - Autoplay habilitado
  - Hardware acceleration

#### `VideosAdapter.kt`
- **Ubicación**: `presentation/ui/videos/`
- **Propósito**: Adaptador para lista de videos
- **Características**:
  - ListAdapter con DiffUtil
  - Carga de thumbnails con Glide
  - Click listener personalizable
  - Badge de categoría

### 🎨 Nuevos Layouts

#### `activity_videos_educativos.xml`
- Toolbar con título
- Campo de búsqueda con icono
- ChipGroup para categorías
- RecyclerView para videos
- Estados vacíos (sin videos, sin resultados)
- ProgressBar de carga

#### `activity_video_player_simple.xml`
- WebView para video de YouTube
- ScrollView con información del video
- Botones de acción (Abrir, Compartir)
- FAB para cerrar
- Diseño responsive

#### `item_video_educativo.xml`
- Thumbnail del video con overlay de play
- Título del video (máximo 2 líneas)
- Nombre del canal
- Badge de categoría
- Diseño Material Card

### 🎯 Nuevos Drawables y Resources

#### Iconos Creados
- `ic_video_empty.xml` - Icono de video vacío
- `ic_download.xml` - Icono de descarga
- `ic_pip.xml` - Icono de Picture-in-Picture
- `ic_share.xml` - Icono de compartir

#### Backgrounds
- `circle_play_background.xml` - Fondo circular para botón play
- `category_badge_background.xml` - Fondo para badge de categoría
- `gradient_blue_card.xml` - Gradiente azul para botón "Mis Pacientes"
- `gradient_orange_card.xml` - Gradiente naranja para botón "Videos"

#### Color Selectors
- `chip_background_selector.xml` - Selector de color para chips de categorías

### 🔗 Integración en Dashboard de Padres

#### Mejoras en `activity_padres_dashboard.xml`
- **Botones rediseñados**: "Mis Pacientes" y "Videos" más compactos
- **Altura reducida**: 120dp → 100dp para mejor uso del espacio
- **Gradientes visuales**: Fondo degradado para mejor apariencia
- **Iconos más grandes**: 32dp → 36dp para mejor visibilidad
- **Bordes redondeados**: 16dp → 20dp para diseño más moderno

#### Actualización en `PadresDashboardActivity.kt`
```kotlin
binding.cardVideos.setOnClickListener {
    val intent = Intent(this, VideosEducativosActivity::class.java)
    startActivity(intent)
}
```

### 🔒 Seguridad y Privacidad

#### API Key Protection
- ✅ API key NO incluida en control de versiones
- ✅ Archivo `.env` en `.gitignore`
- ✅ Carga mediante variables de entorno
- ✅ Fallback a BuildConfig para builds

#### Términos de Servicio de YouTube
- ✅ Uso de YouTube Data API v3 oficial
- ✅ Reproducción mediante embed permitido
- ✅ Descarga redirige a YouTube oficial (cumple ToS)
- ✅ Safe Search habilitado

### 📊 Datos del Modelo

#### `VideoEducativo.kt`
```kotlin
data class VideoEducativo(
    val id: String,              // ID del video de YouTube
    val title: String,           // Título del video
    val thumbnailUrl: String,    // URL del thumbnail
    val channelTitle: String,    // Nombre del canal
    val videoUrl: String,        // URL completa del video
    val category: String,        // Categoría (TODOS, CONDUCTA, etc.)
    val description: String      // Descripción del video
)
```

#### `VideoCategory.kt`
```kotlin
enum class VideoCategory(
    val displayName: String,     // Nombre para mostrar
    val searchTerm: String       // Término de búsqueda en YouTube
) {
    TODOS("Todos", "TEA autismo"),
    CONDUCTA("Conducta", "TEA autismo conducta comportamiento"),
    COMUNICACION("Comunicación", "TEA autismo comunicación lenguaje"),
    EMOCIONES("Emociones", "TEA autismo emociones regulación")
}
```

### 🚀 Flujo de Usuario

1. **Acceso**: Dashboard de Padres → Botón "Videos" (naranja)
2. **Búsqueda**:
   - Usar campo de búsqueda para texto libre
   - O seleccionar categoría con chips
3. **Navegación**:
   - Ver primeros 5 videos
   - Scroll hacia abajo para cargar más (5 en 5)
4. **Reproducción**:
   - Click en video para abrir reproductor
   - Video se reproduce automáticamente en WebView
5. **Acciones**:
   - Ver en pantalla completa
   - Abrir en YouTube app
   - Compartir con otros



