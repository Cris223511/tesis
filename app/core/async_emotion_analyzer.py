import asyncio
import numpy as np
import tensorflow as tf
from typing import Dict, List, Optional, Tuple
import base64
import io
from PIL import Image
import cv2
from concurrent.futures import ThreadPoolExecutor
import time

class AsyncEmotionAnalyzer:
    def __init__(self, model_path: str):
        self.model = None
        self.model_path = model_path
        self.executor = ThreadPoolExecutor(max_workers=4)
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
        self.emotion_labels = ['angry', 'disgust', 'fear', 'happy', 'sad']
        self._load_model()

    def _load_model(self):
        try:
            self.model = tf.keras.models.load_model(self.model_path)
            self.model.compile(
                optimizer=tf.keras.optimizers.Adam(learning_rate=0.0001),
                loss='categorical_crossentropy',
                metrics=['accuracy']
            )
        except Exception as e:
            print(f"Error loading model: {e}")
            self.model = None

    async def analyze_emotion_async(self, image_base64: str) -> Dict:
        loop = asyncio.get_event_loop()

        start_time = time.time()

        image_data = await loop.run_in_executor(
            self.executor,
            self._decode_base64_image,
            image_base64
        )

        if image_data is None:
            return {"error": "Failed to decode image"}

        faces = await loop.run_in_executor(
            self.executor,
            self._detect_faces,
            image_data
        )

        if len(faces) == 0:
            return {"error": "No faces detected"}

        predictions = await loop.run_in_executor(
            self.executor,
            self._predict_emotions,
            image_data,
            faces
        )

        processing_time = time.time() - start_time

        return {
            "emotions": predictions,
            "processing_time": f"{processing_time:.3f}s",
            "faces_detected": len(faces)
        }

    def _decode_base64_image(self, base64_string: str) -> Optional[np.ndarray]:
        try:
            if ',' in base64_string:
                base64_string = base64_string.split(',')[1]

            image_data = base64.b64decode(base64_string)
            image = Image.open(io.BytesIO(image_data))

            return np.array(image)
        except Exception as e:
            print(f"Error decoding image: {e}")
            return None

    def _detect_faces(self, image: np.ndarray) -> List[Tuple[int, int, int, int]]:
        try:
            if len(image.shape) == 3:
                gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
            else:
                gray = image

            faces = self.face_cascade.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(48, 48)
            )

            return faces.tolist() if len(faces) > 0 else []
        except Exception as e:
            print(f"Error detecting faces: {e}")
            return []

    def _predict_emotions(self, image: np.ndarray, faces: List) -> List[Dict]:
        if self.model is None:
            return []

        predictions = []

        for (x, y, w, h) in faces:
            try:
                face_img = image[y:y+h, x:x+w]

                if len(face_img.shape) == 3:
                    face_img = cv2.cvtColor(face_img, cv2.COLOR_RGB2GRAY)

                face_img = cv2.resize(face_img, (48, 48))
                face_img = face_img.astype('float32') / 255.0
                face_img = np.expand_dims(face_img, axis=0)
                face_img = np.expand_dims(face_img, axis=3)

                prediction = self.model.predict(face_img, verbose=0)[0]

                emotion_scores = {
                    self.emotion_labels[i]: float(prediction[i])
                    for i in range(len(self.emotion_labels))
                }

                dominant_emotion = self.emotion_labels[np.argmax(prediction)]
                confidence = float(np.max(prediction))

                predictions.append({
                    "face_location": {"x": x, "y": y, "width": w, "height": h},
                    "dominant_emotion": dominant_emotion,
                    "confidence": confidence,
                    "all_emotions": emotion_scores
                })

            except Exception as e:
                print(f"Error processing face: {e}")
                continue

        return predictions

    async def batch_analyze(self, images: List[str]) -> List[Dict]:
        tasks = [self.analyze_emotion_async(img) for img in images]
        results = await asyncio.gather(*tasks)
        return results

    def cleanup(self):
        self.executor.shutdown(wait=True)
        if self.model:
            tf.keras.backend.clear_session()