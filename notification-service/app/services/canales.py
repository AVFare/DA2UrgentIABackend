"""Strategy de canales: todos exponen enviar(notificacion) y devuelven el estado resultante."""

from abc import ABC, abstractmethod
from typing import Any

from app.providers.email_provider import EmailProvider

ENVIADA = "ENVIADA"
FALLIDA = "FALLIDA"


class Canal(ABC):
    tipo: str

    @abstractmethod
    def enviar(self, notificacion: dict[str, Any]) -> str: ...


class EmailSimulado(Canal):
    """Canal EMAIL_SIMULADO del contrato. EmailProvider decide si el mail sale de verdad (EMAIL_PROVIDER=real)
    o solo se loguea (simulado, el default)."""

    tipo = "EMAIL_SIMULADO"

    def enviar(self, notificacion: dict[str, Any]) -> str:
        enviado = EmailProvider.enviar(notificacion["destinatario"], notificacion["asunto"], notificacion["mensaje"])
        return ENVIADA if enviado else FALLIDA


class Interna(Canal):
    """La notificacion queda guardada y el agente la consulta por GET: no hay nada que enviar."""

    tipo = "INTERNA"

    def enviar(self, notificacion: dict[str, Any]) -> str:
        return ENVIADA


CANALES: dict[str, Canal] = {canal.tipo: canal for canal in (EmailSimulado(), Interna())}


def obtener_canal(tipo: str) -> Canal:
    return CANALES[tipo]
