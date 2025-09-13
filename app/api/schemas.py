from pydantic import BaseModel
from typing import Optional, List, Dict
from datetime import datetime

class EmotionRequest(BaseModel):
    image_base64: str
    metadata: Optional[dict] = None

class EmotionResponse(BaseModel):
    success: bool
    emotions: Optional[dict] = None
    dominant_emotion: Optional[str] = None
    confidence: Optional[float] = None
    error: Optional[str] = None
    timestamp: datetime = datetime.now()

class RateLimitInfo(BaseModel):
    limit: int
    remaining: int
    reset: Optional[str] = None
    current: int

class HealthResponse(BaseModel):
    status: str
    timestamp: datetime
    service: str = "emotion-ml-service"

class ErrorResponse(BaseModel):
    error: str
    detail: Optional[str] = None
    timestamp: datetime = datetime.now()

# Esquemas antiguos para compatibilidad si los necesitas
class PredictionRequest(BaseModel):
    image_base64: str
    use_tta: bool = True

class BatchPredictionRequest(BaseModel):
    images: List[str]
    use_tta: bool = True

class PredictionResponse(BaseModel):
    emotion: str
    confidence: float
    distribution: List[float]
    processing_time_ms: float
    timestamp: datetime
    from_cache: bool = False