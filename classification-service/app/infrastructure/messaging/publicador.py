"""Publica ClasificacionGenerada en RabbitMQ (implementa PublicadorDeClasificaciones)."""

import asyncio

from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractExchange

from app.domain.models import RegistroClasificacion
from app.infrastructure.messaging.conexion import ConexionRabbitMq
from app.infrastructure.messaging.mensajes import ClasificacionGenerada
from app.infrastructure.messaging.topologia import Topologia

TIMEOUT_PUBLICACION_S = 10.0


class RabbitMqPublicador:
    def __init__(self, conexion: ConexionRabbitMq, topologia: Topologia) -> None:
        self._conexion = conexion
        self._topologia = topologia
        self._exchange: AbstractExchange | None = None
        self._candado = asyncio.Lock()

    async def publicar(self, registro: RegistroClasificacion, correlation_id: str) -> None:
        mensaje = ClasificacionGenerada.desde_registro(registro, correlation_id)
        exchange = await self._obtener_exchange()
        # mandatory: si ninguna cola recibe el mensaje, falla en vez de perderlo.
        await exchange.publish(
            Message(
                mensaje.a_json(),
                content_type="application/json",
                delivery_mode=DeliveryMode.PERSISTENT,
                message_id=str(mensaje.event_id),
                correlation_id=correlation_id,
                type=mensaje.event_type,
            ),
            routing_key=self._topologia.clave_generada,
            mandatory=True,
            timeout=TIMEOUT_PUBLICACION_S,
        )

    async def _obtener_exchange(self) -> AbstractExchange:
        async with self._candado:
            if self._exchange is None:
                conexion = await self._conexion.obtener()
                # Con confirmaciones del broker: publish espera a que el mensaje quede aceptado.
                canal = await conexion.channel(publisher_confirms=True, on_return_raises=True)
                self._exchange, _ = await self._topologia.declarar(canal)
            return self._exchange
