"""GET /health (seccion 5): lo usan el healthcheck del Compose y el CI."""

from fastapi import APIRouter

from app.config import SERVICE_NAME, VERSION
from app.infrastructure.api.schemas.health import HealthResponse

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    summary="Estado del servicio",
    operation_id="health",
    response_description="El servicio esta arriba",
    openapi_extra={"security": []},
)
def health() -> HealthResponse:
    return HealthResponse(status="UP", service=SERVICE_NAME, version=VERSION)
