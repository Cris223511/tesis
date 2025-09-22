# Emotion ML Service

Servicio de análisis de emociones con machine learning usando CNN, integrado con sistema de autenticación JWT y control de acceso basado en roles.

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
GOLANG_SERVICE_URL=http://localhost:8080
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
| **Terapeuta** | TP | Analizar emociones (100/día), ver sus análisis, editar sus análisis (2/día) |
| **Cuidador** | CD |  ver analisis y reportes |,
| **Paciente** | PC |  ver analisis y reportes |,
| **Admin** | AD | Sin límites, acceso completo, puede eliminar cualquier análisis |

## 📊 Modelo de Machine Learning

### Arquitectura del modelo
- **Tipo:** CNN (Convolutional Neural Network) personalizada
- **Emociones detectadas:** Angry, Disgust, Fear, Happy, Neutral, Sad, Surprise (7 clases)
- **Tamaño de entrada:** 48x48 píxeles en escala de grises
- **Detección de rostros:** OpenCV Haar Cascade Classifier
- **Parámetros:** 685,159 parámetros entrenables

### Estructura de la CNN

```
Layer (type)                Output Shape              Param #
================================================================
conv2d (Conv2D)             (None, 46, 46, 32)        320
batch_normalization         (None, 46, 46, 32)        128
conv2d_1 (Conv2D)           (None, 44, 44, 32)        9248
max_pooling2d               (None, 22, 22, 32)        0
dropout (25%)               (None, 22, 22, 32)        0

conv2d_2 (Conv2D)           (None, 20, 20, 64)        18496
batch_normalization_1       (None, 20, 20, 64)        256
conv2d_3 (Conv2D)           (None, 18, 18, 64)        36928
max_pooling2d_1             (None, 9, 9, 64)          0
dropout_1 (25%)             (None, 9, 9, 64)          0

conv2d_4 (Conv2D)           (None, 7, 7, 128)         73856
batch_normalization_2       (None, 7, 7, 128)         512
conv2d_5 (Conv2D)           (None, 5, 5, 128)         147584
max_pooling2d_2             (None, 2, 2, 128)         0
dropout_2 (25%)             (None, 2, 2, 128)         0

flatten                     (None, 512)               0
dense (Dense)               (None, 512)               262656
batch_normalization_3       (None, 512)               2048
dropout_3 (50%)             (None, 512)               0
dense_1 (Dense)             (None, 256)               131328
dropout_4 (50%)             (None, 256)               0
dense_2 (Dense)             (None, 7)                 1799
================================================================
Total params: 685,159 (2.61 MB)
```

### Características técnicas
- **Optimizador:** Adam con learning rate adaptativo
- **Función de pérdida:** Categorical Crossentropy
- **Regularización:** Dropout (25% y 50%) + Batch Normalization
- **Aumento de datos:** Rotación, traslación, zoom, flip horizontal
- **Callbacks:** EarlyStopping, ReduceLROnPlateau, ModelCheckpoint

## 🧠 Entrenamiento del modelo

### Requisitos para entrenar
```bash
# Instalar dependencias adicionales
pip install pandas matplotlib seaborn scikit-learn

# Dataset recomendado: FER2013
# Descargar desde: https://www.kaggle.com/datasets/msambare/fer2013
# Colocar en: data/fer2013.csv
```

### Entrenar modelo personalizado

```bash
# Entrenar con dataset FER2013 (recomendado)
python train_model.py

# El script automáticamente:
# 1. Descarga/verifica el dataset FER2013
# 2. Preprocesa las imágenes (48x48, normalización)
# 3. Divide datos (80% train, 20% validación)
# 4. Entrena CNN con data augmentation
# 5. Evalúa rendimiento y genera gráficos
# 6. Guarda el mejor modelo
```

### Estructura del dataset esperado

```csv
emotion,pixels,Usage
0,"23 45 67 89 ...",Training
1,"12 34 56 78 ...",PublicTest
...
```

- **emotion:** 0=Angry, 1=Disgust, 2=Fear, 3=Happy, 4=Neutral, 5=Sad, 6=Surprise
- **pixels:** 2304 valores (48x48) separados por espacios
- **Usage:** Training/PublicTest/PrivateTest

### Archivos generados después del entrenamiento

