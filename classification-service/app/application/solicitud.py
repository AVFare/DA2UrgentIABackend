"""Solicitud de clasificacion que llega por la cola, ya validada."""

from dataclasses import dataclass
from uuid import UUID


@dataclass(frozen=True)
class SolicitudDeClasificacion:
    solicitud_id: UUID
    ticket_id: UUID
    titulo: str
    descripcion: str
    correlation_id: str
