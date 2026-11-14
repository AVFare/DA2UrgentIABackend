"""Decide que hacer con cada mensaje de la cola de solicitudes.

Valida el mensaje contra el contrato (hace de Anti-Corruption Layer de la cola) y lo pasa al
ProcesadorDeSolicitudes. No conoce RabbitMQ: devuelve un Resultado y el consumidor lo traduce
a ack o reject.
"""

import logging
from enum import Enum

from pydantic import ValidationError

from app.application.procesador_de_solicitudes import ProcesadorDeSolicitudes
from app.infrastructure.api.correlation import correlation_id_actual
from app.infrastructure.messaging.mensajes import ClasificacionSolicitada

log = logging.getLogger(__name__)


class Resultado(Enum):
    PROCESADA = "procesada"
    # El mensaje no cumple el contrato: reintentarlo no lo arregla.
    DESCARTADA = "descartada"
    # Fallo algo transitorio (base o broker): se reintenta.
    REINTENTAR = "reintentar"


class ManejadorDeSolicitudes:
    def __init__(self, procesador: ProcesadorDeSolicitudes) -> None:
        self._procesador = procesador

    async def manejar(self, cuerpo: bytes) -> Resultado:
        try:
            mensaje = ClasificacionSolicitada.model_validate_json(cuerpo)
        except ValidationError as error:
            campos = ", ".join(".".join(str(parte) for parte in detalle["loc"]) for detalle in error.errors()[:5])
            log.warning("Solicitud descartada: no cumple el contrato (%s)", campos or "JSON invalido")
            return Resultado.DESCARTADA

        token = correlation_id_actual.set(mensaje.correlation_id)
        try:
            await self._procesador.procesar(mensaje.a_solicitud())
            return Resultado.PROCESADA
        except Exception:
            log.exception("Error al procesar la solicitud %s; se reintenta", mensaje.event_id)
            return Resultado.REINTENTAR
        finally:
            correlation_id_actual.reset(token)
