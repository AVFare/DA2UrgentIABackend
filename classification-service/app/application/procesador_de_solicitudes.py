"""Caso de uso de atender una solicitud de clasificacion que llega por la cola.

Clasifica con la ClasificacionFacade y publica el resultado. Es idempotente: una solicitud
que ya se proceso no vuelve a consultar al LLM, solo se publica otra vez lo guardado.
"""

import logging
from dataclasses import dataclass
from uuid import UUID

from app.application.clasificacion_facade import ClasificacionFacade
from app.application.ports.clasificacion_repository import ClasificacionRepository
from app.application.ports.publicador_clasificaciones import PublicadorDeClasificaciones

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class SolicitudDeClasificacion:
    solicitud_id: UUID
    ticket_id: UUID
    titulo: str
    descripcion: str
    correlation_id: str


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
            registro = await self._facade.clasificar(
                solicitud.ticket_id, solicitud.titulo, solicitud.descripcion, solicitud_id=solicitud.solicitud_id
            )
        await self._publicador.publicar(registro, solicitud.correlation_id)
