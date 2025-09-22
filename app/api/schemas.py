from pydantic import BaseModel
from typing import Optional, List, Dict
from datetime import datetime

class EmotionRequest(BaseModel):
    image: str  # Base64 encoded image - matches Android field name
    description: Optional[str] = None
    child_id: Optional[int] = None

# Emotion scores matching Android format
class EmotionScores(BaseModel):
    angry: float
    disgust: float
    fear: float
    happy: float
    neutral: float
    sad: float
    surprise: float

# Face location data
class FaceLocation(BaseModel):
    x: int
    y: int
    width: int
    height: int

# Individual emotion result
class EmotionResult(BaseModel):
    face_index: int
    emotions: EmotionScores
    dominant_emotion: str
    face_location: Optional[FaceLocation] = None

# Main response matching Android EmotionAnalysisResponse
class EmotionAnalysisResponse(BaseModel):
    id: str
    user_id: int
    child_id: Optional[int] = None
    child_name: Optional[str] = None
    image: str
    description: Optional[str] = None
    results: List[EmotionResult]
    dominant_emotion: str
    confidence_score: float
    created_at: datetime
    updated_at: Optional[datetime] = None
    can_edit: bool = True
    can_delete: bool = False

# Legacy response for compatibility
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