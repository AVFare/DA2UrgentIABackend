"""Punto de entrada de classification-service.

Arranque: python -m app.main (puerto 8082). Swagger propio en /docs y OpenAPI en /openapi.json.
"""

import logging

import uvicorn
from fastapi import FastAPI

from app import __version__
from app.config import PUERTO, get_settings
from app.infrastructure.api import health, routes
from app.infrastructure.api.correlation import registrar_correlation_id
from app.infrastructure.api.errors import registrar_manejadores_de_error
from app.infrastructure.api.openapi import DESCRIPCION, generar_openapi
from app.infrastructure.logs import configurar_logs

log = logging.getLogger(__name__)


def create_app() -> FastAPI:
    app = FastAPI(
        title="UrgentIA - classification-service",
        version=__version__,
        description=DESCRIPCION,
        openapi_url="/openapi.json",
        docs_url="/docs",
        redoc_url=None,
    )
    registrar_correlation_id(app)
    registrar_manejadores_de_error(app)
    app.include_router(health.router)
    app.include_router(routes.router)
    app.openapi = lambda: generar_openapi(app)
    return app


app = create_app()


if __name__ == "__main__":
    settings = get_settings()
    configurar_logs(settings.log_level)
    log.info("Arranca classification-service en el puerto %s con LLM_PROVIDER=%s", PUERTO, settings.llm_provider)
    # log_config=None: uvicorn usa la configuracion de logs JSON de arriba.
    # access_log=False: cada pedido lo registra el middleware de correlation.py, con su correlationId.
    uvicorn.run(app, host="0.0.0.0", port=PUERTO, log_config=None, access_log=False)
