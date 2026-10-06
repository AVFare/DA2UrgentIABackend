"""ClasificacionFacade: el caso de uso de clasificar un ticket (CONTEXTO_PROYECTO.md, 9.3).

Flujo: enmascarar -> armar el prompt -> consultar al LLM con timeout -> reintentar una vez si
la respuesta no es valida y queda presupuesto -> guardar -> devolver.
"""

import asyncio
import logging
import re
import time
from collections.abc import Callable
from datetime import datetime, timezone
from uuid import UUID

from app.application.errors import LlmRespuestaInvalidaError, LlmTimeoutError
from app.application.masking import enmascarar
from app.application.ports.clasificacion_repository import ClasificacionRepository, Pagina
from app.application.ports.llm_provider import LlmProvider, PedidoLlm, RespuestaLlm
from app.domain.models import RegistroClasificacion

log = logging.getLogger(__name__)

PRESUPUESTO_S = 6.0
MARGEN_REINTENTO_S = 2.0
TICKET_DE_PRUEBA = ("Prueba de arranque", "Ticket de prueba para preparar el modelo")

_MARCADORES = re.compile(r"\{(titulo|descripcion)\}")


def armar_prompt(plantilla: str, titulo: str, descripcion: str) -> str:
    valores = {"titulo": titulo, "descripcion": descripcion}
    return _MARCADORES.sub(lambda marcador: valores[marcador.group(1)], plantilla)


class ClasificacionFacade:
    def __init__(
        self,
        llm: LlmProvider,
        repositorio: ClasificacionRepository,
        plantilla: str,
        version_prompt: str,
        timeout_llm_s: float,
        presupuesto_s: float = PRESUPUESTO_S,
        margen_reintento_s: float = MARGEN_REINTENTO_S,
        reloj: Callable[[], float] = time.monotonic,
        ahora: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
    ) -> None:
        self._llm = llm
        self._repositorio = repositorio
        self._plantilla = plantilla
        self._version_prompt = version_prompt
        self._timeout_llm_s = timeout_llm_s
        self._presupuesto_s = presupuesto_s
        self._margen_reintento_s = margen_reintento_s
        self._reloj = reloj
        self._ahora = ahora

    async def clasificar(self, ticket_id: UUID, titulo: str, descripcion: str) -> RegistroClasificacion:
        titulo = enmascarar(titulo)
        descripcion = enmascarar(descripcion)
        pedido = PedidoLlm(titulo, descripcion, armar_prompt(self._plantilla, titulo, descripcion))

        inicio = self._reloj()
        respuesta = await self._consultar(pedido, inicio)
        latencia_ms = round((self._reloj() - inicio) * 1000)

        registro = RegistroClasificacion(
            ticket_id=ticket_id,
            clasificacion=respuesta.clasificacion,
            proveedor=self._llm.nombre,
            modelo=respuesta.modelo,
            version_prompt=self._version_prompt,
            latencia_ms=latencia_ms,
            fecha=self._ahora().replace(microsecond=0),
            texto_enmascarado=f"{titulo}\n{descripcion}",
            respuesta_cruda=enmascarar(respuesta.texto_crudo),
        )
        guardado = await self._repositorio.guardar(registro)
        log.info("Ticket %s clasificado con %s/%s en %d ms: %s", ticket_id, guardado.proveedor, guardado.modelo,
                 latencia_ms, guardado.clasificacion.categoria)
        return guardado

    async def listar(self, ticket_id: UUID | None, page: int, size: int) -> Pagina[RegistroClasificacion]:
        return await self._repositorio.listar(ticket_id, page, size)

    async def precalentar(self) -> None:
        """Prepara el LLM con el prompt real, para que la primera clasificacion no tarde de mas."""
        await self._llm.precalentar(armar_prompt(self._plantilla, *TICKET_DE_PRUEBA))

    async def _consultar(self, pedido: PedidoLlm, inicio: float) -> RespuestaLlm:
        """Una consulta, y un reintento si la respuesta no es valida y quedan MARGEN_REINTENTO_S."""
        for intento in (1, 2):
            timeout_s = min(self._timeout_llm_s, self._restante(inicio))
            try:
                async with asyncio.timeout(timeout_s):
                    return await self._llm.consultar(pedido)
            except TimeoutError:
                raise LlmTimeoutError(round(timeout_s * 1000)) from None
            except LlmRespuestaInvalidaError as error:
                if intento == 2 or self._restante(inicio) < self._margen_reintento_s:
                    raise
                log.warning("Respuesta invalida del LLM (%s); se reintenta", error)
        raise AssertionError("inalcanzable")

    def _restante(self, inicio: float) -> float:
        return self._presupuesto_s - (self._reloj() - inicio)
