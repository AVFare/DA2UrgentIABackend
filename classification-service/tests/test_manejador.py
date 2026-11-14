import asyncio
import json

from app.application.errors import LlmTimeoutError
from app.application.procesador_de_solicitudes import ProcesadorDeSolicitudes
from app.infrastructure.api.correlation import correlation_id_actual
from app.infrastructure.messaging.manejador import ManejadorDeSolicitudes, Resultado
from tests.fakes import CLASIFICACION_CRITICA, LlmFalso, PublicadorFalso, RepositorioEnMemoria
from tests.test_clasificacion_facade import crear_facade


def solicitud(**cambios) -> dict:
    mensaje = {
        "eventId": "0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b",
        "eventType": "ClasificacionSolicitada",
        "version": 1,
        "occurredAt": "2026-11-14T14:03:10Z",
        "correlationId": "correlacion-1",
        "source": "otro-servicio",
        "payload": {
            "ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "titulo": "No puede ingresar nadie",
            "descripcion": "Producción caída, todos los usuarios bloqueados",
        },
    }
    mensaje.update(cambios)
    return mensaje


def armar(*respuestas):
    repositorio = RepositorioEnMemoria()
    publicador = PublicadorFalso()
    procesador = ProcesadorDeSolicitudes(crear_facade(LlmFalso(*respuestas), repositorio), repositorio, publicador)
    return ManejadorDeSolicitudes(procesador), repositorio, publicador


def manejar(manejador, mensaje) -> Resultado:
    cuerpo = mensaje if isinstance(mensaje, bytes) else json.dumps(mensaje).encode()
    return asyncio.run(manejador.manejar(cuerpo))


def test_una_solicitud_valida_se_procesa_con_su_correlation_id():
    manejador, repositorio, publicador = armar(CLASIFICACION_CRITICA)

    assert manejar(manejador, solicitud()) is Resultado.PROCESADA
    assert str(repositorio.registros[0].solicitud_id) == "0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b"
    assert publicador.publicados[0][0].correlation_id == "correlacion-1"
    assert correlation_id_actual.get() == "-"


def test_lo_que_no_es_json_se_descarta():
    manejador, repositorio, _ = armar()

    assert manejar(manejador, b"esto no es JSON") is Resultado.DESCARTADA
    assert repositorio.registros == []


def test_un_mensaje_que_no_cumple_el_contrato_se_descarta():
    manejador, _, _ = armar()
    sin_descripcion = solicitud(payload={"ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "titulo": "Login caído"})

    assert manejar(manejador, sin_descripcion) is Resultado.DESCARTADA
    assert manejar(manejador, solicitud(eventType="TicketCreado")) is Resultado.DESCARTADA
    assert manejar(manejador, solicitud(eventId="no-es-uuid")) is Resultado.DESCARTADA
    assert manejar(manejador, solicitud(campoDeMas=1)) is Resultado.DESCARTADA


def test_si_el_llm_falla_se_responde_con_el_error_y_no_se_reintenta():
    manejador, _, publicador = armar(LlmTimeoutError(5000))

    assert manejar(manejador, solicitud()) is Resultado.PROCESADA
    assert publicador.publicados == []
    assert len(publicador.errores) == 1


def test_un_error_inesperado_tambien_se_reintenta():
    manejador, _, publicador = armar(CLASIFICACION_CRITICA)
    publicador.error = RuntimeError("sin conexion")

    assert manejar(manejador, solicitud()) is Resultado.REINTENTAR
