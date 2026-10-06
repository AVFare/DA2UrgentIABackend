from fastapi import FastAPI

from app.config import settings
from app.endpoints import health
from app.exceptions.handlers import registrar_handlers
from app.logging_config import configurar_logging
from app.middleware.correlation import correlation_id_middleware


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
    )
    app.middleware("http")(correlation_id_middleware)
    registrar_handlers(app)
    _openapi_sin_422(app)
    app.include_router(health.router)
    return app


app = create_app()
