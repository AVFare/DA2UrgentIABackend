from typing import Annotated

from fastapi import APIRouter, Depends, Query
from pymongo.database import Database

from app.db import get_db
from app.schemas.error import OutputError
from app.schemas.notificacion import InputFiltroNotificaciones, OutputNotificacion
from app.schemas.pagination import OutputPagination
from app.services.notificacion_service import NotificacionService

router = APIRouter(tags=["notificaciones"])


@router.get("/api/notificaciones", responses={400: {"model": OutputError}})
def listar_notificaciones(
    filtros: Annotated[InputFiltroNotificaciones, Query()], db: Database = Depends(get_db)
) -> OutputPagination[OutputNotificacion]:
    """Lista notificaciones, de la más nueva a la más vieja, filtrando por ticket y/o destinatario."""
    pagina = NotificacionService(db).listar(filtros.model_dump(mode="json", by_alias=True))
    return OutputPagination[OutputNotificacion].model_validate(pagina)


@router.get("/api/notificaciones/{id}", responses={404: {"model": OutputError}})
def obtener_notificacion(id: str, db: Database = Depends(get_db)) -> OutputNotificacion:
    """Devuelve una notificación por id."""
    return OutputNotificacion.model_validate(NotificacionService(db).obtener(id))
