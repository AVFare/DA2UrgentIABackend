from typing import Generic, TypeVar

from pydantic import Field

from app.schemas.camel_model import CamelModel

T = TypeVar("T")


class InputPagination(CamelModel):
    page: int = Field(default=0, ge=0, description="Página, desde 0")
    size: int = Field(default=20, ge=1, le=100, description="Tamaño de página (máximo 100)")


class OutputPagination(CamelModel, Generic[T]):  # noqa: UP046 - Generic[T] para que corra en 3.11 y 3.12
    content: list[T]
    page: int = Field(examples=[0])
    size: int = Field(examples=[20])
    total_elements: int = Field(examples=[1])
    total_pages: int = Field(examples=[1])
