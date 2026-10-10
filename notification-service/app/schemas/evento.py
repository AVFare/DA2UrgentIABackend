"""Sobre de los eventos de ticket (contexto, seccion 10.1 y contracts/events/ticket-events.schema.json)."""

from uuid import UUID

from pydantic import Field

from app.schemas.camel_model import CamelModel, FechaUtc
from app.schemas.enums import (
    Categoria,
    EstadoTicket,
    ModuloAfectado,
    Prioridad,
    ResultadoEvento,
    TipoEvento,
)

EJEMPLO_SOBRE = {
    "eventId": "3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "eventType": "TicketEscalado",
    "version": 1,
    "occurredAt": "2026-10-05T14:03:12Z",
    "correlationId": "a8e1b2c3-0000-4000-8000-000000000001",
    "source": "ticket-service",
    "payload": {
        "ticket": {
            "ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "titulo": "No puede ingresar nadie",
            "estado": "ESCALADO",
            "prioridad": "P1",
            "categoria": "INCIDENTE",
            "moduloAfectado": "AUTENTICACION",
            "solicitanteId": "b1c2d3e4-0000-4000-8000-000000000004",
            "agenteAsignadoId": None,
            "fechaCreacion": "2026-10-05T14:03:10Z",
            "fechaLimiteSla": "2026-10-05T15:03:11Z",
        },
        "estadoAnterior": "CLASIFICADO",
        "motivo": "Prioridad P1",
    },
}


class InputTicket(CamelModel):
    """Foto del ticket despues del cambio. No trae la descripcion."""

    ticket_id: UUID
    titulo: str = Field(min_length=5, max_length=120)
    estado: EstadoTicket
    prioridad: Prioridad | None = None
    categoria: Categoria | None = None
    modulo_afectado: ModuloAfectado | None = None
    solicitante_id: UUID
    agente_asignado_id: UUID | None = None
    fecha_creacion: FechaUtc
    fecha_limite_sla: FechaUtc | None = None


class InputPayload(CamelModel):
    ticket: InputTicket
    estado_anterior: EstadoTicket | None = None
    motivo: str | None = None


class InputEvento(CamelModel):
    """Sobre del evento. Un eventType fuera del enum responde 400, como pide el contrato."""

    model_config = CamelModel.model_config | {"json_schema_extra": {"examples": [EJEMPLO_SOBRE]}}

    event_id: UUID
    event_type: TipoEvento
    version: int = Field(ge=1)
    occurred_at: FechaUtc
    correlation_id: str = Field(min_length=1)
    source: str = Field(min_length=1)
    payload: InputPayload


class OutputEventoRecibido(CamelModel):
    event_id: UUID = Field(examples=["3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"])
    resultado: ResultadoEvento
