from fastapi import APIRouter, Body, Depends
from pymongo.database import Database

from app.db import get_db
from app.schemas.error import OutputError
from app.schemas.evento import InputEvento, OutputEventoRecibido
from app.services.evento_service import EventoService

router = APIRouter(tags=["eventos"])


@router.post(
    "/api/eventos",
    status_code=202,
    responses={400: {"model": OutputError, "description": "El sobre no cumple el esquema"}},
)
def recibir_evento(evento: InputEvento = Body(...), db: Database = Depends(get_db)) -> OutputEventoRecibido:
    """Recibe un evento de ticket-service (solo red interna). Es idempotente por eventId:
    responde PROCESADO, DUPLICADO o IGNORADO."""
    resultado = EventoService(db).procesar(evento.model_dump(mode="json", by_alias=True))
    return OutputEventoRecibido(event_id=evento.event_id, resultado=resultado)
