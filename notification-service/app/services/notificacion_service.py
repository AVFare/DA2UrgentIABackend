import logging
from datetime import UTC, datetime
from typing import Any

from pymongo.database import Database

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
