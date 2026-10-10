import asyncio
import json

import httpx2
import pytest

from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError
from app.application.ports.llm_provider import PedidoLlm
from app.domain.enums import Categoria
from app.infrastructure.llm.openai_compatible_provider import OpenAICompatibleProvider

PEDIDO = PedidoLlm(titulo="Login", descripcion="No anda", prompt="Clasificá este ticket")
CONTENIDO = json.dumps({
    "categoria": "INCIDENTE", "urgencia": "ALTA", "impacto": "ALTO", "moduloAfectado": "AUTENTICACION",
    "requiereEscalamiento": True, "confianza": 0.9, "justificacion": "Nadie puede ingresar",
})


def respuesta_chat(contenido: str = CONTENIDO, modelo: str = "qwen2.5:1.5b") -> dict:
    return {"model": modelo, "choices": [{"message": {"role": "assistant", "content": contenido}}]}


def crear_provider(manejador, api_key: str = "", precalentar_al_iniciar: bool = True) -> OpenAICompatibleProvider:
    return OpenAICompatibleProvider(
        nombre="ollama",
        base_url="http://llm:11434/v1/",
        modelo="qwen2.5:1.5b",
        api_key=api_key,
        precalentar_al_iniciar=precalentar_al_iniciar,
        cliente=httpx2.AsyncClient(transport=httpx2.MockTransport(manejador)),
    )


def consultar(provider: OpenAICompatibleProvider):
    return asyncio.run(provider.consultar(PEDIDO))


def test_manda_el_pedido_con_el_formato_de_chat_completions():
    recibidos = []

    def manejador(request: httpx2.Request) -> httpx2.Response:
        recibidos.append(request)
        return httpx2.Response(200, json=respuesta_chat())

    consultar(crear_provider(manejador, api_key="clave-secreta"))

    request = recibidos[0]
    cuerpo = json.loads(request.content)
    assert str(request.url) == "http://llm:11434/v1/chat/completions"
    assert request.headers["Authorization"] == "Bearer clave-secreta"
    assert cuerpo["model"] == "qwen2.5:1.5b"
    assert cuerpo["messages"] == [{"role": "user", "content": "Clasificá este ticket"}]
    assert cuerpo["response_format"] == {"type": "json_object"}
    assert cuerpo["temperature"] == 0


def test_sin_api_key_no_manda_authorization():
    recibidos = []

    def manejador(request):
        recibidos.append(request)
        return httpx2.Response(200, json=respuesta_chat())

    consultar(crear_provider(manejador))

    assert "Authorization" not in recibidos[0].headers


def test_devuelve_la_clasificacion_el_modelo_y_el_texto_crudo():
    respuesta = consultar(crear_provider(lambda request: httpx2.Response(200, json=respuesta_chat(modelo="qwen2.5:1.5b-instruct"))))

    assert respuesta.clasificacion.categoria is Categoria.INCIDENTE
    assert respuesta.modelo == "qwen2.5:1.5b-instruct"
    assert respuesta.texto_crudo == CONTENIDO


@pytest.mark.parametrize("status", [401, 429, 500, 503])
def test_un_error_http_es_llm_no_disponible(status):
    with pytest.raises(LlmNoDisponibleError, match=f"HTTP {status}") as error:
        consultar(crear_provider(lambda request: httpx2.Response(status, json={"error": "x"})))
    assert error.value.status_http == status


def test_un_error_de_conexion_es_llm_no_disponible():
    def manejador(request):
        raise httpx2.ConnectError("connection refused", request=request)

    with pytest.raises(LlmNoDisponibleError, match="no se pudo conectar"):
        consultar(crear_provider(manejador))


@pytest.mark.parametrize("cuerpo", [
    {"choices": []},
    {"sin": "choices"},
    {"choices": [{"message": {"content": None}}]},
])
def test_una_respuesta_sin_el_formato_esperado_es_invalida(cuerpo):
    with pytest.raises(LlmRespuestaInvalidaError):
        consultar(crear_provider(lambda request: httpx2.Response(200, json=cuerpo)))


def test_un_contenido_que_no_es_clasificacion_es_invalido():
    with pytest.raises(LlmRespuestaInvalidaError, match="faltan campos"):
        consultar(crear_provider(lambda request: httpx2.Response(200, json=respuesta_chat('{"hola": 1}'))))


def test_precalentar_no_falla_si_el_llm_no_responde():
    def manejador(request):
        raise httpx2.ConnectError("connection refused", request=request)

    asyncio.run(crear_provider(manejador).precalentar("prompt"))


def test_precalentar_manda_el_prompt_con_un_solo_token_de_respuesta():
    recibidos = []

    def manejador(request):
        recibidos.append(json.loads(request.content))
        return httpx2.Response(200, json=respuesta_chat())

    asyncio.run(crear_provider(manejador).precalentar("prompt completo"))

    assert recibidos[0]["messages"] == [{"role": "user", "content": "prompt completo"}]
    assert recibidos[0]["max_tokens"] == 1


def test_sin_precalentamiento_habilitado_no_hace_ningun_pedido():
    recibidos = []

    def manejador(request):
        recibidos.append(request)
        return httpx2.Response(200, json=respuesta_chat())

    asyncio.run(crear_provider(manejador, precalentar_al_iniciar=False).precalentar("prompt"))

    assert recibidos == []
