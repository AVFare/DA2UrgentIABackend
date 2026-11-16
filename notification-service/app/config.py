"""Unica lectura del entorno del servicio."""

from typing import Literal

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_NAME = "notification-service"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    mongo_uri: str = "mongodb://localhost:27017/notifications_db"
    mongo_db: str = "notifications_db"
    port: int = 8084
    log_level: str = "INFO"
    service_version: str = "0.1.0"

    # RabbitMQ (contracts/messaging/): es la unica entrada de eventos. Sin URL no se levanta el
    # consumidor (los tests no la usan).
    rabbitmq_url: str | None = None
    rabbitmq_cola: str = "notificaciones.eventos"
    rabbitmq_prefetch: int = 10

    # Mail. "simulado" (default, para el CI) solo escribe un log; "real" manda por SMTP.
    email_provider: Literal["simulado", "real"] = "simulado"
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 587
    smtp_user: str | None = None
    smtp_password: SecretStr | None = None
    email_remitente: str | None = None
    email_destino_demo: str | None = None

    @model_validator(mode="after")
    def _mail_real_completo(self) -> "Settings":
        if self.email_provider == "real":
            clave = self.smtp_password.get_secret_value() if self.smtp_password else ""
            valores = {
                "smtp_user": self.smtp_user,
                "smtp_password": clave,
                "email_destino_demo": self.email_destino_demo,
            }
            faltan = [nombre.upper() for nombre, valor in valores.items() if not (valor or "").strip()]
            if faltan:
                raise ValueError(f"EMAIL_PROVIDER=real necesita {', '.join(faltan)}")
        return self


settings = Settings()
