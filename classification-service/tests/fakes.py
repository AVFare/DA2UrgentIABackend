"""Dobles de prueba de los puertos de salida."""

import asyncio
from dataclasses import replace
from uuid import UUID

from app.application.errors import LlmError
from app.application.ports.clasificacion_repository import Pagina
from app.application.ports.llm_provider import PedidoLlm, RespuestaLlm
from app.domain.models import Clasificacion, RegistroClasificacion

CLASIFICACION_CRITICA = Clasificacion(
    categoria="INCIDENTE",
    urgencia="ALTA",
    impacto="ALTO",
    modulo_afectado="AUTENTICACION",
    requiere_escalamiento=True,
    confianza=0.93,
    justificacion="Caída total del login en producción",
)


class RepositorioEnMemoria:
    def __init__(self) -> None:
        self.registros: list[RegistroClasificacion] = []

    async def guardar(self, registro: RegistroClasificacion) -> RegistroClasificacion:
        guardado = replace(registro, id=f"{len(self.registros) + 1:024x}")
        self.registros.append(guardado)
        return guardado

    async def listar(self, ticket_id: UUID | None, page: int, size: int) -> Pagina[RegistroClasificacion]:
        filtrados = [r for r in self.registros if ticket_id is None or r.ticket_id == ticket_id]
        ordenados = sorted(filtrados, key=lambda r: r.fecha, reverse=True)
        return Pagina(ordenados[page * size:(page + 1) * size], page, size, len(filtrados))


class RelojFalso:
    def __init__(self) -> None:
        self.ahora = 0.0

    def __call__(self) -> float:
        return self.ahora


class LlmFalso:
    """Devuelve (o lanza) las respuestas programadas, en orden.

    Cada consulta avanza el reloj falso `segundos_por_consulta` y, si se pide, espera de verdad
    `demora_real_s` (para probar el timeout).
    """

    nombre = "falso"

    def __init__(self, *respuestas: Clasificacion | LlmError, reloj: RelojFalso | None = None,
                 segundos_por_consulta: float = 0.0, demora_real_s: float = 0.0) -> None:
        self._respuestas = list(respuestas)
        self._reloj = reloj
        self._segundos = segundos_por_consulta
        self._demora_real_s = demora_real_s
        self.pedidos: list[PedidoLlm] = []

    async def consultar(self, pedido: PedidoLlm) -> RespuestaLlm:
        self.pedidos.append(pedido)
        if self._reloj:
            self._reloj.ahora += self._segundos
        if self._demora_real_s:
            await asyncio.sleep(self._demora_real_s)
        respuesta = self._respuestas.pop(0)
        if isinstance(respuesta, LlmError):
            raise respuesta
        return RespuestaLlm(clasificacion=respuesta, modelo="modelo-falso",
                            texto_crudo=f'{{"respuesta": "cruda", "eco": "{pedido.descripcion}"}}')
