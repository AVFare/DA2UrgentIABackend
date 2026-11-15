"""Caso de uso de atender una solicitud de clasificacion que llega por la cola.

Clasifica con la ClasificacionFacade y publica el resultado. Si el LLM falla, responde con el
error en lugar de reintentar: quien pidio la clasificacion decide si la vuelve a pedir.

Es idempotente: una solicitud que ya se proceso no vuelve a consultar al LLM, solo se publica
otra vez lo guardado. Cualquier otro error (base o broker caidos) se propaga para que la
solicitud se reintente.
"""

import logging

from app.application.clasificacion_facade import ClasificacionFacade
from app.application.errors import LlmError
from app.application.ports.clasificacion_repository import ClasificacionRepository
from app.application.ports.publicador_clasificaciones import PublicadorDeClasificaciones
from app.application.solicitud import SolicitudDeClasificacion

log = logging.getLogger(__name__)

__all__ = ["ProcesadorDeSolicitudes", "SolicitudDeClasificacion"]


class ProcesadorDeSolicitudes:
    def __init__(self, facade: ClasificacionFacade, repositorio: ClasificacionRepository,
                 publicador: PublicadorDeClasificaciones) -> None:
        self._facade = facade
        self._repositorio = repositorio
        self._publicador = publicador

    async def procesar(self, solicitud: SolicitudDeClasificacion) -> None:
        registro = await self._repositorio.buscar_por_solicitud(solicitud.solicitud_id)
        if registro:
            log.info("La solicitud %s ya estaba clasificada; se publica otra vez", solicitud.solicitud_id)
        else:
            try:
                registro = await self._facade.clasificar(
                    solicitud.ticket_id, solicitud.titulo, solicitud.descripcion, solicitud_id=solicitud.solicitud_id
                )
            except LlmError as error:
                log.warning("No se pudo clasificar la solicitud %s (%s); se responde con el error",
                            solicitud.solicitud_id, error)
                await self._publicador.publicar_error(solicitud, error)
                return
        await self._publicador.publicar_clasificacion(solicitud, registro)
