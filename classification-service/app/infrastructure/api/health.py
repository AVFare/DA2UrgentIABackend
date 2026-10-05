"""GET /health (seccion 5): lo usan el healthcheck del Compose y el CI."""

from fastapi import APIRouter
from pydantic import BaseModel

from app import __version__
from app.config import SERVICE_NAME

router = APIRouter(tags=["Health"])


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


@router.get(
    "/health",
    summary="Estado del servicio",
    operation_id="health",
    response_description="El servicio esta arriba",
    openapi_extra={"security": []},
)
def health() -> HealthResponse:
    return HealthResponse(status="UP", service=SERVICE_NAME, version=__version__)