```
models/
├── emotion_model.h5          # Modelo final entrenado
├── best_emotion_model.h5     # Mejor modelo durante entrenamiento
└── emotion_weights.h5        # Solo pesos (opcional)

training_logs/
├── confusion_matrix.png      # Matriz de confusión
├── training_history.png      # Gráficos de entrenamiento
└── training_log.txt          # Log detallado
```

### Evaluación del modelo

El script de entrenamiento genera automáticamente:

1. **Precisión por clase:** Reporte detallado de clasificación
2. **Matriz de confusión:** Visualización de errores entre clases
3. **Curvas de entrenamiento:** Pérdida y precisión por época
4. **Prueba con muestra:** Predicción de ejemplo con distribución de probabilidades

### Ejemplo de salida esperada

```bash
🎯 Precisión en validación: 0.8542
📋 Reporte de clasificación:
               precision    recall  f1-score   support
       angry       0.82      0.78      0.80        28
     disgust       0.89      0.85      0.87        28
        fear       0.79      0.82      0.80        28
       happy       0.96      0.93      0.94        28
     neutral       0.81      0.86      0.83        28
         sad       0.78      0.75      0.76        28
    surprise       0.92      0.89      0.90        28

🧪 Prueba con muestra:
🎯 Emoción real: happy
🤖 Predicción: happy (94.23%)
📊 Distribución completa:
  angry: 2.34%
  disgust: 0.87%
  fear: 1.45%
  happy: 94.23%
  neutral: 0.67%
  sad: 0.32%
  surprise: 0.12%
```

### Usar modelo personalizado

Una vez entrenado, el servicio automáticamente usará el nuevo modelo:

```python
# El servicio carga automáticamente:
# 1. models/emotion_model.h5 (si existe)
# 2. Modelo preentrenado (fallback)

# Para forzar recarga:
# Reiniciar el servicio FastAPI
uvicorn app.main:app --reload --port 5000
```

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

## 🧹 Limpieza y Mantenimiento del Proyecto

### Archivos eliminados durante la última limpieza (2025-09-21)

#### ✅ Archivos duplicados removidos:
- `./app/middlewares/auth 2.py` - Copia duplicada del middleware de autenticación
- `./app/api/routes 2.py` - Copia duplicada de las rutas API
- `./app/api/schemas 2.py` - Copia duplicada de los esquemas Pydantic

#### ✅ Archivos innecesarios del core removidos:
- `./app/core/model_manager.py` - Manager de pool de modelos (no utilizado)
- `./app/core/predictor.py` - Predictor con batch processing (no utilizado)
- `./app/core/preprocessor.py` - Preprocesador con CLAHE (no utilizado)

#### ✅ Archivos del sistema removidos:
- `.DS_Store` files - Archivos de metadatos de macOS

### Estructura actual del directorio core:
```
app/core/
├── __init__.py
├── real_emotion_analyzer.py    # ✅ Analizador principal funcional
└── emotion_analyzer.py         # ✅ Versión anterior (compatibilidad)
```

### ¿Por qué se removieron estos archivos?

1. **Archivos duplicados**: Creados accidentalmente durante el desarrollo, contenían código idéntico o desactualizado
2. **Módulos no utilizados**: Los archivos `model_manager.py`, `predictor.py` y `preprocessor.py` implementaban funcionalidades avanzadas como:
   - Pool de modelos para concurrencia
   - Procesamiento por lotes (batch processing)
   - Mejoras de iluminación con CLAHE
   - Test-Time Augmentation (TTA)

   Sin embargo, el servicio actual usa `RealEmotionAnalyzer` que implementa análisis visual directo más efectivo que CNN.

3. **Archivos de sistema**: `.DS_Store` son metadatos de macOS que no deben estar en el repositorio

### Archivos conservados:

#### Scripts de entrenamiento:
- `train_real_model.py` - Para entrenar futuros modelos CNN
- `download_model.py` - Para descargar modelos preentrenados

#### Análisis funcional:
- `real_emotion_analyzer.py` - Implementación principal que combina CNN + análisis visual
- `emotion_analyzer.py` - Versión anterior conservada por compatibilidad

### Verificación post-limpieza:
✅ Importación del servicio: Sin errores
✅ Carga del modelo CNN: Exitosa
✅ Estructura del proyecto: Organizada
✅ Funcionalidad: Mantenida completamente

### Recomendaciones para el futuro:
1. **Evitar duplicados**: Usar herramientas de control de versión correctamente
2. **Limpieza regular**: Revisar archivos no utilizados cada sprint
3. **Documentación**: Mantener este registro actualizado en cada limpieza

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

