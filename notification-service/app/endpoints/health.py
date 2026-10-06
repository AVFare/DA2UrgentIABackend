from fastapi import APIRouter

from app.config import SERVICE_NAME, settings
from app.schemas.health import OutputHealth

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> OutputHealth:
    """Indica que el servicio está levantado."""
    return OutputHealth(status="UP", service=SERVICE_NAME, version=settings.service_version)
