"""Configuracion del servicio desde variables de entorno (CONTEXTO_PROYECTO.md, seccion 14).

Nunca hay secretos en el codigo: la API key llega solo por LLM_API_KEY.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_NAME = "classification-service"
VERSION = "0.1.0"
PUERTO = 8082


class Settings(BaseSettings):
    """Cada campo se lee de la variable de entorno con el mismo nombre en mayusculas."""

    model_config = SettingsConfigDict(extra="ignore")

    mongo_uri: str = ""
    # mock: reglas por palabras clave, sin internet (default para desarrollo, tests y plan B).
    # ollama: modelo local. groq: API en internet. Ambos hablan el contrato de OpenAI.
    llm_provider: Literal["mock", "ollama", "groq"] = "mock"
    llm_api_key: SecretStr = SecretStr("")
    llm_model: str = ""
    # Opcional: pisa la URL por defecto del proveedor.
    llm_base_url: str = ""
    llm_timeout_ms: int = Field(default=5000, gt=0)
    prompt_version: str = "v1"
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"

    @field_validator("llm_provider", "log_level", mode="before")
    @classmethod
    def _normalizar(cls, valor: str, info) -> str:
        if not isinstance(valor, str):
            return valor
        valor = valor.strip()
        return valor.lower() if info.field_name == "llm_provider" else valor.upper()


@lru_cache
def get_settings() -> Settings:
    return Settings()
