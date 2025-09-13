import numpy as np
from typing import List, Dict, Optional
import time
from app.config import settings
from app.core.preprocessor import FacePreprocessor
from app.core.model_manager import ModelPool

class EmotionPredictor:
    def __init__(self, model_pool: ModelPool):
        self.model_pool = model_pool
        self.preprocessor = FacePreprocessor()
        self.emotions = settings.emotions
        self.bias_correction = np.array(settings.bias_correction)
    
    async def predict_batch(self, images: List[np.ndarray]) -> List[Dict]:
        start_time = time.time()
        
        # Preprocess all images
        processed = []
        for img in images:
            face = self.preprocessor.preprocess(img)
            if face is not None:
                processed.append(face)
        
        if not processed:
            return [{"error": "No faces detected"}] * len(images)
        
        # Get model and predict
        model = await self.model_pool.get_model()
        try:
            batch_array = np.array(processed) / 255.0
            predictions = model.predict(batch_array, batch_size=32, verbose=0)
            
            results = []
            for pred in predictions:
                # Apply bias correction
                pred = pred * self.bias_correction
                pred = pred / np.sum(pred)
                
                emotion_idx = np.argmax(pred)
                confidence = float(pred[emotion_idx])
                
                results.append({
                    "emotion": self.emotions[emotion_idx],
                    "confidence": confidence,
                    "distribution": pred.tolist(),
                    "processing_time_ms": (time.time() - start_time) * 1000
                })
            
            return results
        finally:
            await self.model_pool.return_model(model)
    
    def apply_tta(self, image: np.ndarray) -> List[np.ndarray]:
        augmented = [image]
        augmented.append(cv2.flip(image, 1))
        
        for angle in [5, -5]:
            M = cv2.getRotationMatrix2D((112, 112), angle, 1.0)
            rotated = cv2.warpAffine(image, M, (224, 224))
            augmented.append(rotated)
        
        for factor in [0.9, 1.1]:
            bright = cv2.convertScaleAbs(image, alpha=factor, beta=0)
            augmented.append(bright)
        
        return augmented
