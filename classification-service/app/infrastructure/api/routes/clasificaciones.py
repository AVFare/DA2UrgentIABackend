"""Endpoints de /api/clasificaciones (contrato de la seccion 9.3)."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Query

from app.infrastructure.api.correlation import HEADER
from app.infrastructure.api.schemas.clasificacion import (
    ClasificacionResponse,
    ClasificarRequest,
    PaginaClasificaciones,
)
from app.infrastructure.api.schemas.error import ErrorResponse


def _header_correlation_id(
    correlation_id: Annotated[
        str | None,
        Header(alias=HEADER, description="Id para seguir el pedido en los logs. Si no viene, se genera uno."),
    ] = None,
) -> None:
    """Solo documenta el header en el OpenAPI; lo procesa el middleware de correlation.py."""


_ERRORES_COMUNES = {
    400: {"model": ErrorResponse, "description": "VALIDACION - body o parametros invalidos"},
    500: {"model": ErrorResponse, "description": "ERROR_INTERNO - cualquier otro error (por ejemplo, MongoDB caido)"},
}

router = APIRouter(
    prefix="/api/clasificaciones",
    tags=["Clasificaciones"],
    dependencies=[Depends(_header_correlation_id)],
    responses=_ERRORES_COMUNES,
)


@router.post(
    "",
    summary="Clasificar un ticket",
    description=(
        "Enmascara los datos personales, consulta al LLM, valida la respuesta, la guarda y la devuelve. "
        "Si la respuesta del LLM no es valida, reintenta una vez mientras quede tiempo. El tiempo maximo "
        "de espera del LLM es configurable; si se supera, responde 504."
    ),
    operation_id="clasificar",
    response_model=ClasificacionResponse,
    response_description="Clasificacion sugerida por la IA",
    responses={
        502: {"model": ErrorResponse, "description": "LLM_RESPUESTA_INVALIDA - el LLM devolvio algo que no valida"},
        504: {"model": ErrorResponse, "description": "LLM_TIMEOUT - el LLM no respondio a tiempo"},
    },
)
def clasificar(pedido: ClasificarRequest) -> ClasificacionResponse:
    raise NotImplementedError


@router.get(
    "",
    summary="Listar clasificaciones",
    description="Clasificaciones paginadas, la mas reciente primero. Con ticketId, solo las de ese ticket.",
    operation_id="listarClasificaciones",
    response_model=PaginaClasificaciones,
    response_description="Pagina de clasificaciones (mas reciente primero)",
)
def listar(
    ticket_id: Annotated[UUID | None, Query(alias="ticketId", description="Filtra por ticket (UUID)")] = None,
    page: Annotated[int, Query(ge=0, description="Numero de pagina, desde 0")] = 0,
    size: Annotated[int, Query(ge=1, le=100, description="Tamaño de pagina")] = 20,
) -> PaginaClasificaciones:
    raise NotImplementedError
