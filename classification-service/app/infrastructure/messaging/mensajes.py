"""Mensajes de clasificacion, con el sobre estandar de los eventos.

Contrato: contracts/events/clasificacion-events.schema.json y contracts/messaging/.
"""

from datetime import UTC, datetime
from typing import Literal
from uuid import NAMESPACE_URL, UUID, uuid5

from pydantic import ConfigDict, Field

from app.application.errors import LlmError, LlmNoDisponibleError, LlmTimeoutError
from app.application.solicitud import SolicitudDeClasificacion
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


class ClasificacionDelMensaje(ClasificacionResponse):
    """Los campos de ClasificacionResponse; el ticketId no se repite porque ya va en el payload."""

    ticket_id: UUID = Field(exclude=True)


CodigoDeError = Literal["LLM_TIMEOUT", "LLM_RESPUESTA_INVALIDA", "ERROR_INTERNO"]


class ErrorDeClasificacion(Mensaje):
    codigo: CodigoDeError
    mensaje: str = Field(min_length=1)

    @classmethod
    def desde_error(cls, error: LlmError) -> "ErrorDeClasificacion":
        """Mismos codigos y textos que el REST (infrastructure/api/errors.py)."""
        if isinstance(error, LlmTimeoutError):
            return cls(codigo="LLM_TIMEOUT", mensaje=str(error))
        if isinstance(error, LlmNoDisponibleError):
            return cls(codigo="LLM_RESPUESTA_INVALIDA", mensaje=f"No se pudo consultar al LLM: {error}")
        return cls(codigo="LLM_RESPUESTA_INVALIDA", mensaje=f"La respuesta del LLM no es valida: {error}")


class PayloadGenerada(Mensaje):
    ticket_id: UUID
    resultado: Literal["CLASIFICADO", "ERROR"]
    clasificacion: ClasificacionDelMensaje | None = None
    error: ErrorDeClasificacion | None = None


class ClasificacionGenerada(Mensaje):
    event_id: UUID
    event_type: Literal["ClasificacionGenerada"] = "ClasificacionGenerada"
    version: Literal[1] = VERSION_DEL_SOBRE
    occurred_at: datetime
    correlation_id: str
    source: Literal["classification-service"] = SERVICE_NAME
    payload: PayloadGenerada

    @classmethod
    def clasificado(cls, solicitud: SolicitudDeClasificacion,
                    registro: RegistroClasificacion) -> "ClasificacionGenerada":
        return cls(
            event_id=_event_id(solicitud, "clasificado"),
            occurred_at=registro.fecha,
            correlation_id=solicitud.correlation_id,
            payload=PayloadGenerada(
                ticket_id=solicitud.ticket_id,
                resultado="CLASIFICADO",
                clasificacion=ClasificacionDelMensaje.model_validate(
                    ClasificacionResponse.desde_registro(registro), from_attributes=True
                ),
            ),
        )

    @classmethod
    def con_error(cls, solicitud: SolicitudDeClasificacion, error: LlmError) -> "ClasificacionGenerada":
        return cls(
            event_id=_event_id(solicitud, "error"),
            occurred_at=datetime.now(UTC),
            correlation_id=solicitud.correlation_id,
            payload=PayloadGenerada(
                ticket_id=solicitud.ticket_id, resultado="ERROR", error=ErrorDeClasificacion.desde_error(error)
            ),
        )

    def a_json(self) -> bytes:
        return self.model_dump_json(by_alias=True, exclude_none=True).encode()


def _event_id(solicitud: SolicitudDeClasificacion, resultado: str) -> UUID:
    """Sale de la solicitud: si se procesa dos veces, la respuesta lleva el mismo eventId."""
    return uuid5(NAMESPACE_URL, f"urgentia:clasificacion-generada:{resultado}:{solicitud.solicitud_id}")
