import pytest
from pydantic import ValidationError

from app.config import Settings

VARIABLES = ["MONGO_URI", "LLM_PROVIDER", "LLM_API_KEY", "LLM_MODEL", "LLM_BASE_URL",
             "LLM_TIMEOUT_MS", "PROMPT_VERSION", "LOG_LEVEL"]


@pytest.fixture(autouse=True)
def entorno_limpio(monkeypatch):
    for variable in VARIABLES:
        monkeypatch.delenv(variable, raising=False)


def test_sin_variables_usa_los_defaults():
    settings = Settings()

    assert settings.llm_provider == "mock"
    assert settings.llm_timeout_ms == 5000
    assert settings.prompt_version == "v2"
    assert settings.log_level == "INFO"
    assert settings.llm_api_key.get_secret_value() == ""


def test_lee_las_variables_de_entorno(monkeypatch):
    monkeypatch.setenv("LLM_PROVIDER", "Groq")
    monkeypatch.setenv("LLM_API_KEY", "secreta")
    monkeypatch.setenv("LLM_MODEL", "qwen/qwen3.8-27b")
    monkeypatch.setenv("LLM_TIMEOUT_MS", "3000")
    monkeypatch.setenv("LOG_LEVEL", "debug")

    settings = Settings()

    assert settings.llm_provider == "groq"
    assert settings.llm_model == "qwen/qwen3.8-27b"
    assert settings.llm_timeout_ms == 3000
    assert settings.log_level == "DEBUG"
    assert "secreta" not in repr(settings)


@pytest.mark.parametrize("variable, valor", [
    ("LLM_PROVIDER", "openai"),
    ("LLM_TIMEOUT_MS", "0"),
    ("LLM_TIMEOUT_MS", "abc"),
])
def test_rechaza_valores_invalidos(monkeypatch, variable, valor):
    monkeypatch.setenv(variable, valor)

    with pytest.raises(ValidationError):
        Settings()
