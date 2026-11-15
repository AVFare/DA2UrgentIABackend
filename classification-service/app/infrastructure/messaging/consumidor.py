"""Consume la cola de solicitudes de clasificacion de RabbitMQ.

Confirma cada mensaje despues de procesarlo: si el servicio se cae a mitad de camino, RabbitMQ
lo vuelve a entregar. Lo que no se pudo procesar vuelve a la cola, y al agotar las reentregas
RabbitMQ lo manda a la cola de mensajes muertos.
"""

import asyncio
import logging

from aio_pika.abc import AbstractIncomingMessage

from app.infrastructure.messaging.conexion import ConexionRabbitMq
from app.infrastructure.messaging.manejador import ManejadorDeSolicitudes, Resultado
from app.infrastructure.messaging.topologia import Topologia

log = logging.getLogger(__name__)


class ConsumidorDeSolicitudes:
    def __init__(self, conexion: ConexionRabbitMq, manejador: ManejadorDeSolicitudes, topologia: Topologia,
                 prefetch: int) -> None:
        self._conexion = conexion
        self._manejador = manejador
        self._topologia = topologia
        self._prefetch = prefetch

    async def ejecutar(self) -> None:
        """Consume hasta que se cancela la tarea."""
        conexion = await self._conexion.obtener()
        canal = await conexion.channel()
        # Como mucho `prefetch` solicitudes en proceso a la vez.
        await canal.set_qos(prefetch_count=self._prefetch)
        _, cola = await self._topologia.declarar(canal)
        await cola.consume(self._recibir)
        log.info("Consumiendo %s (hasta %d a la vez)", cola.name, self._prefetch)
        try:
            await asyncio.Future()
        finally:
            # Deja de consumir; lo que estaba en proceso sin confirmar vuelve a la cola.
            await canal.close()

    async def _recibir(self, mensaje: AbstractIncomingMessage) -> None:
        resultado = await self._manejador.manejar(mensaje.body)
        if resultado is Resultado.PROCESADA:
            await mensaje.ack()
        elif resultado is Resultado.DESCARTADA:
            await mensaje.reject(requeue=False)
        else:
            # reject y no nack: en RabbitMQ 4 un nack con requeue no cuenta para x-delivery-limit,
            # y la solicitud se reintentaria sin fin.
            await mensaje.reject(requeue=True)
