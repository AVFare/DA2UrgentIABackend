"""DTO del formato comun de error (seccion 5.3)."""

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

from app.infrastructure.api.schemas.base import CamelModel

CodigoError = Literal["VALIDACION", "NO_ENCONTRADO", "LLM_RESPUESTA_INVALIDA", "LLM_TIMEOUT", "ERROR_INTERNO"]


class DetalleError(BaseModel):
    campo: str
    mensaje: str


class ErrorResponse(CamelModel):
    """Formato comun de error (seccion 5.3)."""

    codigo: CodigoError
    mensaje: str
    detalles: list[DetalleError] | None = Field(default=None, description="Solo en errores de validacion")
    timestamp: datetime
    path: str
    correlation_id: str
