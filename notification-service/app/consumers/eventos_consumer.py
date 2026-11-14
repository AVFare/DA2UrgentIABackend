"""Consumidor de RabbitMQ de la cola notificaciones.eventos (contracts/messaging/).

Es una entrada mas, como el endpoint POST /api/eventos: valida el sobre con el mismo schema y
delega en EventoService. Sigue las reglas del contrato:
- ack recien despues de procesar (la notificacion ya quedo guardada);
- mensaje invalido: nack sin reencolar, va directo a la dead-letter;
- falla transitoria (Mongo caido, etc.): nack con reencolado; RabbitMQ lo manda a la dead-letter
  cuando se agotan los reintentos (x-delivery-limit), asi que aca no se cuentan.
"""

import logging
import threading
from collections.abc import Callable
from enum import StrEnum
from typing import Any

import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.exceptions import AMQPError
from pika.spec import Basic, BasicProperties
from pydantic import ValidationError
from pymongo.database import Database

from app.exceptions.api_exception import ApiException
from app.logging_config import correlation_id_var
from app.schemas.evento import InputEvento
from app.services.evento_service import EventoService

logger = logging.getLogger("consumidor")


class Decision(StrEnum):
    ACK = "ACK"
    RECHAZAR = "RECHAZAR"  # nack sin reencolar: dead-letter
    REINTENTAR = "REINTENTAR"  # nack con reencolado


def procesar_mensaje(cuerpo: bytes, db: Database) -> Decision:
    """Procesa un mensaje y decide que responderle al broker."""
    try:
        evento = InputEvento.model_validate_json(cuerpo)
    except ValidationError as error:
        campos = sorted({".".join(str(p) for p in e["loc"]) for e in error.errors()})
        logger.warning("Mensaje invalido: no cumple el esquema", extra={"campos": campos})
        return Decision.RECHAZAR

    try:
        resultado = EventoService(db).procesar(evento.model_dump(mode="json", by_alias=True))
    except ApiException as error:
        if error.status < 500:
            logger.warning(error.mensaje, extra={"eventId": str(evento.event_id), "detalles": error.detalles})
            return Decision.RECHAZAR
        logger.error(error.mensaje, extra={"eventId": str(evento.event_id)})
        return Decision.REINTENTAR
    except Exception:
        logger.exception("No se pudo procesar el evento", extra={"eventId": str(evento.event_id)})
        return Decision.REINTENTAR

    logger.info("Mensaje procesado", extra={"eventId": str(evento.event_id), "resultado": resultado})
    return Decision.ACK


class ConsumidorEventos:
    """Escucha la cola en un hilo propio y se reconecta solo si el broker se cae."""

    def __init__(
        self,
        url: str,
        cola: str,
        prefetch: int,
        obtener_db: Callable[[], Database],
        espera_reconexion: float = 5.0,
    ):
        self._parametros = pika.URLParameters(url)
        self._cola = cola
        self._prefetch = prefetch
        self._obtener_db = obtener_db
        self._espera_reconexion = espera_reconexion
        self._detener = threading.Event()
        self._hilo: threading.Thread | None = None
        self._conexion: pika.BlockingConnection | None = None
        self._canal: BlockingChannel | None = None

    def iniciar(self) -> None:
        self._hilo = threading.Thread(target=self._correr, name="consumidor-rabbitmq", daemon=True)
        self._hilo.start()

    def detener(self) -> None:
        self._detener.set()
        conexion, canal = self._conexion, self._canal
        if conexion is not None and canal is not None and conexion.is_open:
            # pika no es thread-safe: el corte se pide al hilo que maneja la conexion.
            conexion.add_callback_threadsafe(canal.stop_consuming)
        if self._hilo is not None:
            self._hilo.join(timeout=10)

    def _correr(self) -> None:
        while not self._detener.is_set():
            try:
                self._conexion = pika.BlockingConnection(self._parametros)
                self._canal = self._conexion.channel()
                self._canal.basic_qos(prefetch_count=self._prefetch)
                self._canal.basic_consume(queue=self._cola, on_message_callback=self._al_recibir)
                logger.info("Escuchando la cola", extra={"cola": self._cola})
                self._canal.start_consuming()
            except AMQPError as error:
                if not self._detener.is_set():
                    logger.warning(
                        "Sin conexion con RabbitMQ o sin la cola; se reintenta",
                        extra={"cola": self._cola, "error": repr(error), "esperaSegundos": self._espera_reconexion},
                    )
            finally:
                self._cerrar()
            self._detener.wait(self._espera_reconexion)
        logger.info("Consumidor detenido", extra={"cola": self._cola})

    def _cerrar(self) -> None:
        try:
            if self._conexion is not None and self._conexion.is_open:
                self._conexion.close()
        except AMQPError:
            pass
        self._conexion = None
        self._canal = None

    def _al_recibir(
        self, canal: BlockingChannel, entrega: Basic.Deliver, propiedades: BasicProperties, cuerpo: bytes
    ) -> None:
        # Hasta validar el sobre, los logs usan el correlation_id de las propiedades AMQP.
        token = correlation_id_var.set(propiedades.correlation_id)
        try:
            decision = procesar_mensaje(cuerpo, self._obtener_db())
            self._responder(canal, entrega, propiedades, decision)
        finally:
            correlation_id_var.reset(token)

    @staticmethod
    def _responder(
        canal: BlockingChannel, entrega: Basic.Deliver, propiedades: BasicProperties, decision: Decision
    ) -> None:
        if decision is Decision.ACK:
            canal.basic_ack(delivery_tag=entrega.delivery_tag)
            return
        extra: dict[str, Any] = {
            "messageId": propiedades.message_id,
            "entregasPrevias": (propiedades.headers or {}).get("x-delivery-count", 0),
        }
        reencolar = decision is Decision.REINTENTAR
        logger.warning("Mensaje reencolado" if reencolar else "Mensaje enviado a la dead-letter", extra=extra)
        canal.basic_nack(delivery_tag=entrega.delivery_tag, requeue=reencolar)
