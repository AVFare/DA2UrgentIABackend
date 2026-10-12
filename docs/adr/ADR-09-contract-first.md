# ADR-09: Contract-first

**Estado:** aceptada

## Contexto

Seis personas desarrollan seis servicios en paralelo, y cada uno depende de las APIs de los otros.

## Decisión

- El OpenAPI de cada servicio vive en `contracts/` y el esquema de los eventos en `contracts/events/`. Los dos se escriben y se acuerdan antes del código.
- Nadie programa un endpoint que no esté en el contrato.
- Para cambiar un contrato, hay que avisar al grupo y obtener la aprobación del dueño del servicio que lo consume.
- Mientras tanto, cada uno desarrolla contra mocks del contrato (Prism o WireMock) o con `LLM_PROVIDER=mock`.

## Consecuencias

- Cada uno trabaja sin esperar a los demás.
- El Swagger agregado del gateway muestra contratos acordados.
- Los cambios de contrato requieren coordinación.
