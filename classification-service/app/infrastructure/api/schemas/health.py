"""DTO de GET /health (seccion 5)."""

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
