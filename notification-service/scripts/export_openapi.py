"""Genera contracts/notification-service.yaml a partir del OpenAPI de FastAPI.

Uso, desde notification-service/: python scripts/export_openapi.py
"""

import sys
from pathlib import Path

import yaml

RAIZ_SERVICIO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ_SERVICIO))

from app.main import app  # noqa: E402

destino = RAIZ_SERVICIO.parent / "contracts" / "notification-service.yaml"
destino.write_text(yaml.safe_dump(app.openapi(), sort_keys=False, allow_unicode=True), encoding="utf-8")
print(f"OpenAPI exportado a {destino}")
