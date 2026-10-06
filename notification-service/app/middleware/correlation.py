import uuid
from collections.abc import Awaitable, Callable

from fastapi import Request, Response

from app.logging_config import correlation_id_var

HEADER = "X-Correlation-Id"


async def correlation_id_middleware(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
    """Lee X-Correlation-Id (o lo genera), lo deja en un contextvar y lo devuelve en la respuesta."""
    valor = request.headers.get(HEADER) or str(uuid.uuid4())
    request.state.correlation_id = valor
    token = correlation_id_var.set(valor)
    try:
        respuesta = await call_next(request)
    finally:
        correlation_id_var.reset(token)
    respuesta.headers[HEADER] = valor
    return respuesta
