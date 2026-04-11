from datetime import datetime
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from app.api.schemas import (
    AnalysesListResponse,
    DeleteResponse,
    EditAnalysisResponse,
    EmotionAnalysisResponse,
    EmotionRequest,
    EmotionResponse,
    EmotionResult,
    EmotionScores,
    HealthResponse,
    RateLimitInfo,
    SessionAnalysesRequest,
    SessionAnalysesResponse,
)
from app.core.real_emotion_analyzer import RealEmotionAnalyzer
from app.middlewares.auth import jwt_bearer
from app.middlewares.rate_limiter import rate_limiter
from app.services.analysis_store import analysis_store

router = APIRouter()
emotions_analyzer = RealEmotionAnalyzer()


def _build_analysis_response(
    *,
    analysis_id: str,
    user_id: int,
    child_id: Optional[int],
    session_id: Optional[int],
    image: str,
    description: Optional[str],
    result: Dict[str, Any],
    created_at: datetime,
) -> EmotionAnalysisResponse:
    emotions_data = result.get("emotions", {})
    emotion_scores = EmotionScores(
        angry=emotions_data.get("angry", 0.0),
        disgust=emotions_data.get("disgust", 0.0),
        fear=emotions_data.get("fear", 0.0),
        happy=emotions_data.get("happy", 0.0),
        neutral=emotions_data.get("neutral", 0.0),
        sad=emotions_data.get("sad", 0.0),
        surprise=emotions_data.get("surprise", 0.0),
    )
    emotion_result = EmotionResult(
        face_index=0,
        emotions=emotion_scores,
        dominant_emotion=result.get("dominant_emotion", "neutral"),
        face_location=None,
    )
    return analysis_store.create_analysis(
        analysis_id=analysis_id,
        user_id=user_id,
        child_id=child_id,
        session_id=session_id,
        image=image,
        description=description,
        results=[emotion_result.dict()],
        dominant_emotion=result.get("dominant_emotion", "neutral"),
        confidence_score=result.get("confidence", 0.0),
        created_at=created_at,
    )


def _ensure_analysis_access(
    analysis: EmotionAnalysisResponse,
    requester_id: int,
    roles: str,
) -> None:
    if rate_limiter.is_admin(roles) or rate_limiter.is_terapeuta(roles):
        return
    if analysis.user_id != requester_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para acceder a este análisis",
        )


@router.get("/health", response_model=HealthResponse)
async def health_check():
    return HealthResponse(
        status="healthy",
        timestamp=datetime.now(),
        service="emotion-ml-service",
    )


