"""Test de integracion del consumidor y el publicador contra un RabbitMQ real.

Se corre solo si esta definida RABBITMQ_URL_TEST, por ejemplo con el RabbitMQ del Compose:
    RABBITMQ_URL_TEST="amqp://urgentia:rabbit_pass@localhost:5672" pytest tests/test_rabbitmq_worker.py
Cada test usa exchanges y colas con nombre propio y los borra al terminar.
"""

import asyncio
import json
import os
import uuid

import pytest
from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractIncomingMessage, AbstractQueue

from app.application.errors import LlmTimeoutError
from app.application.procesador_de_solicitudes import ProcesadorDeSolicitudes
from app.infrastructure.messaging.conexion import ConexionRabbitMq
from app.infrastructure.messaging.consumidor import ConsumidorDeSolicitudes
from app.infrastructure.messaging.manejador import ManejadorDeSolicitudes
from app.infrastructure.messaging.publicador import RabbitMqPublicador
from app.infrastructure.messaging.topologia import Topologia
from tests.fakes import CLASIFICACION_CRITICA, LlmFalso, RepositorioEnMemoria
from tests.test_clasificacion_facade import crear_facade
from tests.test_manejador import solicitud

RABBITMQ_URL_TEST = os.getenv("RABBITMQ_URL_TEST")
ESPERA_MAXIMA_S = 15.0
TIEMPO_MAXIMO_DEL_TEST_S = 60.0

pytestmark = pytest.mark.skipif(not RABBITMQ_URL_TEST, reason="Definir RABBITMQ_URL_TEST para correr contra RabbitMQ")


class Escenario:
    """El worker andando contra RabbitMQ, con una cola de prueba que recibe las respuestas."""

    def __init__(self, topologia: Topologia, exchange, respuestas: AbstractQueue | None, muertos: AbstractQueue,
                 solicitudes: AbstractQueue, llm: LlmFalso, repositorio: RepositorioEnMemoria) -> None:
        self.topologia = topologia
        self._exchange = exchange
        self.respuestas = respuestas
        self.muertos = muertos
        self.solicitudes = solicitudes
        self.llm = llm
        self.repositorio = repositorio

    async def publicar(self, cuerpo: dict | bytes) -> None:
        cuerpo = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo).encode()
        await self._exchange.publish(
            Message(cuerpo, delivery_mode=DeliveryMode.PERSISTENT), routing_key=self.topologia.clave_solicitada
        )

    @staticmethod
    async def esperar(cola: AbstractQueue) -> AbstractIncomingMessage:
        limite = asyncio.get_running_loop().time() + ESPERA_MAXIMA_S
        while asyncio.get_running_loop().time() < limite:
            mensaje = await cola.get(fail=False)
            if mensaje:
                await mensaje.ack()
                return mensaje
            await asyncio.sleep(0.1)
        raise AssertionError(f"No llego ningun mensaje a {cola.name}")

    async def pendientes(self) -> int:
        """Solicitudes que siguen en la cola, sin contar las que se estan procesando."""
        return (await self.solicitudes.channel.declare_queue(self.solicitudes.name, passive=True)).declaration_result.message_count


def con_worker(prueba, *respuestas_del_llm, con_cola_de_respuestas: bool = True):
    async def correr():
        sufijo = uuid.uuid4().hex[:8]
        topologia = Topologia(
            exchange=f"test.clasificacion.{sufijo}",
            exchange_de_muertos=f"test.clasificacion.dlx.{sufijo}",
            cola_de_solicitudes=f"test.solicitudes.{sufijo}",
            cola_de_muertos=f"test.solicitudes.dlq.{sufijo}",
        )
        llm = LlmFalso(*respuestas_del_llm)
        repositorio = RepositorioEnMemoria()
        conexion = ConexionRabbitMq(RABBITMQ_URL_TEST)
        procesador = ProcesadorDeSolicitudes(
            crear_facade(llm, repositorio), repositorio, RabbitMqPublicador(conexion, topologia)
        )
        consumidor = ConsumidorDeSolicitudes(conexion, ManejadorDeSolicitudes(procesador), topologia, prefetch=1)

        canal = await (await conexion.obtener()).channel()
        exchange, solicitudes = await topologia.declarar(canal)
        muertos = await canal.get_queue(topologia.cola_de_muertos)
        respuestas = None
        if con_cola_de_respuestas:
            respuestas = await canal.declare_queue(f"test.respuestas.{sufijo}", durable=True)
            await respuestas.bind(exchange, topologia.clave_generada)

        tarea = asyncio.create_task(consumidor.ejecutar())
        try:
            await prueba(Escenario(topologia, exchange, respuestas, muertos, solicitudes, llm, repositorio))
        finally:
            tarea.cancel()
            await asyncio.gather(tarea, return_exceptions=True)
            for cola in (respuestas, muertos, solicitudes):
                if cola:
                    await cola.delete(if_unused=False, if_empty=False)
            await canal.exchange_delete(topologia.exchange)
            await canal.exchange_delete(topologia.exchange_de_muertos)
            await conexion.cerrar()

    asyncio.run(asyncio.wait_for(correr(), TIEMPO_MAXIMO_DEL_TEST_S))


