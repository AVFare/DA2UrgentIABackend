"""Recepcion de eventos: idempotencia y reglas evento -> destinatario y canal.

Del lado consumidor, el servicio es un observador de los eventos del ticket:
para sumar un evento que notifique se agrega una entrada en REGLAS, sin tocar ifs.
"""

import logging
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from pymongo.database import Database

from app.exceptions.api_exception import ApiException
from app.logging_config import correlation_id_var
from app.repositories.evento_procesado_repository import EventoProcesadoRepository
from app.services.notificacion_service import NotificacionService

logger = logging.getLogger("eventos")

PROCESADO = "PROCESADO"
DUPLICADO = "DUPLICADO"
IGNORADO = "IGNORADO"
GRUPO_GUARDIA = "GRUPO:GUARDIA"


@dataclass(frozen=True)
class Regla:
    canal: str
    destinatario: Callable[[dict[str, Any]], str]


def _guardia(evento: dict[str, Any]) -> str:
    return GRUPO_GUARDIA


def _agente_asignado(evento: dict[str, Any]) -> str:
    agente = evento["payload"]["ticket"].get("agenteAsignadoId")
    if not agente:
        raise ApiException(
            status=400,
            codigo="VALIDACION",
            mensaje="La solicitud no es válida",
            detalles=[{"campo": "payload.ticket.agenteAsignadoId", "mensaje": "TicketAsignado requiere agente"}],
        )
    return f"USUARIO:{agente}"


def _solicitante(evento: dict[str, Any]) -> str:
    return f"USUARIO:{evento['payload']['ticket']['solicitanteId']}"


# Contexto, secciones 9.4 y 10.2. Cualquier otro evento responde IGNORADO.
REGLAS: dict[str, Regla] = {
    "TicketEscalado": Regla(canal="EMAIL_SIMULADO", destinatario=_guardia),
    "TicketAsignado": Regla(canal="INTERNA", destinatario=_agente_asignado),
    "TicketResuelto": Regla(canal="EMAIL_SIMULADO", destinatario=_solicitante),
}


def resolver_destino(evento: dict[str, Any]) -> tuple[str, str] | None:
    """Devuelve (destinatario, canal), o None si el evento no notifica."""
    regla = REGLAS.get(evento["eventType"])
    if regla is None:
        return None
    return regla.destinatario(evento), regla.canal


class EventoService:
    def __init__(self, db: Database):
        self.repository = EventoProcesadoRepository(db)
        self.notificacion_service = NotificacionService(db)

    def procesar(self, evento: dict[str, Any]) -> str:
        """Devuelve PROCESADO, DUPLICADO o IGNORADO."""
        # Todo lo que se loguee mientras se procesa (incluido el email simulado) lleva el correlationId del sobre.
        token = correlation_id_var.set(evento["correlationId"])
        try:
            return self._procesar(evento)
        finally:
            correlation_id_var.reset(token)

    def _procesar(self, evento: dict[str, Any]) -> str:
        event_id, event_type = evento["eventId"], evento["eventType"]

        # La entrega es "al menos una vez": el indice unico en eventId evita procesar dos veces.
        if not self.repository.registrar(event_id, event_type):
            logger.info("Evento duplicado", extra={"eventId": event_id, "eventType": event_type})
            return DUPLICADO

        try:
            resultado = self._aplicar_regla(evento)
            self.repository.actualizar_resultado(event_id, resultado)
        except Exception:
            # Si fallo a mitad de camino se libera la marca para que el reintento de ticket-service lo procese.
            self.repository.eliminar(event_id)
            raise

        logger.info(f"Evento {resultado.lower()}", extra={"eventId": event_id, "eventType": event_type})
        return resultado

    def _aplicar_regla(self, evento: dict[str, Any]) -> str:
        destino = resolver_destino(evento)
        if destino is None:
            return IGNORADO
        destinatario, canal = destino
        self.notificacion_service.crear_desde_evento(evento, destinatario, canal)
        return PROCESADO
