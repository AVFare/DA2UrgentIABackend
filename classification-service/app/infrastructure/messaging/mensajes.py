"""Mensajes de clasificacion, con el sobre estandar de los eventos (contracts/messaging/clasificacion.md)."""

from datetime import datetime
from typing import Literal
from uuid import NAMESPACE_URL, UUID, uuid5

from pydantic import ConfigDict, Field

from app.application.procesador_de_solicitudes import SolicitudDeClasificacion
from app.config import SERVICE_NAME
from app.domain.models import RegistroClasificacion
from app.infrastructure.api.schemas.base import CamelModel
from app.infrastructure.api.schemas.clasificacion import ClasificacionResponse

VERSION_DEL_SOBRE = 1


class Mensaje(CamelModel):
    model_config = ConfigDict(extra="forbid")


class PayloadSolicitada(Mensaje):
    ticket_id: UUID
    titulo: str = Field(min_length=5, max_length=120)
    descripcion: str = Field(min_length=10, max_length=2000)


class ClasificacionSolicitada(Mensaje):
    event_id: UUID
    event_type: Literal["ClasificacionSolicitada"]
    version: Literal[1]
    occurred_at: datetime
    correlation_id: str = Field(min_length=1)
    source: str = Field(min_length=1)
    payload: PayloadSolicitada

    def a_solicitud(self) -> SolicitudDeClasificacion:
        return SolicitudDeClasificacion(
            solicitud_id=self.event_id,
            ticket_id=self.payload.ticket_id,
            titulo=self.payload.titulo,
            descripcion=self.payload.descripcion,
            correlation_id=self.correlation_id,
        )


class PayloadGenerada(Mensaje):
    solicitud_id: UUID
    clasificacion: ClasificacionResponse


class ClasificacionGenerada(Mensaje):
    event_id: UUID
    event_type: Literal["ClasificacionGenerada"] = "ClasificacionGenerada"
    version: Literal[1] = VERSION_DEL_SOBRE
    occurred_at: datetime
    correlation_id: str
    source: Literal["classification-service"] = SERVICE_NAME
    payload: PayloadGenerada

    @classmethod
    def desde_registro(cls, registro: RegistroClasificacion, correlation_id: str) -> "ClasificacionGenerada":
        if registro.solicitud_id is None:
            raise ValueError("El registro no viene de una solicitud")
        return cls(
            # Sale de la solicitud: si se procesa dos veces, la respuesta lleva el mismo eventId.
            event_id=uuid5(NAMESPACE_URL, f"urgentia:clasificacion-generada:{registro.solicitud_id}"),
            occurred_at=registro.fecha,
            correlation_id=correlation_id,
            payload=PayloadGenerada(
                solicitud_id=registro.solicitud_id,
                clasificacion=ClasificacionResponse.desde_registro(registro),
            ),
        )

    def a_json(self) -> bytes:
        return self.model_dump_json(by_alias=True).encode()
