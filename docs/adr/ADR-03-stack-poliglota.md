# ADR-03: Stack políglota, con un máximo de tres lenguajes

**Estado:** aceptada

## Contexto

Python es el ecosistema natural para el componente de IA, y la arquitectura de componentes tiene que mostrar que cada servicio es independiente de la tecnología de los demás.

## Decisión

- Java 21 con Spring Boot 3.5: `api-gateway`, `ticket-service` y `user-service`.
- Python 3.12 con FastAPI: `classification-service` y `notification-service`.
- Node 20 con NestJS: `reporting-service`.

No se suma un cuarto lenguaje.

## Consecuencias

- Cada servicio usa la herramienta que mejor le sirve. El único acoplamiento entre ellos son los contratos OpenAPI y el sobre de eventos.
- El CI corre tres toolchains: Maven, pytest y npm.
- Las convenciones comunes (formato de error, `X-Correlation-Id`, logs en JSON, `GET /health`) se implementan una vez por lenguaje.
