import json
import socket
from types import SimpleNamespace

import pytest

from app.consumers.eventos_consumer import ConsumidorEventos, Decision, procesar_mensaje
from app.logging_config import JsonFormatter
from app.repositories.notificacion_repository import NotificacionRepository
from tests.conftest import sobre


def _cuerpo(datos: dict) -> bytes:
    return json.dumps(datos).encode()


def test_escalado_registra_una_notificacion_a_la_guardia(mongo, caplog):
    caplog.set_level("INFO")
    evento = sobre("TicketEscalado")

    assert procesar_mensaje(_cuerpo(evento), mongo) is Decision.ACK

    guardada = mongo.notificaciones.find_one()
    assert guardada["destinatario"] == "GRUPO:GUARDIA"
    assert guardada["canal"] == "EMAIL_SIMULADO"
    assert guardada["estado"] == "ENVIADA"
    assert guardada["asunto"] == "[P1] Ticket escalado: No puede ingresar nadie"
    # El email simulado se loguea con el correlationId del sobre.
    log = next(json.loads(JsonFormatter().format(r)) for r in caplog.records if r.name == "providers.email")
    assert log["correlationId"] == evento["correlationId"]


def test_mensaje_repetido_se_confirma_sin_duplicar(mongo):
    cuerpo = _cuerpo(sobre("TicketEscalado"))

    assert procesar_mensaje(cuerpo, mongo) is Decision.ACK
    assert procesar_mensaje(cuerpo, mongo) is Decision.ACK
    assert mongo.notificaciones.count_documents({}) == 1


def test_evento_que_no_notifica_se_confirma(mongo):
    assert procesar_mensaje(_cuerpo(sobre("TicketCreado", estado="NUEVO")), mongo) is Decision.ACK
    assert mongo.notificaciones.count_documents({}) == 0


@pytest.mark.parametrize("cuerpo", [b"no es json", b"{}", _cuerpo({**sobre(), "eventType": "TicketInventado"})])
def test_mensaje_invalido_va_a_la_dead_letter(mongo, cuerpo):
    assert procesar_mensaje(cuerpo, mongo) is Decision.RECHAZAR
    assert mongo.eventos_procesados.count_documents({}) == 0


def test_asignado_sin_agente_va_a_la_dead_letter_sin_marcar_el_evento(mongo):
    cuerpo = _cuerpo(sobre("TicketAsignado", estado="ASIGNADO", agenteAsignadoId=None))

    assert procesar_mensaje(cuerpo, mongo) is Decision.RECHAZAR
    assert mongo.eventos_procesados.count_documents({}) == 0


def test_falla_transitoria_se_reintenta_y_libera_el_evento(mongo, monkeypatch):
    def falla(self, documento):
        raise RuntimeError("mongo caído")

    monkeypatch.setattr(NotificacionRepository, "insert", falla)

    assert procesar_mensaje(_cuerpo(sobre("TicketEscalado")), mongo) is Decision.REINTENTAR
    assert mongo.eventos_procesados.count_documents({}) == 0


class CanalFalso:
    def __init__(self):
        self.respuestas = []

    def basic_ack(self, delivery_tag):
        self.respuestas.append(("ack", delivery_tag))

    def basic_reject(self, delivery_tag, requeue):
        self.respuestas.append(("reject", delivery_tag, requeue))


@pytest.mark.parametrize(
    "decision, respuesta",
    [
        (Decision.ACK, ("ack", 7)),
        (Decision.RECHAZAR, ("reject", 7, False)),
        (Decision.REINTENTAR, ("reject", 7, True)),
    ],
)
def test_cada_decision_se_traduce_en_la_respuesta_al_broker(decision, respuesta):
    canal = CanalFalso()
    entrega = SimpleNamespace(delivery_tag=7)
    propiedades = SimpleNamespace(message_id="m-1", headers={"x-delivery-count": 2}, correlation_id="c-1")

    ConsumidorEventos._responder(canal, entrega, propiedades, decision)

    assert canal.respuestas == [respuesta]


def test_los_logs_del_mensaje_llevan_el_correlation_id_del_sobre(mongo, caplog):
    caplog.set_level("INFO")
    evento = sobre("TicketEscalado")
    consumidor = ConsumidorEventos("amqp://x:y@localhost:5672", "notificaciones.eventos", 10, obtener_db=lambda: mongo)
    canal = CanalFalso()

    consumidor._al_recibir(
        canal,
        SimpleNamespace(delivery_tag=1),
        SimpleNamespace(message_id=evento["eventId"], headers=None, correlation_id=evento["correlationId"]),
        _cuerpo(evento),
    )

    assert canal.respuestas == [("ack", 1)]
    procesado = next(r for r in caplog.records if r.getMessage() == "Mensaje procesado")
    assert procesado.correlationId == evento["correlationId"]


def test_si_el_nombre_del_broker_no_resuelve_sigue_reintentando(mongo, monkeypatch):
    consumidor = ConsumidorEventos(
        "amqp://x:y@rabbitmq:5672", "notificaciones.eventos", 10, obtener_db=lambda: mongo, espera_reconexion=0
    )
    intentos = []

    def conexion_que_falla(parametros):
        intentos.append(parametros)
        if len(intentos) == 3:
            consumidor._detener.set()
        raise socket.gaierror(-2, "Name or service not known")

    monkeypatch.setattr("app.consumers.eventos_consumer.pika.BlockingConnection", conexion_que_falla)

    consumidor._correr()

    assert len(intentos) == 3
