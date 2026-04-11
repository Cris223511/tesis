from __future__ import annotations

from contextlib import contextmanager
from math import ceil
from typing import Dict, Iterable, List, Optional, Tuple

from sqlalchemy import JSON, Column, DateTime, Float, Integer, String, Text, create_engine, desc, func
from sqlalchemy.orm import Session, declarative_base, sessionmaker
from datetime import datetime

from app.api.schemas import EmotionAnalysisResponse, EmotionResult, PaginationInfo
from app.config import get_settings

settings = get_settings()
Base = declarative_base()

VISIBLE_EMOTIONS = ("happy", "sad", "angry", "surprise", "disgust")


def _normalize_visible_emotion(dominant_emotion: str, results: List[dict]) -> str:
    lowered = (dominant_emotion or "").lower()
    if lowered in VISIBLE_EMOTIONS:
        return lowered

    if not results:
        return "disgust"

    emotions = (results[0] or {}).get("emotions", {})
    if not isinstance(emotions, dict):
        return "disgust"

    best_label = None
    best_score = -1.0
    for label in VISIBLE_EMOTIONS:
        score = float(emotions.get(label, 0.0) or 0.0)
        if score > best_score:
            best_label = label
            best_score = score

    return best_label or "disgust"


class EmotionAnalysisRecord(Base):
    __tablename__ = "emotion_analyses"

    id = Column(String(64), primary_key=True)
    user_id = Column(Integer, nullable=False, index=True)
    child_id = Column(Integer, nullable=True, index=True)
    session_id = Column(Integer, nullable=True, index=True)
    image = Column(Text, nullable=False)
    description = Column(Text, nullable=True)
    results = Column(JSON, nullable=False)
    dominant_emotion = Column(String(50), nullable=False)
    confidence_score = Column(Float, nullable=False)
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow, index=True)
    updated_at = Column(DateTime, nullable=True)


