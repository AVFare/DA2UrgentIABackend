"""Prueba del consumidor contra un RabbitMQ real con la topologia de contracts/messaging/ cargada.

Se saltea si no esta RABBITMQ_URL_TEST. Con el Compose levantado:
    RABBITMQ_URL_TEST=amqp://urgentia:rabbit_pass@localhost:5672 pytest tests/test_consumidor_rabbitmq.py
"""

import json
import os
import time

import pika
import pytest

from app.consumers.eventos_consumer import ConsumidorEventos
from tests.conftest import sobre

URL = os.getenv("RABBITMQ_URL_TEST")
COLA = "notificaciones.eventos"
DLQ = "notificaciones.eventos.dlq"

pytestmark = pytest.mark.skipif(not URL, reason="sin RABBITMQ_URL_TEST")


@pytest.fixture
def canal():
    conexion = pika.BlockingConnection(pika.URLParameters(URL))
    canal = conexion.channel()
    canal.queue_purge(COLA)
    canal.queue_purge(DLQ)
    yield canal
    conexion.close()


@pytest.fixture
def consumidor(mongo):
    consumidor = ConsumidorEventos(URL, COLA, 10, obtener_db=lambda: mongo, espera_reconexion=0.5)
    consumidor.iniciar()
    yield consumidor
    consumidor.detener()


def _publicar(canal, routing_key: str, cuerpo: bytes, message_id: str = "", correlation_id: str = "") -> None:
    canal.basic_publish(
        exchange="urgentia.eventos",
        routing_key=routing_key,
        body=cuerpo,
        properties=pika.BasicProperties(
            content_type="application/json",
            delivery_mode=2,
            message_id=message_id,
            correlation_id=correlation_id,
            type=routing_key,
        ),
    )


def _esperar(condicion, segundos: float = 10) -> bool:
    limite = time.monotonic() + segundos
    while time.monotonic() < limite:
        if condicion():
            return True
        time.sleep(0.2)
    return False


def _mensajes(canal, cola: str) -> int:
    return canal.queue_declare(cola, passive=True).method.message_count


def test_evento_publicado_en_el_exchange_genera_la_notificacion(canal, consumidor, mongo):
    evento = sobre("TicketEscalado")
    _publicar(canal, "TicketEscalado", json.dumps(evento).encode(), evento["eventId"], evento["correlationId"])

    assert _esperar(lambda: mongo.notificaciones.count_documents({"eventId": evento["eventId"]}) == 1)
    assert _esperar(lambda: _mensajes(canal, COLA) == 0)


def test_mensaje_invalido_termina_en_la_dead_letter(canal, consumidor, mongo):
    _publicar(canal, "TicketEscalado", b"esto no es un sobre")

    assert _esperar(lambda: _mensajes(canal, DLQ) == 1)
    assert mongo.notificaciones.count_documents({}) == 0
