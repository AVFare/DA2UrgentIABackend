"""Endpoints de /api/clasificaciones (contrato de la seccion 9.3)."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Query, Request

from app.application.clasificacion_facade import ClasificacionFacade
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


def _facade(request: Request) -> ClasificacionFacade:
    return request.app.state.facade


Facade = Annotated[ClasificacionFacade, Depends(_facade)]


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
        502: {
            "model": ErrorResponse,
            "description": "LLM_RESPUESTA_INVALIDA - el LLM devolvio algo que no valida o respondio con error",
        },
        504: {"model": ErrorResponse, "description": "LLM_TIMEOUT - el LLM no respondio a tiempo"},
    },
)
async def clasificar(pedido: ClasificarRequest, facade: Facade) -> ClasificacionResponse:
    registro = await facade.clasificar(pedido.ticket_id, pedido.titulo, pedido.descripcion)
    return ClasificacionResponse.desde_registro(registro)


@router.get(
    "",
    summary="Listar clasificaciones",
    description="Clasificaciones paginadas, la mas reciente primero. Con ticketId, solo las de ese ticket.",
    operation_id="listarClasificaciones",
    response_model=PaginaClasificaciones,
    response_description="Pagina de clasificaciones (mas reciente primero)",
)
async def listar(
    facade: Facade,
    ticket_id: Annotated[UUID | None, Query(alias="ticketId", description="Filtra por ticket (UUID)")] = None,
    page: Annotated[int, Query(ge=0, description="Numero de pagina, desde 0")] = 0,
    size: Annotated[int, Query(ge=1, le=100, description="Tamaño de pagina")] = 20,
) -> PaginaClasificaciones:
    return PaginaClasificaciones.desde_pagina(await facade.listar(ticket_id, page, size))
