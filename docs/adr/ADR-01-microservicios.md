# ADR-01: Microservicios desde la primera entrega

**Estado:** aceptada

## Contexto

La entrega final del TP pide una arquitectura SOA o de microservicios, con servicios REST y SOAP y mensajería. Si la Primera Parte se hace como un monolito, después hay que partirlo.

## Decisión

Un servicio por bounded context desde la Primera Parte: `api-gateway`, `ticket-service`, `user-service`, `classification-service`, `notification-service` y `reporting-service`.

## Consecuencias

- La parte 2 cambia transportes y agrega servicios sin reescribir lo que ya existe.
- Cada integrante es dueño de un servicio.
- Hay más infraestructura desde el inicio: Compose, un Dockerfile por servicio y un Swagger agregado.
- Los servicios necesitan contratos acordados antes de programar (ADR-09).
