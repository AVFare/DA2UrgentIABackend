"""Tests de /api/clasificaciones con la facade real, el MockLlmProvider y un repositorio en memoria."""

import pytest
from fastapi.testclient import TestClient

from app.application.clasificacion_facade import ClasificacionFacade
from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError, LlmTimeoutError
from app.infrastructure.llm.mock_provider import MockLlmProvider
from app.infrastructure.prompts import cargar_plantilla
from app.main import create_app
from tests.fakes import LlmFalso, RepositorioEnMemoria

TICKET_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
PEDIDO = {
    "ticketId": TICKET_ID,
    "titulo": "No puede ingresar nadie",
    "descripcion": "Producción caída, todos los usuarios bloqueados en el login desde las 9",
}


def crear_cliente(llm=None) -> tuple[TestClient, RepositorioEnMemoria]:
    repositorio = RepositorioEnMemoria()
    facade = ClasificacionFacade(
        llm=llm or MockLlmProvider(),
        repositorio=repositorio,
        plantilla=cargar_plantilla("full"),
        version_prompt="full",
        timeout_llm_s=5,
    )
    return TestClient(create_app(facade), raise_server_exceptions=False), repositorio


def test_clasifica_el_caso_de_la_demo():
    cliente, repositorio = crear_cliente()

    respuesta = cliente.post("/api/clasificaciones", json=PEDIDO)

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert {k: cuerpo[k] for k in ("ticketId", "categoria", "urgencia", "impacto", "moduloAfectado",
                                     "requiereEscalamiento", "confianza", "proveedor", "modelo", "versionPrompt")} == {
        "ticketId": TICKET_ID, "categoria": "INCIDENTE", "urgencia": "ALTA", "impacto": "ALTO",
        "moduloAfectado": "AUTENTICACION", "requiereEscalamiento": True, "confianza": 0.7,
        "proveedor": "mock", "modelo": "mock-v1", "versionPrompt": "full",
    }
    assert cuerpo["id"] == repositorio.registros[0].id
    assert cuerpo["fecha"].endswith("Z")
    assert isinstance(cuerpo["latenciaMs"], int)
    assert "prioridad" not in cuerpo


def test_lista_paginado_y_filtrado_por_ticket():
    cliente, _ = crear_cliente()
    cliente.post("/api/clasificaciones", json=PEDIDO)
    cliente.post("/api/clasificaciones", json=PEDIDO)
    cliente.post("/api/clasificaciones", json={**PEDIDO, "ticketId": "11111111-1111-4111-8111-111111111111"})

    del_ticket = cliente.get("/api/clasificaciones", params={"ticketId": TICKET_ID, "size": 1}).json()
    todas = cliente.get("/api/clasificaciones").json()

    assert (del_ticket["totalElements"], del_ticket["totalPages"], len(del_ticket["content"])) == (2, 2, 1)
    assert del_ticket["content"][0]["ticketId"] == TICKET_ID
    assert (todas["totalElements"], todas["page"], todas["size"]) == (3, 0, 20)


@pytest.mark.parametrize("error, status, codigo", [
    (LlmRespuestaInvalidaError("urgencia fuera de la lista"), 502, "LLM_RESPUESTA_INVALIDA"),
    (LlmNoDisponibleError("groq respondio HTTP 503"), 502, "LLM_RESPUESTA_INVALIDA"),
    (LlmTimeoutError(5000), 504, "LLM_TIMEOUT"),
])
def test_errores_del_llm_con_el_formato_comun(error, status, codigo):
    cliente, repositorio = crear_cliente(LlmFalso(error, error))

    respuesta = cliente.post("/api/clasificaciones", json=PEDIDO, headers={"X-Correlation-Id": "abc"})

    assert respuesta.status_code == status
    cuerpo = respuesta.json()
    assert cuerpo["codigo"] == codigo
    assert cuerpo["correlationId"] == "abc"
    assert cuerpo["path"] == "/api/clasificaciones"
    assert repositorio.registros == []


def test_timeout_informa_el_limite():
    cliente, _ = crear_cliente(LlmFalso(LlmTimeoutError(5000)))

    assert cliente.post("/api/clasificaciones", json=PEDIDO).json()["mensaje"] == "El LLM no respondio en 5000 ms"
