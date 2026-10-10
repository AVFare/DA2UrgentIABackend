"""Todo error sale con el formato comun (contexto, seccion 5.3) y queda logueado con su correlationId."""

import logging
from datetime import UTC, datetime
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.exceptions.api_exception import ApiException
from app.logging_config import correlation_id_var

logger = logging.getLogger("errores")

_CODIGOS_HTTP = {400: "VALIDACION", 404: "NO_ENCONTRADO", 405: "VALIDACION"}


def _respuesta(
    request: Request, status: int, codigo: str, mensaje: str, detalles: list[dict[str, str]] | None = None
) -> JSONResponse:
    correlation_id = getattr(request.state, "correlation_id", None)
    cuerpo: dict[str, Any] = {
        "codigo": codigo,
        "mensaje": mensaje,
        "timestamp": datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "path": request.url.path,
        "correlationId": correlation_id,
    }
    if detalles:
        cuerpo["detalles"] = detalles
    headers = {"X-Correlation-Id": correlation_id} if correlation_id else None
    return JSONResponse(status_code=status, content=cuerpo, headers=headers)


def _campo(loc: tuple[Any, ...]) -> str:
    partes = [str(p) for p in loc]
    if partes and partes[0] in ("body", "query", "path", "header"):
        partes = partes[1:]
    return ".".join(partes) or "body"


def registrar_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiException)
    async def _api(request: Request, exc: ApiException) -> JSONResponse:
        logger.warning(exc.mensaje, extra={"codigo": exc.codigo, "path": request.url.path})
        return _respuesta(request, exc.status, exc.codigo, exc.mensaje, exc.detalles)

    @app.exception_handler(RequestValidationError)
    async def _validacion(request: Request, exc: RequestValidationError) -> JSONResponse:
        detalles = [
            {"campo": _campo(e.get("loc", ())), "mensaje": e.get("msg", "valor inválido")} for e in exc.errors()
        ]
        logger.warning("Solicitud inválida", extra={"codigo": "VALIDACION", "path": request.url.path})
        return _respuesta(request, 400, "VALIDACION", "La solicitud no es válida", detalles)

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        codigo = _CODIGOS_HTTP.get(exc.status_code, "ERROR_INTERNO")
        logger.warning(str(exc.detail), extra={"codigo": codigo, "path": request.url.path})
        return _respuesta(request, exc.status_code, codigo, str(exc.detail))

    @app.exception_handler(Exception)
    async def _interno(request: Request, exc: Exception) -> JSONResponse:
        # Este handler corre afuera del middleware: se recupera el correlationId del request.
        correlation_id_var.set(getattr(request.state, "correlation_id", None))
        logger.exception("Error no controlado", extra={"path": request.url.path})
        return _respuesta(request, 500, "ERROR_INTERNO", "Ocurrió un error interno")
