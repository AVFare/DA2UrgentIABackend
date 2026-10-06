"""Punto de entrada de classification-service.

Arranque: python -m app.main (puerto 8082). Swagger propio en /docs y OpenAPI en /openapi.json.
"""

import asyncio
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from pymongo import AsyncMongoClient

from app.application.clasificacion_facade import ClasificacionFacade, armar_prompt
from app.config import PUERTO, VERSION, get_settings
from app.infrastructure.api.correlation import registrar_correlation_id
from app.infrastructure.api.errors import registrar_manejadores_de_error
from app.infrastructure.api.openapi import DESCRIPCION, generar_openapi
from app.infrastructure.api.routes import clasificaciones, health
from app.infrastructure.llm.factory import crear_llm_provider
from app.infrastructure.llm.openai_compatible_provider import OpenAICompatibleProvider
from app.infrastructure.logs import configurar_logs
from app.infrastructure.persistence.clasificacion_repository import COLECCION, MongoClasificacionRepository
from app.infrastructure.prompts import cargar_plantilla

log = logging.getLogger(__name__)


@asynccontextmanager
async def _ciclo_de_vida(app: FastAPI) -> AsyncIterator[None]:
    """Arma las dependencias al arrancar y las cierra al apagar. Si la facade ya viene armada, la usa."""
    if getattr(app.state, "facade", None) is not None:
        yield
        return

    settings = get_settings()
    if not settings.mongo_uri:
        raise RuntimeError("Falta la variable MONGO_URI")

    proveedor = crear_llm_provider(settings)
    cliente_mongo = AsyncMongoClient(settings.mongo_uri, tz_aware=True, serverSelectionTimeoutMS=5000)
    repositorio = MongoClasificacionRepository(cliente_mongo.get_default_database()[COLECCION])
    await repositorio.crear_indices()

    plantilla = cargar_plantilla(settings.prompt_version)
    app.state.facade = ClasificacionFacade(
        llm=proveedor,
        repositorio=repositorio,
        plantilla=plantilla,
        version_prompt=settings.prompt_version,
        timeout_llm_s=settings.llm_timeout_ms / 1000,
    )

    # En CPU, Ollama tarda varios segundos en cargar el modelo y procesar la parte fija del prompt.
    precalentado = None
    if settings.llm_provider == "ollama" and isinstance(proveedor, OpenAICompatibleProvider):
        prompt = armar_prompt(plantilla, "Prueba de arranque", "Ticket de prueba para precalentar el modelo")
        precalentado = asyncio.create_task(proveedor.precalentar(prompt))

    log.info("Listo: LLM_PROVIDER=%s, modelo=%s, prompt=%s", proveedor.nombre,
             getattr(proveedor, "modelo", "mock-v1"), settings.prompt_version)
    try:
        yield
    finally:
        if precalentado:
            precalentado.cancel()
        if isinstance(proveedor, OpenAICompatibleProvider):
            await proveedor.cerrar()
        await cliente_mongo.close()


def create_app(facade: ClasificacionFacade | None = None) -> FastAPI:
    app = FastAPI(
        title="UrgentIA - classification-service",
        version=VERSION,
        description=DESCRIPCION,
        openapi_url="/openapi.json",
        docs_url="/docs",
        redoc_url=None,
        lifespan=_ciclo_de_vida,
    )
    app.state.facade = facade
    registrar_correlation_id(app)
    registrar_manejadores_de_error(app)
    app.include_router(health.router)
    app.include_router(clasificaciones.router)
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
