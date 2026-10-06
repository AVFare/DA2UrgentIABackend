"""Puerto de salida hacia el LLM. Lo implementan los adapters de infrastructure/llm."""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class PedidoLlm:
    """Lo que se le manda al LLM. Todo el texto ya viene enmascarado."""

    titulo: str
    descripcion: str
    prompt: str


@dataclass(frozen=True)
class RespuestaLlm:
    """Respuesta cruda del LLM, sin validar: la valida response_parser (ACL)."""

    texto: str
    modelo: str


class LlmProvider(Protocol):
    """Strategy: hay un adapter por contrato de API (mock, compatible con OpenAI, ...)."""

    nombre: str

    async def consultar(self, pedido: PedidoLlm) -> RespuestaLlm:
        """Devuelve la respuesta cruda del LLM. El timeout lo controla quien llama."""
        ...
