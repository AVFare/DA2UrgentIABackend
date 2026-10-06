from datetime import UTC, datetime
from typing import Annotated

from pydantic import BaseModel, ConfigDict, PlainSerializer
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """snake_case en el codigo, camelCase en el JSON (contexto, seccion 5)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


def _iso_utc(fecha: datetime) -> str:
    if fecha.tzinfo is None:
        fecha = fecha.replace(tzinfo=UTC)
    return fecha.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


# Fecha en UTC que se serializa como ISO-8601 con Z y sin milisegundos: 2026-10-05T14:03:11Z
FechaUtc = Annotated[datetime, PlainSerializer(_iso_utc, return_type=str, when_used="json")]
