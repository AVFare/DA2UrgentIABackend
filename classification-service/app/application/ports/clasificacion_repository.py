"""Puerto de salida hacia la persistencia de clasificaciones. Lo implementa infrastructure/persistence."""

import math
from dataclasses import dataclass
from typing import Protocol
from uuid import UUID

from app.domain.models import RegistroClasificacion


@dataclass(frozen=True)
class Pagina[T]:
    elementos: list[T]
    page: int
    size: int
    total: int

    @property
    def total_pages(self) -> int:
        return math.ceil(self.total / self.size) if self.size else 0


class ClasificacionRepository(Protocol):
    async def guardar(self, registro: RegistroClasificacion) -> RegistroClasificacion:
        """Guarda el registro y lo devuelve con su id."""
        ...

    async def listar(self, ticket_id: UUID | None, page: int, size: int) -> Pagina[RegistroClasificacion]:
        """Registros mas recientes primero; con ticket_id, solo los de ese ticket."""
        ...
