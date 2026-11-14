"""Puerto de salida para responder una solicitud de clasificacion. Lo implementa infrastructure/messaging."""

from typing import Protocol

from app.application.errors import LlmError
from app.application.solicitud import SolicitudDeClasificacion
from app.domain.models import RegistroClasificacion


class PublicadorDeClasificaciones(Protocol):
    """Las dos publicaciones tienen que fallar si el mensaje no quedo aceptado, para que la solicitud se reintente."""

    async def publicar_clasificacion(self, solicitud: SolicitudDeClasificacion,
                                     registro: RegistroClasificacion) -> None:
        """Responde la solicitud con la clasificacion guardada."""
        ...

    async def publicar_error(self, solicitud: SolicitudDeClasificacion, error: LlmError) -> None:
        """Responde que la solicitud no se pudo clasificar porque fallo el LLM."""
        ...
