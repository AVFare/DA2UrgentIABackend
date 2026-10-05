from fastapi import FastAPI

from app.config import settings
from app.endpoints import health


def create_app() -> FastAPI:
    app = FastAPI(
        title="notification-service",
        version=settings.service_version,
        description="UrgentIA: consume eventos de tickets y registra notificaciones (email simulado e internas).",
    )
    app.include_router(health.router)
    return app


app = create_app()
