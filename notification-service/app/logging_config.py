"""Logs en una linea JSON: timestamp, level, service, correlationId y message (contexto, seccion 5)."""

import json
import logging
from contextvars import ContextVar
from datetime import UTC, datetime

from app.config import SERVICE_NAME, settings

correlation_id_var: ContextVar[str | None] = ContextVar("correlation_id", default=None)

_CAMPOS_ESTANDAR = set(logging.makeLogRecord({}).__dict__) | {"message", "asctime", "correlationId"}
_fabrica_original = logging.getLogRecordFactory()


def _fabrica_con_correlation_id(*args, **kwargs) -> logging.LogRecord:
    # Se toma al crear el registro, mientras el contextvar del request o del evento sigue vigente.
    record = _fabrica_original(*args, **kwargs)
    record.correlationId = correlation_id_var.get()
    return record


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        linea = {
            "timestamp": datetime.fromtimestamp(record.created, tz=UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "level": record.levelname,
            "service": SERVICE_NAME,
            "correlationId": getattr(record, "correlationId", None),
            "message": record.getMessage(),
        }
        linea.update({k: v for k, v in record.__dict__.items() if k not in _CAMPOS_ESTANDAR})
        if record.exc_info:
            linea["error"] = self.formatException(record.exc_info)
        return json.dumps(linea, ensure_ascii=False, default=str)


def configurar_logging() -> None:
    logging.setLogRecordFactory(_fabrica_con_correlation_id)
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    raiz = logging.getLogger()
    raiz.handlers = [handler]
    raiz.setLevel(settings.log_level)
    for nombre in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logging.getLogger(nombre).handlers = []
        logging.getLogger(nombre).propagate = True
