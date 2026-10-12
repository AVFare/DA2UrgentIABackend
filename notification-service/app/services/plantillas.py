"""Factory de plantillas: asunto y mensaje segun el tipo de evento.

Los asuntos son los del contrato (contexto, seccion 9.4). El evento llega como dict
en camelCase, igual que el sobre del contrato.
"""

from abc import ABC, abstractmethod
from typing import Any


def _sla(ticket: dict[str, Any]) -> str:
    return ticket.get("fechaLimiteSla") or "sin definir"


class Plantilla(ABC):
    @abstractmethod
    def generar(self, evento: dict[str, Any]) -> tuple[str, str]:
        """Devuelve (asunto, mensaje)."""


class PlantillaTicketEscalado(Plantilla):
    def generar(self, evento: dict[str, Any]) -> tuple[str, str]:
        ticket = evento["payload"]["ticket"]
        motivo = evento["payload"].get("motivo") or "sin motivo informado"
        return (
            f"[P1] Ticket escalado: {ticket['titulo']}",
            f"El ticket fue escalado. Motivo: {motivo}. SLA: {_sla(ticket)}",
        )


class PlantillaTicketAsignado(Plantilla):
    def generar(self, evento: dict[str, Any]) -> tuple[str, str]:
        ticket = evento["payload"]["ticket"]
        prioridad = ticket.get("prioridad") or "sin prioridad"
        return (
            f"Se te asignó el ticket: {ticket['titulo']}",
            f"Se te asignó el ticket {ticket['ticketId']}. Prioridad: {prioridad}. SLA: {_sla(ticket)}",
        )


class PlantillaTicketResuelto(Plantilla):
    def generar(self, evento: dict[str, Any]) -> tuple[str, str]:
        ticket = evento["payload"]["ticket"]
        return (
            f"Tu ticket fue resuelto: {ticket['titulo']}",
            f"Tu ticket {ticket['ticketId']} fue resuelto. Si el problema sigue, respondé para reabrirlo.",
        )


class PlantillaFactory:
    _plantillas: dict[str, Plantilla] = {
        "TicketEscalado": PlantillaTicketEscalado(),
        "TicketAsignado": PlantillaTicketAsignado(),
        "TicketResuelto": PlantillaTicketResuelto(),
    }

    @classmethod
    def para(cls, tipo_evento: str) -> Plantilla:
        plantilla = cls._plantillas.get(tipo_evento)
        if plantilla is None:
            raise ValueError(f"No hay plantilla para {tipo_evento}")
        return plantilla
