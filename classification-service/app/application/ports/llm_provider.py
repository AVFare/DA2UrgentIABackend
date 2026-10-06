"""Puerto de salida hacia el LLM. Lo implementan los adapters de infrastructure/llm."""

from dataclasses import dataclass
from typing import Protocol

from app.domain.models import Clasificacion


@dataclass(frozen=True)
class PedidoLlm:
    """Lo que se le manda al LLM. Todo el texto ya viene enmascarado."""

    titulo: str
    descripcion: str
    prompt: str


@dataclass(frozen=True)
class RespuestaLlm:
    clasificacion: Clasificacion
    modelo: str
    texto_crudo: str


class LlmProvider(Protocol):
    """Strategy: hay un adapter por contrato de API (mock, compatible con OpenAI, ...)."""

    nombre: str

    async def consultar(self, pedido: PedidoLlm) -> RespuestaLlm:
        """Clasifica el pedido. El timeout lo controla quien llama.

        Lanza LlmRespuestaInvalidaError si la respuesta no es una clasificacion valida y
        LlmNoDisponibleError si no se pudo consultar al LLM.
        """
        ...
