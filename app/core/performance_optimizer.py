import numpy as np
import cv2
from typing import Tuple, Optional
import tensorflow as tf
from functools import lru_cache
import asyncio
from concurrent.futures import ThreadPoolExecutor

class PerformanceOptimizer:
    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=4)
        self.image_cache = {}

    @staticmethod
    def optimize_image(image: np.ndarray, target_size: Tuple[int, int] = (48, 48)) -> np.ndarray:
        if len(image.shape) == 3 and image.shape[2] == 3:
            image = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

        if image.shape[:2] != target_size:
            image = cv2.resize(image, target_size, interpolation=cv2.INTER_AREA)

        image = image.astype(np.float32) / 255.0

        return image

    @staticmethod
    def batch_process_images(images: list, target_size: Tuple[int, int] = (48, 48)) -> np.ndarray:
        processed = []
        for img in images:
            processed.append(PerformanceOptimizer.optimize_image(img, target_size))
        return np.array(processed)

    @lru_cache(maxsize=128)
    def get_face_detector(self):
        return cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

    def detect_faces_optimized(self, image: np.ndarray) -> list:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if len(image.shape) == 3 else image

        gray = cv2.equalizeHist(gray)

        detector = self.get_face_detector()
        faces = detector.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=(30, 30),
            flags=cv2.CASCADE_SCALE_IMAGE
        )

        return faces

    async def process_image_async(self, image_data: bytes) -> np.ndarray:
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            self.executor,
            self._process_image_sync,
            image_data
        )
        return result

    def _process_image_sync(self, image_data: bytes) -> np.ndarray:
        nparr = np.frombuffer(image_data, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        return self.optimize_image(image)

class ModelOptimizer:
    @staticmethod
    def optimize_model(model: tf.keras.Model) -> tf.keras.Model:
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

        return model

    @staticmethod
    def quantize_model(model: tf.keras.Model) -> bytes:
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = ModelOptimizer._representative_dataset_gen
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS_INT8,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        converter.inference_input_type = tf.uint8
        converter.inference_output_type = tf.uint8

        tflite_model = converter.convert()
        return tflite_model

    @staticmethod
    def _representative_dataset_gen():
        for _ in range(100):
            data = np.random.rand(1, 48, 48, 1).astype(np.float32)
            yield [data]

class CacheOptimizer:
    def __init__(self, max_size: int = 100):
        self.cache = {}
        self.max_size = max_size
        self.access_count = {}

    def get(self, key: str) -> Optional[any]:
        if key in self.cache:
            self.access_count[key] = self.access_count.get(key, 0) + 1
            return self.cache[key]
        return None

    def set(self, key: str, value: any):
        if len(self.cache) >= self.max_size:
            self._evict_lru()
        self.cache[key] = value
        self.access_count[key] = 0

    def _evict_lru(self):
        if self.access_count:
            lru_key = min(self.access_count, key=self.access_count.get)
            del self.cache[lru_key]
            del self.access_count[lru_key]

    def clear(self):
        self.cache.clear()
        self.access_count.clear()