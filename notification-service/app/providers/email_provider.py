import logging

logger = logging.getLogger("providers.email")


class EmailProvider:
    @staticmethod
    def enviar(destinatario: str, asunto: str, mensaje: str) -> bool:
        """
        Simula el envio de un email escribiendo una linea de log estructurado.
        Si despues hay SMTP real, solo cambia este archivo.

        Args:
            destinatario: "GRUPO:GUARDIA" o "USUARIO:{uuid}".
            asunto: asunto ya armado por la plantilla.
            mensaje: cuerpo ya armado por la plantilla.

        Response:
            True si se registro. Linea de log que genera:
            {"timestamp": "2026-10-05T14:03:12Z", "level": "INFO",
             "service": "notification-service", "correlationId": "a8e1b2c3-...",
             "message": "EMAIL_SIMULADO", "destinatario": "GRUPO:GUARDIA",
             "asunto": "[P1] Ticket escalado: No puede ingresar nadie",
             "mensaje": "El ticket fue escalado. Motivo: Prioridad P1. SLA: 2026-10-05T15:03:11Z"}
        """
        logger.info("EMAIL_SIMULADO", extra={"destinatario": destinatario, "asunto": asunto, "mensaje": mensaje})
        return True
