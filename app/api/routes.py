from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from typing import Dict, Any, Optional
from app.api.schemas import EmotionRequest, EmotionResponse, EmotionAnalysisResponse, EmotionScores, EmotionResult, FaceLocation, HealthResponse, RateLimitInfo
from app.middlewares.auth import jwt_bearer
from app.middlewares.rate_limiter import rate_limiter
from app.core.real_emotion_analyzer import RealEmotionAnalyzer
from datetime import datetime
import json

router = APIRouter()
emotions_analyzer = RealEmotionAnalyzer()

emotion_analyses_db = {}

@router.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Endpoint público para verificar el estado del servicio
    """
    return HealthResponse(
        status="healthy",
        timestamp=datetime.now(),
        service="emotion-ml-service"
    )

@router.post("/analyze-emotion", response_model=EmotionAnalysisResponse)
async def analyze_emotion(
    request: Request,
    emotion_data: EmotionRequest,
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint protegido para analizar emociones en una imagen
    Requiere autenticación JWT del backend_usuarios
    Roles permitidos: PD (padre) y AD (admin)
    """
    try:
        # DEBUG: Imprimir datos recibidos
        print(f"🔍 Datos procesados del cliente:")
        print(f"  - Token data: {token_data}")
        print(f"  - image length: {len(emotion_data.image) if emotion_data.image else 'None'}")
        print(f"  - description: {emotion_data.description}")
        print(f"  - child_id: {emotion_data.child_id}")
        print(f"  - Client IP: {request.client.host}")

        # Extraer información del usuario del token
        user_id = token_data.get("user_id", 0)
        roles = token_data.get("roles", "")
        client_ip = request.client.host

        # Verificar que el usuario tenga permisos para análisis de emociones
        if not rate_limiter.has_emotion_permissions(roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No tienes permisos para acceder a este servicio"
            )

        # Verificar límite de peticiones
        rate_info = await rate_limiter.check_rate_limit(user_id, client_ip, roles)

        # Analizar la emoción
        result = await emotions_analyzer.analyze(emotion_data.image)

        # Construir la respuesta en el formato esperado por Android
        analysis_id = f"{user_id}_{datetime.now().timestamp()}"
        now = datetime.now()

        # Crear los datos de emociones
        emotions_data = result.get("emotions", {})
        emotion_scores = EmotionScores(
            angry=emotions_data.get("angry", 0.0),
            disgust=emotions_data.get("disgust", 0.0),
            fear=emotions_data.get("fear", 0.0),
            happy=emotions_data.get("happy", 0.0),
            neutral=emotions_data.get("neutral", 0.0),
            sad=emotions_data.get("sad", 0.0),
            surprise=emotions_data.get("surprise", 0.0)
        )

        # Crear el resultado de emoción
        emotion_result = EmotionResult(
            face_index=0,
            emotions=emotion_scores,
            dominant_emotion=result.get("dominant_emotion", "neutral"),
            face_location=None  # Por ahora no incluimos ubicación del rostro
        )

        # Guardar en base de datos (simulada)
        emotion_analyses_db[analysis_id] = {
            "user_id": user_id,
            "result": result,
            "timestamp": now.isoformat(),
            "description": emotion_data.description,
            "child_id": emotion_data.child_id
        }

        return EmotionAnalysisResponse(
            id=analysis_id,
            user_id=user_id,
            child_id=emotion_data.child_id,
            child_name=None,  # Podríamos obtener esto del sistema de usuarios
            image=emotion_data.image,
            description=emotion_data.description,
            results=[emotion_result],
            dominant_emotion=result.get("dominant_emotion", "neutral"),
            confidence_score=result.get("confidence", 0.0),
            created_at=now,
            updated_at=None,
            can_edit=not rate_limiter.is_admin(roles),  # Usuarios normales pueden editar
            can_delete=rate_limiter.is_admin(roles)     # Solo admins pueden eliminar
        )

    except HTTPException:
        raise
    except Exception as e:
        # En caso de error, retornar un error HTTP apropiado
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error analyzing emotion: {str(e)}"
        )

