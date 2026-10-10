"""Propagacion del X-Correlation-Id (CONTEXTO_PROYECTO.md, seccion 5.1).

Si el pedido trae el header se reusa; si no, se genera uno. Queda disponible en
request.state, en una ContextVar (para los logs) y vuelve en el header de la respuesta.
"""

import logging
import time
import uuid
from contextvars import ContextVar

from fastapi import FastAPI, Request

HEADER = "X-Correlation-Id"

log = logging.getLogger(__name__)

correlation_id_actual: ContextVar[str] = ContextVar("correlation_id", default="-")


def obtener_correlation_id(request: Request) -> str:
    return getattr(request.state, "correlation_id", None) or request.headers.get(HEADER) or "-"


def registrar_correlation_id(app: FastAPI) -> None:
    @app.middleware("http")
    async def _correlation_id(request: Request, call_next):
        correlation_id = request.headers.get(HEADER) or str(uuid.uuid4())
        request.state.correlation_id = correlation_id
        token = correlation_id_actual.set(correlation_id)
        inicio = time.perf_counter()
        try:
            response = await call_next(request)
            # Reemplaza el log de acceso de uvicorn, que no conoce el correlationId.
            log.info("%s %s -> %s (%d ms)", request.method, request.url.path, response.status_code,
                     (time.perf_counter() - inicio) * 1000)
        finally:
            correlation_id_actual.reset(token)
        response.headers[HEADER] = correlation_id
        return response
