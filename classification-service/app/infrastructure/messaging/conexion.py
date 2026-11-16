"""Conexion a RabbitMQ compartida por el consumidor y el publicador.

La primera conexion se reintenta hasta que el broker responde; despues, si se corta,
aio-pika reconecta sola y restaura canales, colas y consumidores.
"""

import asyncio
import logging

import aio_pika
from aio_pika.abc import AbstractRobustConnection

log = logging.getLogger(__name__)

ESPERA_ENTRE_INTENTOS_S = 5.0


class ConexionRabbitMq:
    def __init__(self, url: str, espera_entre_intentos_s: float = ESPERA_ENTRE_INTENTOS_S) -> None:
        self._url = url
        self._espera_s = espera_entre_intentos_s
        self._conexion: AbstractRobustConnection | None = None
        self._candado = asyncio.Lock()

    async def obtener(self) -> AbstractRobustConnection:
        async with self._candado:
            while self._conexion is None:
                try:
                    self._conexion = await aio_pika.connect_robust(self._url)
                    log.info("Conectado a RabbitMQ")
                except (aio_pika.exceptions.AMQPError, OSError) as error:
                    log.warning("RabbitMQ no responde (%s); se reintenta en %.0f s", type(error).__name__,
                                self._espera_s)
                    await asyncio.sleep(self._espera_s)
            return self._conexion

    async def cerrar(self) -> None:
        if self._conexion is not None:
            await self._conexion.close()
            self._conexion = None
