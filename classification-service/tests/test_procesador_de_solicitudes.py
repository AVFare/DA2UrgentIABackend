import asyncio
from uuid import UUID

import pytest

from app.application.errors import LlmNoDisponibleError
from app.application.procesador_de_solicitudes import ProcesadorDeSolicitudes, SolicitudDeClasificacion
from tests.fakes import CLASIFICACION_CRITICA, LlmFalso, PublicadorFalso, RepositorioEnMemoria
from tests.test_clasificacion_facade import crear_facade

SOLICITUD = SolicitudDeClasificacion(
    solicitud_id=UUID("0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b"),
    ticket_id=UUID("7c9e6679-7425-40de-944b-e07fc1f90ae7"),
    titulo="No puede ingresar nadie",
    descripcion="Producción caída, mi mail es juan.perez@empresa.com",
    correlation_id="correlacion-1",
)


def armar(*respuestas):
    llm = LlmFalso(*respuestas)
    repositorio = RepositorioEnMemoria()
    publicador = PublicadorFalso()
    procesador = ProcesadorDeSolicitudes(crear_facade(llm, repositorio), repositorio, publicador)
    return procesador, llm, repositorio, publicador


def test_clasifica_guarda_con_el_id_de_la_solicitud_y_publica():
    procesador, llm, repositorio, publicador = armar(CLASIFICACION_CRITICA)

    asyncio.run(procesador.procesar(SOLICITUD))

    registro = repositorio.registros[0]
    assert registro.solicitud_id == SOLICITUD.solicitud_id
    assert registro.ticket_id == SOLICITUD.ticket_id
    assert "[EMAIL]" in registro.texto_enmascarado
    assert publicador.publicados == [(registro, "correlacion-1")]


def test_una_solicitud_repetida_no_vuelve_a_consultar_al_llm():
    procesador, llm, repositorio, publicador = armar(CLASIFICACION_CRITICA)

    asyncio.run(procesador.procesar(SOLICITUD))
    asyncio.run(procesador.procesar(SOLICITUD))

    assert len(llm.pedidos) == 1
    assert len(repositorio.registros) == 1
    assert [registro for registro, _ in publicador.publicados] == [repositorio.registros[0]] * 2


def test_si_falla_la_publicacion_el_reintento_reusa_lo_guardado():
    procesador, llm, repositorio, publicador = armar(CLASIFICACION_CRITICA)
    publicador.error = ConnectionError("broker caido")

    with pytest.raises(ConnectionError):
        asyncio.run(procesador.procesar(SOLICITUD))
    asyncio.run(procesador.procesar(SOLICITUD))

    assert len(llm.pedidos) == 1
    assert len(publicador.publicados) == 1


def test_si_el_llm_falla_no_guarda_ni_publica():
    procesador, _, repositorio, publicador = armar(LlmNoDisponibleError("groq respondio HTTP 503"))

    with pytest.raises(LlmNoDisponibleError):
        asyncio.run(procesador.procesar(SOLICITUD))

    assert repositorio.registros == []
    assert publicador.publicados == []