class AnalysisStore:
    def __init__(self) -> None:
        database_url = (
            f"mysql+pymysql://{settings.db_user}:{settings.db_password}"
            f"@{settings.db_host}:{settings.db_port}/{settings.db_name}?charset=utf8mb4"
        )
        self.engine = create_engine(database_url, pool_pre_ping=True)
        self.session_factory = sessionmaker(bind=self.engine, autoflush=False, autocommit=False)

    def init_schema(self) -> None:
        Base.metadata.create_all(self.engine)

    @contextmanager
    def session_scope(self) -> Iterable[Session]:
        session = self.session_factory()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()

    def create_analysis(
        self,
        analysis_id: str,
        user_id: int,
        child_id: Optional[int],
        session_id: Optional[int],
        image: str,
        description: Optional[str],
        results: List[dict],
        dominant_emotion: str,
        confidence_score: float,
        created_at: datetime,
    ) -> EmotionAnalysisResponse:
        normalized_dominant = _normalize_visible_emotion(dominant_emotion, results)
        normalized_results = []
        for result in results:
            normalized_result = dict(result)
            normalized_result["dominant_emotion"] = _normalize_visible_emotion(
                normalized_result.get("dominant_emotion", normalized_dominant),
                [normalized_result],
            )
            normalized_results.append(normalized_result)

        with self.session_scope() as session:
            record = EmotionAnalysisRecord(
                id=analysis_id,
                user_id=user_id,
                child_id=child_id,
                session_id=session_id,
                image=image,
                description=description,
                results=normalized_results,
                dominant_emotion=normalized_dominant,
                confidence_score=confidence_score,
                created_at=created_at,
            )
            session.add(record)
            session.flush()
            session.refresh(record)
            return self._to_response(record)

    def update_description(self, analysis_id: str, description: str) -> Optional[EmotionAnalysisResponse]:
        with self.session_scope() as session:
            record = session.get(EmotionAnalysisRecord, analysis_id)
            if record is None:
                return None
            record.description = description
            record.updated_at = datetime.utcnow()
            session.add(record)
            session.flush()
            session.refresh(record)
            return self._to_response(record)

    def delete_analysis(self, analysis_id: str) -> bool:
        with self.session_scope() as session:
            record = session.get(EmotionAnalysisRecord, analysis_id)
            if record is None:
                return False
            session.delete(record)
            return True

    def get_analysis(self, analysis_id: str) -> Optional[EmotionAnalysisResponse]:
        with self.session_scope() as session:
            record = session.get(EmotionAnalysisRecord, analysis_id)
            return self._to_response(record) if record else None

    def get_latest_by_session(self, session_id: int) -> Optional[EmotionAnalysisResponse]:
        with self.session_scope() as session:
            record = (
                session.query(EmotionAnalysisRecord)
                .filter(EmotionAnalysisRecord.session_id == session_id)
                .order_by(desc(EmotionAnalysisRecord.created_at))
                .first()
            )
            return self._to_response(record) if record else None

    def get_latest_by_sessions(self, session_ids: List[int]) -> List[EmotionAnalysisResponse]:
        if not session_ids:
            return []

        latest_ids: Dict[int, str] = {}
        with self.session_scope() as session:
            records = (
                session.query(EmotionAnalysisRecord)
                .filter(EmotionAnalysisRecord.session_id.in_(session_ids))
                .order_by(EmotionAnalysisRecord.session_id.asc(), desc(EmotionAnalysisRecord.created_at))
                .all()
            )
            for record in records:
                if record.session_id is not None and record.session_id not in latest_ids:
                    latest_ids[record.session_id] = record.id

            if not latest_ids:
                return []

            selected = (
                session.query(EmotionAnalysisRecord)
                .filter(EmotionAnalysisRecord.id.in_(list(latest_ids.values())))
                .all()
            )
            return [self._to_response(record) for record in selected]

    def list_analyses(
        self,
        page: int,
        per_page: int,
        user_id: Optional[int] = None,
        child_id: Optional[int] = None,
        session_id: Optional[int] = None,
        order_by: str = "created_at",
        order_direction: str = "desc",
    ) -> Tuple[List[EmotionAnalysisResponse], PaginationInfo]:
        with self.session_scope() as session:
            query = session.query(EmotionAnalysisRecord)

            if user_id is not None:
                query = query.filter(EmotionAnalysisRecord.user_id == user_id)
            if child_id is not None:
                query = query.filter(EmotionAnalysisRecord.child_id == child_id)
            if session_id is not None:
                query = query.filter(EmotionAnalysisRecord.session_id == session_id)

            sortable = {
                "created_at": EmotionAnalysisRecord.created_at,
                "updated_at": EmotionAnalysisRecord.updated_at,
                "confidence_score": EmotionAnalysisRecord.confidence_score,
            }
            order_column = sortable.get(order_by, EmotionAnalysisRecord.created_at)
            query = query.order_by(order_column.asc() if order_direction == "asc" else order_column.desc())

            total = query.with_entities(func.count(EmotionAnalysisRecord.id)).scalar() or 0
            records = query.offset((page - 1) * per_page).limit(per_page).all()
            total_pages = max(1, ceil(total / per_page)) if per_page > 0 else 1

            pagination = PaginationInfo(
                page=page,
                per_page=per_page,
                total=total,
                total_pages=total_pages,
                has_next=page < total_pages,
                has_prev=page > 1,
            )
            return [self._to_response(record) for record in records], pagination

    def _to_response(self, record: EmotionAnalysisRecord) -> EmotionAnalysisResponse:
        raw_results = record.results or []
        normalized_results = []
        for item in raw_results:
            normalized_item = dict(item)
            normalized_item["dominant_emotion"] = _normalize_visible_emotion(
                normalized_item.get("dominant_emotion", record.dominant_emotion),
                [normalized_item],
            )
            normalized_results.append(normalized_item)

        normalized_dominant = _normalize_visible_emotion(record.dominant_emotion, normalized_results)
        results = [EmotionResult(**item) for item in normalized_results]
        return EmotionAnalysisResponse(
            id=record.id,
            user_id=record.user_id,
            child_id=record.child_id,
            session_id=record.session_id,
            child_name=None,
            image=record.image,
            description=record.description,
            results=results,
            dominant_emotion=normalized_dominant,
            confidence_score=record.confidence_score,
            created_at=record.created_at,
            updated_at=record.updated_at,
            can_edit=True,
            can_delete=False,
        )


analysis_store = AnalysisStore()
