from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import settings
from app.db import get_db
from app.endpoints import eventos, health, notificaciones
from app.exceptions.handlers import registrar_handlers
from app.logging_config import configurar_logging
from app.middleware.correlation import correlation_id_middleware
from app.repositories.evento_procesado_repository import EventoProcesadoRepository
from app.repositories.notificacion_repository import NotificacionRepository


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # Mongo no tiene migraciones: las colecciones y los indices se crean al arrancar.
    db = get_db()
    NotificacionRepository(db).ensure_indexes()
    EventoProcesadoRepository(db).ensure_indexes()
    yield


def _openapi_sin_422(app: FastAPI) -> None:
    """La validacion responde 400 con el formato comun, no el 422 de FastAPI: se saca del Swagger."""
    generar = app.openapi

    def openapi() -> dict:
        if app.openapi_schema:
            return app.openapi_schema
        esquema = generar()
        for operaciones in esquema.get("paths", {}).values():
            for operacion in operaciones.values():
                operacion.get("responses", {}).pop("422", None)
        for nombre in ("HTTPValidationError", "ValidationError"):
            esquema.get("components", {}).get("schemas", {}).pop(nombre, None)
        app.openapi_schema = esquema
        return esquema

    app.openapi = openapi


def create_app() -> FastAPI:
    configurar_logging()
    app = FastAPI(
        title="notification-service",
        version=settings.service_version,
        description="UrgentIA: consume eventos de tickets y registra notificaciones (email simulado e internas).",
        lifespan=lifespan,
    )
    app.middleware("http")(correlation_id_middleware)
    registrar_handlers(app)
    _openapi_sin_422(app)
    app.include_router(health.router)
    app.include_router(eventos.router)
    app.include_router(notificaciones.router)
    return app


app = create_app()
