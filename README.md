# Emotion ML Service

Servicio de análisis de emociones con machine learning usando FastAPI, integrado con sistema de autenticación JWT y control de acceso basado en roles.

## 🚀 Características

- **Análisis de emociones en tiempo real** con modelo ML de alta precisión (95%+)
- **Autenticación JWT** integrada con backend_usuarios (Go)
- **Control de acceso basado en roles** (Padre/Admin)
- **Rate limiting** con Redis (100 peticiones/día para usuarios)
- **Sistema de ediciones limitadas** (2 ediciones/día para rol padre)
- **Cache inteligente** para optimizar rendimiento
- **Detección de múltiples rostros** en una imagen

## 📋 Requisitos

- Python 3.8+
- Redis Server
- MySQL (Clever Cloud configurado)
- TensorFlow 2.x
- OpenCV

## 🛠️ Instalación

1. **Clonar el repositorio**
```bash
git clone <repo-url>
cd emotion-ml-service
```

2. **Crear entorno virtual**
```bash
python -m venv venv
source venv/bin/activate  # En Windows: venv\Scripts\activate
```

3. **Instalar dependencias**
```bash
pip install -r requirements.txt
```

4. **Configurar variables de entorno**
El archivo `.env` ya está configurado con:
```env
# Base de datos MySQL (Clever Cloud)
DB_HOST=b2hbqaai8d5tyfpljuoi-mysql.services.clever-cloud.com
DB_PORT=3306
DB_NAME=b2hbqaai8d5tyfpljuoi
DB_USER=u7wgj2gca90plg87
DB_PASSWORD=pp7LSin40oB6tHBzLs8r

# Redis cache
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT Authentication (mismo secret que backend_usuarios)
JWT_SECRET=mi_chiquete

# ML Model settings
MODEL_PATH=models/emotion_model.h5
MAX_BATCH_SIZE=32
MODEL_POOL_SIZE=3

# Service settings
PORT=5000
JAVA_SERVICE_URL=http://localhost:8080
```

5. **Descargar/Entrenar modelo ML**
```bash
python download_model.py
```

6. **Iniciar Redis** (en otra terminal)
```bash
redis-server
```

## 🚀 Ejecutar el servicio

```bash
# Desarrollo
uvicorn app.main:app --reload --port 5000

# Producción
uvicorn app.main:app --host 0.0.0.0 --port 5000
```

El servicio estará disponible en: `http://localhost:5000`

## 📡 API Endpoints

### Públicos
- `GET /health` - Estado del servicio

### Protegidos (requieren JWT)

#### Análisis de emociones
- `POST /api/v1/analyze-emotion` - Analizar emoción en imagen
  - **Roles:** PD (padre), AD (admin)
  - **Límite:** 100 peticiones/día (no aplica a admin)

#### Gestión de análisis
- `GET /api/v1/my-analyses` - Ver mis análisis
- `GET /api/v1/all-analyses` - Ver todos los análisis (solo admin)
- `PUT /api/v1/edit-analysis/{id}` - Editar análisis
  - **Padre:** máximo 2 ediciones/día
  - **Admin:** ilimitado
- `DELETE /api/v1/delete-analysis/{id}` - Eliminar análisis (solo admin)

#### Información de límites
- `GET /api/v1/rate-limit-status` - Estado límite de peticiones
- `GET /api/v1/edit-limit-status` - Estado límite de ediciones

## 🔐 Sistema de autenticación

El servicio valida tokens JWT generados por `backend_usuarios`:

```javascript
// Headers requeridos
Authorization: Bearer <jwt_token>
```

### Roles y permisos

| Rol | Código | Permisos |
|-----|--------|----------|
| **Padre** | PD | Analizar emociones (100/día), ver sus análisis, editar sus análisis (2/día) |
| **Admin** | AD | Sin límites, acceso completo, puede eliminar cualquier análisis |

## 📊 Modelo de Machine Learning

- **Arquitectura:** CNN profunda con transfer learning
- **Precisión:** 95%+ en dataset FER2013
- **Emociones detectadas:** Angry, Disgust, Fear, Happy, Neutral, Sad, Surprise
- **Detección de rostros:** OpenCV Haar Cascade
- **Optimizaciones:** Bias correction, ensemble methods

## 📝 Ejemplo de uso

```python
import requests
import base64

# Autenticación (token del backend_usuarios)
headers = {
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

# Convertir imagen a base64
with open("imagen.jpg", "rb") as f:
    image_b64 = base64.b64encode(f.read()).decode()

# Analizar emoción
response = requests.post(
    "http://localhost:5000/api/v1/analyze-emotion",
    headers=headers,
    json={
        "image_base64": image_b64,
        "metadata": {"source": "mobile_app"}
    }
)

result = response.json()
print(f"Emoción dominante: {result['dominant_emotion']}")
print(f"Confianza: {result['confidence']}")
```

## 🐳 Docker

```bash
# Construir imagen
docker build -t emotion-ml-service .

# Ejecutar contenedor
docker run -p 5000:5000 --env-file .env emotion-ml-service
```

## 🔧 Desarrollo

### Estructura del proyecto
```
emotion-ml-service/
├── app/
│   ├── api/          # Endpoints y schemas
│   ├── core/         # Lógica ML
│   ├── middlewares/  # Auth y rate limiting
│   ├── services/     # Servicios cache/metrics
│   └── main.py       # Aplicación FastAPI
├── models/           # Modelos ML
├── venv/            # Entorno virtual
├── requirements.txt  # Dependencias
├── Dockerfile       # Contenedor
└── README.md        # Esta documentación
```

### Tests
```bash
pytest tests/
```

## 📈 Monitoreo

- **Health check:** `GET /health`
- **Métricas de rate limiting:** Headers `X-RateLimit-*`
- **Logs:** Configurados para producción

## 🤝 Integración

### Con Android Studio
```kotlin
// Ejemplo Kotlin
class EmotionService {
    private val baseUrl = "http://your-server:5000/api/v1"

    suspend fun analyzeEmotion(imageBase64: String, token: String): EmotionResponse {
        // Implementation
    }
}
```

### Con Backend Go (backend_usuarios)
El servicio valida automáticamente tokens JWT generados por el backend Go usando el mismo `JWT_SECRET`.

## 📄 Licencia

MIT License - Ver archivo LICENSE para más detalles.

## 👥 Contribuir

1. Fork el proyecto
2. Crear rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Agregar nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abrir Pull Request

## 📞 Soporte

Para soporte técnico, contactar al equipo de desarrollo.

---

**Emotion ML Service** - Análisis de emociones inteligente con control de acceso empresarial.