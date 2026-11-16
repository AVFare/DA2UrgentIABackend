"""Exchanges, colas y bindings que usa el servicio (contracts/messaging/rabbitmq-definitions.json).

La topologia completa se carga en RabbitMQ desde el contrato. El servicio igual declara la parte
que usa, con los mismos argumentos (declarar algo que ya existe igual no hace nada), para poder
arrancar aunque todavia no se haya cargado.
"""

from dataclasses import dataclass

from aio_pika import ExchangeType
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractQueue

# Reintentos despues de la primera entrega; agotados, la solicitud va a la cola de mensajes muertos.
REENTREGAS_MAXIMAS = 5


@dataclass(frozen=True)
class Topologia:
    exchange: str = "urgentia.clasificacion"
    exchange_de_muertos: str = "urgentia.dlx"
    cola_de_solicitudes: str = "clasificacion.solicitudes"
    cola_de_muertos: str = "clasificacion.solicitudes.dlq"
    clave_solicitada: str = "ClasificacionSolicitada"
    clave_generada: str = "ClasificacionGenerada"

    def argumentos_de_la_cola(self) -> dict:
        return {
            "x-queue-type": "quorum",
            "x-delivery-limit": REENTREGAS_MAXIMAS,
            "x-dead-letter-exchange": self.exchange_de_muertos,
            "x-dead-letter-routing-key": self.cola_de_muertos,
        }

    async def declarar(self, canal: AbstractChannel) -> tuple[AbstractExchange, AbstractQueue]:
        """Declara todo (es idempotente) y devuelve el exchange y la cola de solicitudes."""
        exchange = await canal.declare_exchange(self.exchange, ExchangeType.DIRECT, durable=True)
        muertos = await canal.declare_exchange(self.exchange_de_muertos, ExchangeType.DIRECT, durable=True)

        cola_de_muertos = await canal.declare_queue(
            self.cola_de_muertos, durable=True, arguments={"x-queue-type": "quorum"}
        )
        await cola_de_muertos.bind(muertos, self.cola_de_muertos)

        solicitudes = await canal.declare_queue(
            self.cola_de_solicitudes, durable=True, arguments=self.argumentos_de_la_cola()
        )
        await solicitudes.bind(exchange, self.clave_solicitada)
        return exchange, solicitudes
