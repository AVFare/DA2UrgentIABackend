"""Logs en una linea JSON por evento (CONTEXTO_PROYECTO.md, seccion 5):
timestamp, level, service, correlationId y message.
"""

import json
import logging
import sys
from datetime import datetime, timezone

from app.config import SERVICE_NAME
from app.infrastructure.api.correlation import correlation_id_actual


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        linea = {
            "timestamp": datetime.fromtimestamp(record.created, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "level": record.levelname,
            "service": SERVICE_NAME,
            "correlationId": correlation_id_actual.get(),
            "message": record.getMessage(),
        }
        if record.exc_info:
            linea["error"] = self.formatException(record.exc_info)
        return json.dumps(linea, ensure_ascii=False)


def configurar_logs(nivel: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    raiz = logging.getLogger()
    raiz.handlers[:] = [handler]
    raiz.setLevel(nivel)
