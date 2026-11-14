import logging
import smtplib
from email.message import EmailMessage

from app.config import settings

logger = logging.getLogger("providers.email")

TIMEOUT_SMTP_SEGUNDOS = 10


class EmailProvider:
    @staticmethod
    def enviar(destinatario: str, asunto: str, mensaje: str) -> bool:
        """
        Envia un email segun EMAIL_PROVIDER: "simulado" (default, para el CI) o "real" (SMTP).

        Args:
            destinatario: "GRUPO:GUARDIA" o "USUARIO:{uuid}".
            asunto: asunto ya armado por la plantilla.
            mensaje: cuerpo ya armado por la plantilla.

        Response:
            True si se envio (o se registro, en modo simulado). Si el servidor SMTP falla, lanza
            la excepcion: el canal la atrapa y la notificacion queda FALLIDA.
            Linea de log que genera:
            {"timestamp": "2026-10-05T14:03:12Z", "level": "INFO",
             "service": "notification-service", "correlationId": "a8e1b2c3-...",
             "message": "EMAIL_SIMULADO" | "EMAIL_ENVIADO", "destinatario": "GRUPO:GUARDIA",
             "asunto": "[P1] Ticket escalado: No puede ingresar nadie", ...}
        """
        if settings.email_provider == "real":
            return EmailProvider._enviar_smtp(destinatario, asunto, mensaje)
        logger.info("EMAIL_SIMULADO", extra={"destinatario": destinatario, "asunto": asunto, "mensaje": mensaje})
        return True

    @staticmethod
    def _enviar_smtp(destinatario: str, asunto: str, mensaje: str) -> bool:
        """
        Envia por SMTP con STARTTLS (Gmail: smtp.gmail.com:587 y una contrasena de aplicacion).

        Los usuarios de la demo no tienen casillas reales (@urgentia.local), asi que todo va a
        EMAIL_DESTINO_DEMO y el destinatario original queda al principio del asunto:
        "[GRUPO:GUARDIA] [P1] Ticket escalado: No puede ingresar nadie".
        """
        correo = EmailMessage()
        correo["From"] = settings.email_remitente or settings.smtp_user
        correo["To"] = settings.email_destino_demo
        correo["Subject"] = f"[{destinatario}] {asunto}"
        correo.set_content(f"{mensaje}\n\nDestinatario original: {destinatario}\n-- UrgentIA")

        with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=TIMEOUT_SMTP_SEGUNDOS) as smtp:
            smtp.starttls()
            # Google muestra la contrasena de aplicacion en grupos de 4 con espacios.
            smtp.login(settings.smtp_user, settings.smtp_password.get_secret_value().replace(" ", ""))
            smtp.send_message(correo)

        logger.info(
            "EMAIL_ENVIADO",
            extra={
                "destinatario": destinatario,
                "asunto": asunto,
                "para": settings.email_destino_demo,
                "servidor": f"{settings.smtp_host}:{settings.smtp_port}",
            },
        )
        return True
