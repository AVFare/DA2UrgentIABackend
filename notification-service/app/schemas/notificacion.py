from uuid import UUID

from pydantic import Field

from app.schemas.camel_model import CamelModel, FechaUtc
from app.schemas.enums import CanalNotificacion, EstadoNotificacion, TipoEvento
from app.schemas.pagination import InputPagination


class InputFiltroNotificaciones(InputPagination):
    ticket_id: UUID | None = Field(default=None, description="Filtra por ticket")
    destinatario: str | None = Field(
        default=None, description="GRUPO:GUARDIA o USUARIO:{uuid}", examples=["GRUPO:GUARDIA"]
    )


class OutputNotificacion(CamelModel):
    id: str = Field(examples=["66f7c3b2e4b0a1b2c3d4e5f7"])
    event_id: str = Field(examples=["3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"])
    ticket_id: str = Field(examples=["7c9e6679-7425-40de-944b-e07fc1f90ae7"])
    tipo_evento: TipoEvento
    destinatario: str = Field(examples=["GRUPO:GUARDIA"])
    canal: CanalNotificacion
    asunto: str = Field(examples=["[P1] Ticket escalado: No puede ingresar nadie"])
    mensaje: str = Field(examples=["El ticket fue escalado. Motivo: Prioridad P1. SLA: 2026-10-05T15:03:11Z"])
    estado: EstadoNotificacion
    fecha: FechaUtc = Field(examples=["2026-10-05T14:03:12Z"])
