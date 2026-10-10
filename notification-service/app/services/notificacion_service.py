import logging
import math
from datetime import UTC, datetime
from typing import Any

from pymongo.database import Database

from app.exceptions.api_exception import ApiException
from app.repositories.notificacion_repository import NotificacionRepository
from app.services.canales import FALLIDA, obtener_canal
from app.services.plantillas import PlantillaFactory

logger = logging.getLogger("notificaciones")


class NotificacionService:
    def __init__(self, db: Database):
        self.repository = NotificacionRepository(db)

    def crear_desde_evento(self, evento: dict[str, Any], destinatario: str, canal: str) -> dict[str, Any]:
        """Arma la notificacion con su plantilla, la manda por el canal y la guarda.

        Si el canal falla, la notificacion se guarda igual con estado FALLIDA.
        """
        asunto, mensaje = PlantillaFactory.para(evento["eventType"]).generar(evento)
        notificacion = {
            "eventId": evento["eventId"],
            "ticketId": evento["payload"]["ticket"]["ticketId"],
            "tipoEvento": evento["eventType"],
            "destinatario": destinatario,
            "canal": canal,
            "asunto": asunto,
            "mensaje": mensaje,
            "fecha": datetime.now(UTC).replace(microsecond=0),
        }
        try:
            notificacion["estado"] = obtener_canal(canal).enviar(notificacion)
        except Exception:
            logger.exception("No se pudo enviar la notificacion", extra={"eventId": evento["eventId"], "canal": canal})
            notificacion["estado"] = FALLIDA
        return self.repository.insert(notificacion)

    def listar(self, filtros: dict[str, Any]) -> dict[str, Any]:
        """filtros: { page, size, ticketId?, destinatario? }. Devuelve la pagina del contrato."""
        page, size = filtros["page"], filtros["size"]
        contenido, total = self.repository.buscar(filtros.get("ticketId"), filtros.get("destinatario"), page, size)
        return {
            "content": contenido,
            "page": page,
            "size": size,
            "totalElements": total,
            "totalPages": math.ceil(total / size) if total else 0,
        }

    def obtener(self, id: str) -> dict[str, Any]:
        notificacion = self.repository.find_by_id(id)
        if notificacion is None:
            raise ApiException(status=404, codigo="NO_ENCONTRADO", mensaje=f"No existe la notificación {id}")
        return notificacion
