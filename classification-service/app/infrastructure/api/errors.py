"""Traduce las excepciones al formato comun de error (CONTEXTO_PROYECTO.md, seccion 5.3).

FastAPI responde 422 a los errores de validacion; el contrato pide 400 VALIDACION.
Nunca se devuelve el stacktrace: queda solo en el log.
"""

import logging
from datetime import datetime, timezone

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError, LlmTimeoutError
from app.infrastructure.api.correlation import obtener_correlation_id
from app.infrastructure.api.schemas.error import CodigoError, DetalleError, ErrorResponse

log = logging.getLogger(__name__)

# Mensajes en español para los errores de validacion mas comunes de Pydantic.
_MENSAJES_VALIDACION = {
    "missing": "es obligatorio",
    "string_type": "tiene que ser texto",
    "uuid_parsing": "tiene que ser un UUID",
    "uuid_type": "tiene que ser un UUID",
    "json_invalid": "el JSON no es valido",
    "model_attributes_type": "tiene que ser un objeto JSON",
}


def respuesta_error(
    request: Request,
    status: int,
    codigo: CodigoError,
    mensaje: str,
    detalles: list[DetalleError] | None = None,
) -> JSONResponse:
    error = ErrorResponse(
        codigo=codigo,
        mensaje=mensaje,
        detalles=detalles,
        timestamp=datetime.now(timezone.utc).replace(microsecond=0),
        path=request.url.path,
        correlation_id=obtener_correlation_id(request),
    )
    return JSONResponse(
        status_code=status,
        content=error.model_dump(mode="json", by_alias=True, exclude_none=True),
    )


def _detalle(error: dict) -> DetalleError:
    ubicacion = [str(parte) for parte in error.get("loc", ())]
    campo = ".".join(ubicacion[1:]) or (ubicacion[0] if ubicacion else "body")
    tipo = error.get("type", "")
    contexto = error.get("ctx") or {}
    if tipo == "string_too_short":
        mensaje = f"debe tener al menos {contexto.get('min_length')} caracteres"
    elif tipo == "string_too_long":
        mensaje = f"debe tener como maximo {contexto.get('max_length')} caracteres"
    else:
        mensaje = _MENSAJES_VALIDACION.get(tipo, error.get("msg", "valor invalido"))
    return DetalleError(campo=campo, mensaje=mensaje)


def registrar_manejadores_de_error(app: FastAPI) -> None:
    @app.exception_handler(RequestValidationError)
    async def _validacion(request: Request, exc: RequestValidationError) -> JSONResponse:
        detalles = [_detalle(e) for e in exc.errors()]
        return respuesta_error(request, 400, "VALIDACION", "El pedido tiene datos invalidos", detalles)

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        if exc.status_code == 404:
            return respuesta_error(request, 404, "NO_ENCONTRADO", f"No existe la ruta {request.url.path}")
        if exc.status_code < 500:
            return respuesta_error(request, exc.status_code, "VALIDACION", str(exc.detail))
        return respuesta_error(request, exc.status_code, "ERROR_INTERNO", "Ocurrio un error inesperado")

    @app.exception_handler(LlmTimeoutError)
    async def _llm_timeout(request: Request, exc: LlmTimeoutError) -> JSONResponse:
        log.warning("%s", exc)
        return respuesta_error(request, 504, "LLM_TIMEOUT", str(exc))

    @app.exception_handler(LlmRespuestaInvalidaError)
    async def _llm_respuesta_invalida(request: Request, exc: LlmRespuestaInvalidaError) -> JSONResponse:
        log.warning("Respuesta invalida del LLM: %s", exc)
        return respuesta_error(request, 502, "LLM_RESPUESTA_INVALIDA", f"La respuesta del LLM no es valida: {exc}")

    @app.exception_handler(LlmNoDisponibleError)
    async def _llm_no_disponible(request: Request, exc: LlmNoDisponibleError) -> JSONResponse:
        log.warning("LLM no disponible: %s", exc)
        return respuesta_error(request, 502, "LLM_RESPUESTA_INVALIDA", f"No se pudo consultar al LLM: {exc}")

    @app.exception_handler(Exception)
    async def _inesperado(request: Request, exc: Exception) -> JSONResponse:
        log.exception("Error inesperado en %s %s", request.method, request.url.path)
        return respuesta_error(request, 500, "ERROR_INTERNO", "Ocurrio un error inesperado")