@router.post("/analyze-emotion", response_model=EmotionAnalysisResponse)
async def analyze_emotion(
    request: Request,
    emotion_data: EmotionRequest,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    try:
        user_id = token_data.get("user_id", 0)
        roles = token_data.get("roles", "")
        client_ip = request.client.host

        if not rate_limiter.has_emotion_permissions(roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="No tienes permisos para acceder a este servicio",
            )

        await rate_limiter.check_rate_limit(user_id, client_ip, roles)
        result = await emotions_analyzer.analyze(emotion_data.image)
        analysis_id = f"{user_id}_{datetime.now().timestamp()}"
        now = datetime.now()

        return _build_analysis_response(
            analysis_id=analysis_id,
            user_id=user_id,
            child_id=emotion_data.child_id,
            session_id=emotion_data.session_id,
            image=emotion_data.image,
            description=emotion_data.description,
            result=result,
            created_at=now,
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error analyzing emotion: {str(exc)}",
        )


@router.put("/edit-analysis/{analysis_id}", response_model=EditAnalysisResponse)
async def edit_analysis(
    analysis_id: str,
    updates: Dict[str, Any],
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")

    analysis = analysis_store.get_analysis(analysis_id)
    if analysis is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Análisis no encontrado")

    _ensure_analysis_access(analysis, user_id, roles)
    await rate_limiter.check_edit_limit(user_id, roles)

    description = updates.get("description", analysis.description)
    updated = analysis_store.update_description(analysis_id, description)
    await rate_limiter.increment_edit_counter(user_id, roles)

    return EditAnalysisResponse(
        success=True,
        message="Análisis actualizado",
        analysis=updated,
    )


@router.delete("/delete-analysis/{analysis_id}", response_model=DeleteResponse)
async def delete_analysis(
    analysis_id: str,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    roles = token_data.get("roles", "")
    if not (rate_limiter.is_admin(roles) or rate_limiter.is_terapeuta(roles)):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Solo administradores y terapeutas pueden eliminar análisis",
        )

    if not analysis_store.delete_analysis(analysis_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Análisis no encontrado")

    return DeleteResponse(success=True, message="Análisis eliminado")


@router.get("/analysis/{analysis_id}", response_model=EmotionAnalysisResponse)
async def get_analysis_by_id(
    analysis_id: str,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    analysis = analysis_store.get_analysis(analysis_id)
    if analysis is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Análisis no encontrado")
    _ensure_analysis_access(analysis, user_id, roles)
    return analysis


@router.get("/session/{session_id}/analysis", response_model=EmotionAnalysisResponse)
async def get_session_analysis(
    session_id: int,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    roles = token_data.get("roles", "")
    if not rate_limiter.has_emotion_permissions(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para ver análisis",
        )

    analysis = analysis_store.get_latest_by_session(session_id)
    if analysis is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No hay análisis para esta sesión")
    return analysis


@router.post("/sessions/analyses", response_model=SessionAnalysesResponse)
async def get_session_analyses(
    request_body: SessionAnalysesRequest,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    roles = token_data.get("roles", "")
    if not rate_limiter.has_emotion_permissions(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para ver análisis",
        )
    analyses = analysis_store.get_latest_by_sessions(request_body.session_ids)
    return SessionAnalysesResponse(analyses=analyses)


@router.get("/my-analyses", response_model=AnalysesListResponse)
async def get_my_analyses(
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    child_id: Optional[int] = Query(None),
    session_id: Optional[int] = Query(None),
    order_by: str = Query("created_at"),
    order_direction: str = Query("desc", pattern="^(asc|desc)$"),
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    if not rate_limiter.has_emotion_permissions(roles):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para ver análisis",
        )

    analyses, pagination = analysis_store.list_analyses(
        page=page,
        per_page=per_page,
        user_id=user_id,
        child_id=child_id,
        session_id=session_id,
        order_by=order_by,
        order_direction=order_direction,
    )
    return AnalysesListResponse(analyses=analyses, pagination=pagination, rate_limit=None)


@router.get("/user/{user_id}/analyses", response_model=AnalysesListResponse)
async def get_user_analyses(
    user_id: int,
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    session_id: Optional[int] = Query(None),
    order_by: str = Query("created_at"),
    order_direction: str = Query("desc", pattern="^(asc|desc)$"),
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    requester_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    if not (rate_limiter.is_admin(roles) or rate_limiter.is_terapeuta(roles) or requester_id == user_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes permisos para ver análisis de este usuario",
        )

    analyses, pagination = analysis_store.list_analyses(
        page=page,
        per_page=per_page,
        user_id=user_id,
        session_id=session_id,
        order_by=order_by,
        order_direction=order_direction,
    )
    return AnalysesListResponse(analyses=analyses, pagination=pagination, rate_limit=None)


@router.get("/all-analyses", response_model=AnalysesListResponse)
async def get_all_analyses(
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    user_id: Optional[int] = Query(None),
    child_id: Optional[int] = Query(None),
    session_id: Optional[int] = Query(None),
    order_by: str = Query("created_at"),
    order_direction: str = Query("desc", pattern="^(asc|desc)$"),
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    roles = token_data.get("roles", "")
    if not (rate_limiter.is_admin(roles) or rate_limiter.is_terapeuta(roles)):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Solo administradores y terapeutas pueden ver todos los análisis",
        )

    analyses, pagination = analysis_store.list_analyses(
        page=page,
        per_page=per_page,
        user_id=user_id,
        child_id=child_id,
        session_id=session_id,
        order_by=order_by,
        order_direction=order_direction,
    )
    return AnalysesListResponse(analyses=analyses, pagination=pagination, rate_limit=None)


@router.get("/rate-limit-status", response_model=RateLimitInfo)
async def get_rate_limit_status(
    request: Request,
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    client_ip = request.client.host
    rate_info = await rate_limiter.check_rate_limit(user_id, client_ip, roles)
    return RateLimitInfo(**rate_info)


@router.get("/edit-limit-status")
async def get_edit_limit_status(
    token_data: Dict[str, Any] = Depends(jwt_bearer),
):
    user_id = token_data.get("user_id", 0)
    roles = token_data.get("roles", "")
    try:
        edit_info = await rate_limiter.check_edit_limit(user_id, roles)
        return {"success": True, **edit_info}
    except HTTPException as exc:
        return {"success": False, "error": exc.detail}
