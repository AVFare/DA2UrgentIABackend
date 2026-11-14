"""Exchanges, colas y bindings de la mensajeria de clasificacion (contracts/messaging/clasificacion.md)."""

from dataclasses import dataclass

from aio_pika import ExchangeType
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractQueue

REENTREGAS_MAXIMAS = 3


@dataclass(frozen=True)
class Topologia:
    exchange: str = "urgentia.clasificacion"
    exchange_de_muertos: str = "urgentia.clasificacion.dlx"
    cola_de_solicitudes: str = "classification.solicitudes"
    cola_de_muertos: str = "classification.solicitudes.dlq"
    clave_solicitada: str = "clasificacion.solicitada"
    clave_generada: str = "clasificacion.generada"

    async def declarar(self, canal: AbstractChannel) -> tuple[AbstractExchange, AbstractQueue]:
        """Declara todo (es idempotente) y devuelve el exchange y la cola de solicitudes."""
        exchange = await canal.declare_exchange(self.exchange, ExchangeType.DIRECT, durable=True)
        muertos = await canal.declare_exchange(self.exchange_de_muertos, ExchangeType.DIRECT, durable=True)

        cola_de_muertos = await canal.declare_queue(
            self.cola_de_muertos, durable=True, arguments={"x-queue-type": "quorum"}
        )
        await cola_de_muertos.bind(muertos, self.clave_solicitada)

        solicitudes = await canal.declare_queue(
            self.cola_de_solicitudes,
            durable=True,
            arguments={
                "x-queue-type": "quorum",
                "x-delivery-limit": REENTREGAS_MAXIMAS,
                "x-dead-letter-exchange": self.exchange_de_muertos,
            },
        )
        await solicitudes.bind(exchange, self.clave_solicitada)
        return exchange, solicitudes
