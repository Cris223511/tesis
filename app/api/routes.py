from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from typing import Dict, Any, Optional
from app.api.schemas import EmotionRequest, EmotionResponse, HealthResponse, RateLimitInfo
from app.middlewares.auth import jwt_bearer
from app.middlewares.rate_limiter import rate_limiter
from app.core.emotion_analyzer import EmotionAnalyzer
from datetime import datetime
import json

router = APIRouter()
emotions_analyzer = EmotionAnalyzer()

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

@router.post("/analyze-emotion", response_model=EmotionResponse)
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
        # Extraer información del usuario del token
        user_id = token_data.get("user_id", 0)
        roles = token_data.get("roles", "")
        client_ip = request.client.host

        # Verificar que el usuario tenga rol PD o AD
        if not (rate_limiter.is_padre(roles) or rate_limiter.is_admin(roles)):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No tienes permisos para acceder a este servicio"
            )

        # Verificar límite de peticiones
        rate_info = await rate_limiter.check_rate_limit(user_id, client_ip, roles)

        # Analizar la emoción
        result = await emotions_analyzer.analyze(emotion_data.image_base64)

        # Guardar en base de datos (simulada)
        analysis_id = f"{user_id}_{datetime.now().timestamp()}"
        emotion_analyses_db[analysis_id] = {
            "user_id": user_id,
            "result": result,
            "timestamp": datetime.now().isoformat(),
            "metadata": emotion_data.metadata
        }

        return EmotionResponse(
            success=True,
            emotions=result.get("emotions"),
            dominant_emotion=result.get("dominant_emotion"),
            confidence=result.get("confidence"),
            timestamp=datetime.now()
        )

    except HTTPException:
        raise
    except Exception as e:
        return EmotionResponse(
            success=False,
            error=str(e),
            timestamp=datetime.now()
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
    if not (rate_limiter.is_padre(roles) or rate_limiter.is_admin(roles)):
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