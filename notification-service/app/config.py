"""Unica lectura del entorno del servicio."""

from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_NAME = "notification-service"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    mongo_uri: str = "mongodb://localhost:27017/notifications_db"
    mongo_db: str = "notifications_db"
    port: int = 8084
    log_level: str = "INFO"
    service_version: str = "0.1.0"


settings = Settings()
