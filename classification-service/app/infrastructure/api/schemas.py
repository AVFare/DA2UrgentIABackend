"""Modelos Pydantic de request y response de la API (contrato de la seccion 9.3).

Los atributos van en snake_case y se serializan en camelCase, como pide la seccion 5.
"""

from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.domain.enums import Categoria, Impacto, ModuloAfectado, Urgencia


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ClasificarRequest(CamelModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                    "titulo": "No puede ingresar nadie",
                    "descripcion": "Producción caída, todos los usuarios bloqueados en el login desde las 9",
                }
            ]
        }
    )

    ticket_id: UUID
    titulo: str = Field(min_length=5, max_length=120)
    descripcion: str = Field(min_length=10, max_length=2000)


class ClasificacionResponse(CamelModel):
    """Lo que sugiere la IA. No incluye la prioridad."""

    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "id": "66f7c2a1e4b0a1b2c3d4e5f6",
                    "ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                    "categoria": "INCIDENTE",
                    "urgencia": "ALTA",
                    "impacto": "ALTO",
                    "moduloAfectado": "AUTENTICACION",
                    "requiereEscalamiento": True,
                    "confianza": 0.93,
                    "justificacion": "Caída total del login en producción que afecta a todos los usuarios",
                    "proveedor": "mock",
                    "modelo": "mock-v1",
                    "versionPrompt": "v1",
                    "latenciaMs": 812,
                    "fecha": "2026-10-05T14:03:11Z",
                }
            ]
        }
    )

    id: str = Field(description="ObjectId de MongoDB como string")
    ticket_id: UUID
    categoria: Categoria
    urgencia: Urgencia
    impacto: Impacto
    modulo_afectado: ModuloAfectado
    requiere_escalamiento: bool = Field(
        description="true si el caso parece critico: produccion caida, muchos usuarios bloqueados, "
        "perdida de datos o riesgo de seguridad"
    )
    confianza: float = Field(ge=0, le=1, description="Que tan segura esta la IA de la clasificacion")
    justificacion: str = Field(max_length=300)
    proveedor: str = Field(description="Valor de LLM_PROVIDER con el que se clasifico")
    modelo: str
    version_prompt: str
    latencia_ms: int = Field(description="Tiempo total de la llamada al LLM, reintento incluido")
    fecha: datetime


class PaginaClasificaciones(CamelModel):
    """Pagina de clasificaciones con el formato comun de paginacion (seccion 5)."""

    content: list[ClasificacionResponse]
    page: int = Field(description="Numero de pagina, desde 0")
    size: int
    total_elements: int
    total_pages: int


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
