"""OpenAPI del servicio, ajustado a las convenciones del proyecto.

- Saca las respuestas 422 que FastAPI agrega solo: la validacion responde 400 con el formato comun.
- Declara el esquema bearer, para que el Swagger unificado del gateway muestre el boton Authorize
  y mande el token (seccion 18 del contexto).
- El primer servidor es el gateway: "Try it out" desde el Swagger unificado pasa por el gateway.
"""

from typing import Any

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

SERVIDORES = [
    {"url": "http://localhost:8080", "description": "A traves del api-gateway (rol AGENTE o ADMIN)"},
    {"url": "http://localhost:8084", "description": "Directo al servicio (red interna)"},
]

# /health es publica y /api/eventos es de la red interna (el gateway no la rutea): no piden token.
RUTAS_SIN_TOKEN = {"/health", "/api/eventos"}


def configurar_openapi(app: FastAPI) -> None:
    def openapi() -> dict[str, Any]:
        if app.openapi_schema:
            return app.openapi_schema

        esquema = get_openapi(
            title=app.title,
            version=app.version,
            description=app.description,
            routes=app.routes,
            servers=SERVIDORES,
        )

        for ruta, operaciones in esquema.get("paths", {}).items():
            for operacion in operaciones.values():
                operacion.get("responses", {}).pop("422", None)
                if ruta in RUTAS_SIN_TOKEN:
                    operacion["security"] = []

        componentes = esquema.setdefault("components", {})
        schemas = componentes.get("schemas", {})
        schemas.pop("HTTPValidationError", None)
        schemas.pop("ValidationError", None)
        componentes["securitySchemes"] = {
            "bearer": {
                "type": "http",
                "scheme": "bearer",
                "bearerFormat": "JWT",
                "description": "Token de POST /api/auth/login (user-service). Lo valida el gateway.",
            }
        }
        esquema["security"] = [{"bearer": []}]

        app.openapi_schema = esquema
        return esquema

    app.openapi = openapi
