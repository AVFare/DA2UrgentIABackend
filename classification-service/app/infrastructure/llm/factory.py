"""LlmProviderFactory: elige el adapter segun LLM_PROVIDER (CONTEXTO_PROYECTO.md, 12.1)."""

from dataclasses import dataclass

from app.application.ports.llm_provider import LlmProvider
from app.config import Settings
from app.infrastructure.llm.mock_provider import MockLlmProvider
from app.infrastructure.llm.openai_compatible_provider import OpenAICompatibleProvider


class ConfiguracionLlmInvalidaError(ValueError):
    pass


@dataclass(frozen=True)
class _ProveedorOpenAI:
    base_url: str
    modelo: str
    requiere_api_key: bool


_PROVEEDORES_OPENAI = {
    "ollama": _ProveedorOpenAI("http://host.docker.internal:11434/v1", "qwen2.5:1.5b", requiere_api_key=False),
    "groq": _ProveedorOpenAI("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", requiere_api_key=True),
}


def crear_llm_provider(settings: Settings) -> LlmProvider:
    if settings.llm_provider == "mock":
        return MockLlmProvider()

    proveedor = _PROVEEDORES_OPENAI[settings.llm_provider]
    api_key = settings.llm_api_key.get_secret_value()
    if proveedor.requiere_api_key and not api_key:
        raise ConfiguracionLlmInvalidaError(f"LLM_PROVIDER={settings.llm_provider} necesita LLM_API_KEY")
    return OpenAICompatibleProvider(
        nombre=settings.llm_provider,
        base_url=settings.llm_base_url or proveedor.base_url,
        modelo=settings.llm_model or proveedor.modelo,
        api_key=api_key,
    )
