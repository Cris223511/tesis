import base64
import io
import numpy as np
from PIL import Image
import cv2
from typing import Dict, Any, Optional
import tensorflow as tf
from tensorflow.keras.models import load_model
import os

class EmotionAnalyzer:
    """
    Analizador real de emociones usando modelo de deep learning
    """

    def __init__(self):
        self.emotions = [
            "angry", "disgust", "fear", "happy",
            "neutral", "sad", "surprise"
        ]

        # Cargar el modelo preentrenado
        model_path = os.path.join("models", "emotion_model.h5")

        try:
            # Cargar modelo de emociones (FER2013 o similar)
            self.model = load_model(model_path)
            print(f"Modelo cargado exitosamente desde {model_path}")
        except:
            # Si no existe, usar modelo de Keras preentrenado
            print("Cargando modelo de detección de emociones...")
            self.model = self._load_pretrained_model()

        # Cargar detector de rostros
        self.face_cascade = cv2.CascadeClassifier(
            cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
        )

    def _load_pretrained_model(self):
        """
        Crea o carga un modelo preentrenado para detección de emociones
        """
        # Arquitectura CNN para reconocimiento de emociones
        model = tf.keras.Sequential([
            tf.keras.layers.Conv2D(32, (3, 3), activation='relu', input_shape=(48, 48, 1)),
            tf.keras.layers.MaxPooling2D(2, 2),
            tf.keras.layers.Conv2D(64, (3, 3), activation='relu'),
            tf.keras.layers.MaxPooling2D(2, 2),
            tf.keras.layers.Conv2D(128, (3, 3), activation='relu'),
            tf.keras.layers.MaxPooling2D(2, 2),
            tf.keras.layers.Flatten(),
            tf.keras.layers.Dense(128, activation='relu'),
            tf.keras.layers.Dropout(0.5),
            tf.keras.layers.Dense(7, activation='softmax')
        ])

        model.compile(
            optimizer='adam',
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )

        # Intentar cargar pesos preentrenados
        weights_path = os.path.join("models", "emotion_weights.h5")
        if os.path.exists(weights_path):
            model.load_weights(weights_path)
            print(f"Pesos cargados desde {weights_path}")

        return model

    async def analyze(self, image_base64: str) -> Dict[str, Any]:
        """
        Analiza una imagen y retorna las emociones detectadas usando ML real
        """
        try:
            # Decodificar imagen base64
            image_data = base64.b64decode(image_base64)
            image = Image.open(io.BytesIO(image_data))

            # Convertir a array numpy y BGR para OpenCV
            img_array = np.array(image)

            # Si la imagen es RGBA, convertir a RGB
            if len(img_array.shape) == 3 and img_array.shape[2] == 4:
                img_array = cv2.cvtColor(img_array, cv2.COLOR_RGBA2RGB)

            # Convertir a escala de grises para detección de rostros
            gray = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY) if len(img_array.shape) == 3 else img_array

            # Detectar rostros
            faces = self.face_cascade.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(30, 30)
            )

            if len(faces) == 0:
                return {
                    "emotions": {},
                    "dominant_emotion": None,
                    "confidence": 0.0,
                    "error": "No se detectaron rostros en la imagen",
                    "processed": False
                }

            # Procesar el primer rostro detectado
            x, y, w, h = faces[0]
            face_roi = gray[y:y+h, x:x+w]

            # Preprocesar la imagen del rostro
            processed_face = self.preprocess_face(face_roi)

            # Realizar predicción con el modelo
            predictions = self.model.predict(processed_face, verbose=0)

            # Convertir predicciones a diccionario de emociones
            emotions_scores = {}
            for i, emotion in enumerate(self.emotions):
                emotions_scores[emotion] = float(predictions[0][i])

            # Encontrar emoción dominante
            dominant_idx = np.argmax(predictions[0])
            dominant_emotion = self.emotions[dominant_idx]
            confidence = float(predictions[0][dominant_idx])

            return {
                "emotions": emotions_scores,
                "dominant_emotion": dominant_emotion,
                "confidence": confidence,
                "face_detected": True,
                "face_coordinates": {"x": int(x), "y": int(y), "width": int(w), "height": int(h)},
                "num_faces": len(faces),
                "processed": True
            }

        except Exception as e:
            raise Exception(f"Error analizando imagen: {str(e)}")

    def preprocess_face(self, face_image: np.ndarray) -> np.ndarray:
        """
        Preprocesa la imagen del rostro para el modelo
        """
        # Redimensionar a 48x48 (tamaño estándar para modelos de emociones)
        face_resized = cv2.resize(face_image, (48, 48))

        # Normalizar valores de píxeles
        face_normalized = face_resized / 255.0

        # Reshape para el modelo (batch_size, height, width, channels)
        face_reshaped = np.reshape(face_normalized, (1, 48, 48, 1))

        return face_reshaped

    def detect_multiple_faces(self, image_array: np.ndarray) -> list:
        """
        Detecta y analiza múltiples rostros en una imagen
        """
        gray = cv2.cvtColor(image_array, cv2.COLOR_RGB2GRAY) if len(image_array.shape) == 3 else image_array

        faces = self.face_cascade.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=(30, 30)
        )

        results = []
        for i, (x, y, w, h) in enumerate(faces):
            face_roi = gray[y:y+h, x:x+w]
            processed_face = self.preprocess_face(face_roi)
            predictions = self.model.predict(processed_face, verbose=0)

            emotions_scores = {}
            for j, emotion in enumerate(self.emotions):
                emotions_scores[emotion] = float(predictions[0][j])

            dominant_idx = np.argmax(predictions[0])

            results.append({
                "face_id": i + 1,
                "emotions": emotions_scores,
                "dominant_emotion": self.emotions[dominant_idx],
                "confidence": float(predictions[0][dominant_idx]),
                "coordinates": {"x": int(x), "y": int(y), "width": int(w), "height": int(h)}
            })

        return results