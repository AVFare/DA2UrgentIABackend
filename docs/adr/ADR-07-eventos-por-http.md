# ADR-07: Eventos de dominio con sobre estándar, enviados por HTTP

**Estado:** aceptada (el transporte cambia en la parte 2)

## Contexto

`notification-service` y `reporting-service` tienen que reaccionar a lo que le pasa al ticket sin que `ticket-service` los conozca. La parte 2 exige un broker de mensajes, pero la Primera Parte no.

## Decisión

- Todos los eventos usan el mismo sobre: `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `source` y `payload`. Está definido en `contracts/events/ticket-events.schema.json`.
- El payload lleva una foto del ticket sin la `descripcion`, porque puede tener datos sensibles.
- `ticket-service` publica después del commit (`@TransactionalEventListener(AFTER_COMMIT)`), de forma asíncrona, con `POST /api/eventos` a cada URL de `EVENT_SUBSCRIBERS`.
- Si un envío falla, se reintenta 3 veces (a los 1 s, 2 s y 4 s). Un 4xx no se reintenta.
- Los consumidores son idempotentes: guardan cada `eventId` en `eventos_procesados`, con índice único.

## Consecuencias

- En la parte 2 se cambia el transporte por RabbitMQ o Kafka sin cambiar el formato.
- La entrega es "al menos una vez": un evento puede llegar repetido o desordenado. `reporting-service` no pisa su vista con un evento más viejo (`occurredAt`).
- Si fallan todos los reintentos, el evento se pierde y queda un log `ERROR` con el `eventId`. Se acepta en la Primera Parte; en la parte 2 se resuelve con Transactional Outbox.