@router.put("/edit-analysis/{analysis_id}")
async def edit_analysis(
    analysis_id: str,
    updates: dict,
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint para editar un análisis existente
    Roles permitidos:
    - PD (padre): máximo 2 ediciones por día
    - AD (admin): sin límite
    """
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")

    # Verificar límite de ediciones
    edit_info = await rate_limiter.check_edit_limit(user_id, roles)

    # Verificar que el análisis existe
    if analysis_id not in emotion_analyses_db:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Análisis no encontrado"
        )

    # Si es padre, solo puede editar sus propios análisis
    if rate_limiter.is_padre(roles) and not rate_limiter.is_admin(roles):
        if emotion_analyses_db[analysis_id]["user_id"] != user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No puedes editar análisis de otros usuarios"
            )

    # Realizar la edición
    emotion_analyses_db[analysis_id].update(updates)
    emotion_analyses_db[analysis_id]["last_edited"] = datetime.now().isoformat()
    emotion_analyses_db[analysis_id]["edited_by"] = user_id

    # Incrementar contador de ediciones
    await rate_limiter.increment_edit_counter(user_id, roles)

    return {
        "success": True,
        "message": "Análisis actualizado",
        "edit_info": edit_info
    }

@router.delete("/delete-analysis/{analysis_id}")
async def delete_analysis(
    analysis_id: str,
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint para eliminar un análisis
    Solo permitido para rol AD (admin)
    """
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")

    # Solo admins pueden eliminar
    if not rate_limiter.is_admin(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Solo los administradores pueden eliminar análisis"
        )

    # Verificar que el análisis existe
    if analysis_id not in emotion_analyses_db:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Análisis no encontrado"
        )

    # Eliminar el análisis
    deleted_analysis = emotion_analyses_db.pop(analysis_id)

    return {
        "success": True,
        "message": "Análisis eliminado",
        "deleted_analysis_id": analysis_id
    }

@router.get("/my-analyses")
async def get_my_analyses(
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint para obtener los análisis del usuario actual
    Roles permitidos: PD (padre) y AD (admin)
    """
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")

    # Verificar permisos
    if not rate_limiter.has_emotion_permissions(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para ver análisis"
        )

    # Filtrar análisis del usuario
    user_analyses = {
        aid: analysis
        for aid, analysis in emotion_analyses_db.items()
        if analysis["user_id"] == user_id
    }

    return {
        "success": True,
        "count": len(user_analyses),
        "analyses": user_analyses
    }

@router.get("/all-analyses")
async def get_all_analyses(
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint para obtener todos los análisis
    Solo permitido para rol AD (admin)
    """
    roles = token_data.get("roles", "")

    # Solo admins pueden ver todos los análisis
    if not rate_limiter.is_admin(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Solo los administradores pueden ver todos los análisis"
        )

    return {
        "success": True,
        "count": len(emotion_analyses_db),
        "analyses": emotion_analyses_db
    }

@router.get("/rate-limit-status", response_model=RateLimitInfo)
async def get_rate_limit_status(
    request: Request,
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint protegido para verificar el estado del límite de peticiones
    """
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    client_ip = request.client.host

    rate_info = await rate_limiter.check_rate_limit(user_id, client_ip, roles)
    return RateLimitInfo(**rate_info)

@router.get("/edit-limit-status")
async def get_edit_limit_status(
    token_data: Dict[str, Any] = Depends(jwt_bearer)
):
    """
    Endpoint para verificar el estado del límite de ediciones
    """
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")

    try:
        edit_info = await rate_limiter.check_edit_limit(user_id, roles)
        return {
            "success": True,
            **edit_info
        }
    except HTTPException as e:
        return {
            "success": False,
            "error": e.detail
        }