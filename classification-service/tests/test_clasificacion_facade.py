import asyncio
from datetime import datetime, timezone
from uuid import UUID

import pytest

from app.application.clasificacion_facade import ClasificacionFacade, armar_prompt
from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError, LlmTimeoutError
from tests.fakes import CLASIFICACION_CRITICA, LlmFalso, RelojFalso, RepositorioEnMemoria

TICKET_ID = UUID("7c9e6679-7425-40de-944b-e07fc1f90ae7")
AHORA = datetime(2026, 10, 5, 14, 3, 11, 999, tzinfo=timezone.utc)
PLANTILLA = "Título: {titulo}\nDescripción: {descripcion}"


def crear_facade(llm, repositorio=None, reloj=None, timeout_llm_s=5.0) -> ClasificacionFacade:
    return ClasificacionFacade(
        llm=llm,
        repositorio=repositorio or RepositorioEnMemoria(),
        plantilla=PLANTILLA,
        version_prompt="v1",
        timeout_llm_s=timeout_llm_s,
        reloj=reloj or RelojFalso(),
        ahora=lambda: AHORA,
    )


def clasificar(facade, titulo="No puede ingresar nadie", descripcion="Producción caída para todos"):
    return asyncio.run(facade.clasificar(TICKET_ID, titulo, descripcion))


def test_arma_el_prompt_reemplazando_los_marcadores():
    assert armar_prompt(PLANTILLA, "Login", "No anda") == "Título: Login\nDescripción: No anda"


def test_un_marcador_dentro_del_texto_del_usuario_no_se_reemplaza():
    prompt = armar_prompt(PLANTILLA, "Dice {descripcion}", "texto")

    assert prompt == "Título: Dice {descripcion}\nDescripción: texto"


def test_clasifica_guarda_y_devuelve_el_registro_completo():
    reloj = RelojFalso()
    repositorio = RepositorioEnMemoria()
    llm = LlmFalso(CLASIFICACION_CRITICA, reloj=reloj, segundos_por_consulta=0.812)

    registro = clasificar(crear_facade(llm, repositorio, reloj))

    assert registro.id is not None
    assert registro.ticket_id == TICKET_ID
    assert registro.clasificacion == CLASIFICACION_CRITICA
    assert (registro.proveedor, registro.modelo, registro.version_prompt) == ("falso", "modelo-falso", "v1")
    assert registro.latencia_ms == 812
    assert registro.fecha == AHORA.replace(microsecond=0)
    assert repositorio.registros == [registro]


def test_enmascara_antes_de_llamar_al_llm_y_de_guardar():
    llm = LlmFalso(CLASIFICACION_CRITICA)
    descripcion = "Soy Juan, mi mail es juan.perez@empresa.com y mi celular 11-5555-1234, DNI 30.123.456"

    registro = clasificar(crear_facade(llm), titulo="Ayuda juan.perez@empresa.com", descripcion=descripcion)

    enviado = llm.pedidos[0]
    guardado = registro.texto_enmascarado + registro.respuesta_cruda
    for dato in ("juan.perez@empresa.com", "11-5555-1234", "30.123.456"):
        assert dato not in enviado.prompt
        assert dato not in enviado.titulo + enviado.descripcion
        assert dato not in guardado
    assert "[EMAIL]" in enviado.prompt and "[TELEFONO]" in enviado.prompt and "[DNI]" in enviado.prompt


def test_reintenta_una_vez_si_la_respuesta_es_invalida_y_queda_tiempo():
    reloj = RelojFalso()
    llm = LlmFalso(LlmRespuestaInvalidaError("urgencia fuera de la lista"), CLASIFICACION_CRITICA,
                   reloj=reloj, segundos_por_consulta=1.5)

    registro = clasificar(crear_facade(llm, reloj=reloj))

    assert len(llm.pedidos) == 2
    assert registro.latencia_ms == 3000


def test_no_reintenta_si_quedan_menos_de_2_segundos_del_presupuesto():
    reloj = RelojFalso()
    llm = LlmFalso(LlmRespuestaInvalidaError("JSON invalido"), CLASIFICACION_CRITICA,
                   reloj=reloj, segundos_por_consulta=4.5)

    with pytest.raises(LlmRespuestaInvalidaError):
        clasificar(crear_facade(llm, reloj=reloj))
    assert len(llm.pedidos) == 1


def test_reintenta_una_sola_vez():
    llm = LlmFalso(LlmRespuestaInvalidaError("primera"), LlmRespuestaInvalidaError("segunda"))
    repositorio = RepositorioEnMemoria()

    with pytest.raises(LlmRespuestaInvalidaError, match="segunda"):
        clasificar(crear_facade(llm, repositorio))
    assert len(llm.pedidos) == 2
    assert repositorio.registros == []


def test_si_el_llm_tarda_mas_que_el_timeout_lanza_timeout():
    llm = LlmFalso(CLASIFICACION_CRITICA, demora_real_s=0.5)
    repositorio = RepositorioEnMemoria()

    with pytest.raises(LlmTimeoutError) as error:
        clasificar(crear_facade(llm, repositorio, timeout_llm_s=0.05))
    assert error.value.timeout_ms == 50
    assert repositorio.registros == []


def test_si_el_llm_no_esta_disponible_no_reintenta():
    llm = LlmFalso(LlmNoDisponibleError("HTTP 500"), CLASIFICACION_CRITICA)

    with pytest.raises(LlmNoDisponibleError):
        clasificar(crear_facade(llm))
    assert len(llm.pedidos) == 1


def test_lista_las_clasificaciones_del_repositorio():
    repositorio = RepositorioEnMemoria()
    facade = crear_facade(LlmFalso(CLASIFICACION_CRITICA, CLASIFICACION_CRITICA), repositorio)
    clasificar(facade)
    clasificar(facade)

    pagina = asyncio.run(facade.listar(TICKET_ID, page=0, size=1))

    assert pagina.total == 2
    assert pagina.total_pages == 2
    assert len(pagina.elementos) == 1
