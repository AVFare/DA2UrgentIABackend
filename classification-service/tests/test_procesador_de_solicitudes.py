import asyncio
from uuid import UUID

import pytest

from app.application.errors import LlmNoDisponibleError, LlmTimeoutError
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
    assert publicador.publicados == [(SOLICITUD, registro)]
    assert publicador.errores == []


def test_una_solicitud_repetida_no_vuelve_a_consultar_al_llm():
    procesador, llm, repositorio, publicador = armar(CLASIFICACION_CRITICA)

    asyncio.run(procesador.procesar(SOLICITUD))
    asyncio.run(procesador.procesar(SOLICITUD))

    assert len(llm.pedidos) == 1
    assert len(repositorio.registros) == 1
    assert [registro for _, registro in publicador.publicados] == [repositorio.registros[0]] * 2


def test_si_falla_la_publicacion_el_reintento_reusa_lo_guardado():
    procesador, llm, repositorio, publicador = armar(CLASIFICACION_CRITICA)
    publicador.error = ConnectionError("broker caido")

    with pytest.raises(ConnectionError):
        asyncio.run(procesador.procesar(SOLICITUD))
    asyncio.run(procesador.procesar(SOLICITUD))

    assert len(llm.pedidos) == 1
    assert len(publicador.publicados) == 1


def test_si_el_llm_falla_responde_con_el_error_sin_guardar():
    error = LlmNoDisponibleError("groq respondio HTTP 503")
    procesador, _, repositorio, publicador = armar(error)

    asyncio.run(procesador.procesar(SOLICITUD))

    assert repositorio.registros == []
    assert publicador.publicados == []
    assert publicador.errores == [(SOLICITUD, error)]


def test_una_solicitud_que_fallo_se_puede_volver_a_pedir():
    procesador, llm, repositorio, publicador = armar(LlmTimeoutError(5000), CLASIFICACION_CRITICA)

    asyncio.run(procesador.procesar(SOLICITUD))
    asyncio.run(procesador.procesar(SOLICITUD))

    assert len(llm.pedidos) == 2
    assert len(publicador.errores) == 1
    assert publicador.publicados == [(SOLICITUD, repositorio.registros[0])]


def test_si_falla_la_publicacion_del_error_se_propaga_para_reintentar():
    procesador, _, _, publicador = armar(LlmTimeoutError(5000))
    publicador.error = ConnectionError("broker caido")

    with pytest.raises(ConnectionError):
        asyncio.run(procesador.procesar(SOLICITUD))
