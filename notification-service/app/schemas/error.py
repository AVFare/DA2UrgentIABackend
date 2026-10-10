"""Formato comun de error (contexto, seccion 5.3). Se usa para documentar las respuestas en Swagger."""

from pydantic import Field

from app.schemas.camel_model import CamelModel


class OutputDetalleError(CamelModel):
    campo: str = Field(examples=["payload.ticket.titulo"])
    mensaje: str = Field(examples=["Field required"])


class OutputError(CamelModel):
    codigo: str = Field(examples=["VALIDACION"])
    mensaje: str = Field(examples=["La solicitud no es válida"])
    detalles: list[OutputDetalleError] | None = None
    timestamp: str = Field(examples=["2026-10-05T14:03:11Z"])
    path: str = Field(examples=["/api/eventos"])
    correlation_id: str | None = Field(default=None, examples=["a8e1b2c3-0000-4000-8000-000000000001"])
