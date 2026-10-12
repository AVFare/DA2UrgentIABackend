from pydantic import Field

from app.schemas.camel_model import CamelModel


class OutputHealth(CamelModel):
    status: str = Field(examples=["UP"])
    service: str = Field(examples=["notification-service"])
    version: str = Field(examples=["0.1.0"])