def test_consume_la_solicitud_y_publica_la_clasificacion_generada():
    async def prueba(escenario: Escenario):
        await escenario.publicar(solicitud())

        respuesta = await escenario.esperar(escenario.respuestas)
        cuerpo = json.loads(respuesta.body)

        assert cuerpo["eventType"] == "ClasificacionGenerada"
        assert cuerpo["correlationId"] == "correlacion-1"
        assert cuerpo["payload"]["ticketId"] == "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        assert cuerpo["payload"]["resultado"] == "CLASIFICADO"
        assert cuerpo["payload"]["clasificacion"]["categoria"] == "INCIDENTE"
        assert cuerpo["payload"]["clasificacion"]["id"] == escenario.repositorio.registros[0].id
        assert respuesta.content_type == "application/json"
        assert respuesta.content_encoding == "utf-8"
        assert respuesta.app_id == "classification-service"
        assert respuesta.timestamp is not None
        assert respuesta.delivery_mode == DeliveryMode.PERSISTENT
        assert respuesta.message_id == cuerpo["eventId"]
        assert respuesta.correlation_id == "correlacion-1"
        assert respuesta.type == "ClasificacionGenerada"
        assert await escenario.pendientes() == 0

    con_worker(prueba, CLASIFICACION_CRITICA)


def test_una_solicitud_repetida_publica_la_misma_respuesta_sin_volver_a_clasificar():
    async def prueba(escenario: Escenario):
        await escenario.publicar(solicitud())
        await escenario.publicar(solicitud())

        primera = json.loads((await escenario.esperar(escenario.respuestas)).body)
        segunda = json.loads((await escenario.esperar(escenario.respuestas)).body)

        assert primera == segunda
        assert len(escenario.llm.pedidos) == 1
        assert len(escenario.repositorio.registros) == 1

    con_worker(prueba, CLASIFICACION_CRITICA)


def test_si_el_llm_falla_responde_con_el_error_y_confirma_la_solicitud():
    async def prueba(escenario: Escenario):
        await escenario.publicar(solicitud())

        cuerpo = json.loads((await escenario.esperar(escenario.respuestas)).body)

        assert cuerpo["payload"]["resultado"] == "ERROR"
        assert cuerpo["payload"]["error"]["codigo"] == "LLM_TIMEOUT"
        assert len(escenario.llm.pedidos) == 1
        assert escenario.repositorio.registros == []
        assert await escenario.pendientes() == 0

    con_worker(prueba, LlmTimeoutError(5000))


def test_un_mensaje_que_no_cumple_el_contrato_va_directo_a_la_cola_de_mensajes_muertos():
    async def prueba(escenario: Escenario):
        await escenario.publicar(b"esto no es JSON")

        muerto = await escenario.esperar(escenario.muertos)

        assert muerto.body == b"esto no es JSON"
        assert muerto.headers["x-death"][0]["reason"] == "rejected"
        assert escenario.llm.pedidos == []

    con_worker(prueba)


def test_si_nadie_recibe_la_respuesta_la_solicitud_se_reintenta_y_no_se_pierde():
    async def prueba(escenario: Escenario):
        await escenario.publicar(solicitud())

        muerto = await escenario.esperar(escenario.muertos)

        assert json.loads(muerto.body)["eventId"] == "0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b"
        # Agotados los reintentos, RabbitMQ la manda sola a la cola de mensajes muertos.
        assert muerto.headers["x-death"][0]["reason"] == "delivery_limit"
        assert len(escenario.llm.pedidos) == 1
        assert len(escenario.repositorio.registros) == 1

    con_worker(prueba, CLASIFICACION_CRITICA, con_cola_de_respuestas=False)
