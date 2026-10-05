"""OpenAPI del servicio, ajustado a las convenciones del proyecto.

- Saca las respuestas 422 que FastAPI agrega solo: el contrato usa 400 VALIDACION.
- Declara el esquema bearer para el boton Authorize del Swagger unificado (seccion 18).
"""

from typing import Any

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

DESCRIPCION = """\
Clasifica el texto de un ticket con un LLM (Model as a Service): categoria, urgencia, impacto,
modulo afectado, si requiere escalamiento, confianza y justificacion.
La IA sugiere y el dominio decide: este servicio nunca devuelve la prioridad.

Antes de llamar al LLM enmascara los datos personales (emails, telefonos y DNI). Lo que se
guarda ya esta enmascarado.

Es un servicio interno: desde afuera solo se accede a traves del api-gateway, con rol ADMIN.
"""

SERVIDORES = [
    {"url": "http://localhost:8080", "description": "A traves del api-gateway (rol ADMIN)"},
    {"url": "http://localhost:8082", "description": "Directo al servicio (red interna)"},
]


def generar_openapi(app: FastAPI) -> dict[str, Any]:
    if app.openapi_schema:
        return app.openapi_schema

    esquema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
        servers=SERVIDORES,
    )

    for operaciones in esquema.get("paths", {}).values():
        for operacion in operaciones.values():
            operacion.get("responses", {}).pop("422", None)

    componentes = esquema.setdefault("components", {})
    schemas = componentes.get("schemas", {})
    schemas.pop("HTTPValidationError", None)
    schemas.pop("ValidationError", None)
    componentes["securitySchemes"] = {
        "bearer": {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": "Token de POST /api/auth/login (user-service). Lo valida el gateway; solo ADMIN.",
        }
    }
    esquema["security"] = [{"bearer": []}]

    app.openapi_schema = esquema
    return esquema
