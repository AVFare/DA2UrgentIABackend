"""Puerto de salida para avisar que una solicitud ya tiene su clasificacion. Lo implementa infrastructure/messaging."""

from typing import Protocol

from app.domain.models import RegistroClasificacion


class PublicadorDeClasificaciones(Protocol):
    async def publicar(self, registro: RegistroClasificacion, correlation_id: str) -> None:
        """Publica la clasificacion generada para registro.solicitud_id.

        Tiene que fallar si el mensaje no quedo aceptado, para que la solicitud se reintente.
        """
        ...
