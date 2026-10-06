import pytest

from app.config import Settings
from app.infrastructure.llm.factory import ConfiguracionLlmInvalidaError, crear_llm_provider
from app.infrastructure.llm.mock_provider import MockLlmProvider
from app.infrastructure.llm.openai_compatible_provider import OpenAICompatibleProvider
from app.infrastructure.prompts import cargar_plantilla


@pytest.fixture(autouse=True)
def entorno_limpio(monkeypatch):
    for variable in ("LLM_PROVIDER", "LLM_API_KEY", "LLM_MODEL", "LLM_BASE_URL"):
        monkeypatch.delenv(variable, raising=False)


def test_mock_por_defecto():
    assert isinstance(crear_llm_provider(Settings()), MockLlmProvider)


def test_ollama_usa_la_url_y_el_modelo_por_defecto():
    provider = crear_llm_provider(Settings(llm_provider="ollama"))

    assert isinstance(provider, OpenAICompatibleProvider)
    assert provider.nombre == "ollama"
    assert provider.modelo == "qwen2.5:1.5b"
    assert provider._url == "http://host.docker.internal:11434/v1/chat/completions"


def test_las_variables_pisan_la_url_y_el_modelo():
    provider = crear_llm_provider(Settings(llm_provider="ollama", llm_base_url="http://localhost:11434/v1",
                                           llm_model="qwen2.5:3b"))

    assert provider.modelo == "qwen2.5:3b"
    assert provider._url == "http://localhost:11434/v1/chat/completions"


def test_groq_necesita_api_key():
    with pytest.raises(ConfiguracionLlmInvalidaError, match="LLM_API_KEY"):
        crear_llm_provider(Settings(llm_provider="groq", llm_api_key=""))


def test_groq_con_api_key():
    provider = crear_llm_provider(Settings(llm_provider="groq", llm_api_key="clave"))

    assert provider._url == "https://api.groq.com/openai/v1/chat/completions"
    assert provider._headers == {"Authorization": "Bearer clave"}


def test_carga_el_prompt_v1_con_sus_marcadores():
    plantilla = cargar_plantilla("v1")

    assert plantilla.count("{titulo}") == 1
    assert plantilla.count("{descripcion}") == 1
    assert "Respondé ÚNICAMENTE con un objeto JSON" in plantilla


def test_version_de_prompt_inexistente():
    with pytest.raises(FileNotFoundError, match="clasificacion_v99.txt"):
        cargar_plantilla("v99")
