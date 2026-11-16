# ADR-12: Transactional Outbox y clasificación por cola en ticket-service

**Estado:** aceptada. Reemplaza el envío por HTTP de ADR-07 (el sobre no cambia) y deja ADR-06 como plan B.

## Contexto

Con RabbitMQ (ADR-11), ticket-service tiene que publicar los eventos de dominio en `urgentia.eventos` y pedir la clasificación por `urgentia.clasificacion`. Publicar directo en el broker desde el caso de uso tiene dos problemas:

- **Doble escritura:** el ticket se guarda en PostgreSQL y el mensaje va a RabbitMQ. No hay una transacción que abarque los dos. Si se publica antes del commit y la transacción se deshace, sale un evento de algo que no pasó. Si se publica después y el broker está caído (o el servicio se cae justo entre las dos cosas), el evento se pierde.
- **El alta no puede depender del broker:** si RabbitMQ no responde, crear un ticket tiene que seguir funcionando.

Además, el TP pide que la clasificación con IA deje de bloquear el `POST /api/tickets` (hasta 7 s en la Entrega 1).

## Decisión

**Transactional Outbox:**

- Cada mensaje a publicar se guarda en la tabla `outbox` (Flyway V2) **en la misma transacción** que el ticket. La escribe el adaptador del puerto `EventPublisherPort` (`OutboxEventPublisherAdapter`), así que los casos de uso no cambian. La tabla guarda el sobre ya armado en JSON, el exchange, la routing key y el correlationId.
- `PublicadorDelOutbox` lee las filas pendientes cada 500 ms, en orden, y las publica con **publisher confirms** y `mandatory`. Recién cuando el broker confirma, marca la fila como publicada.
- Si una publicación falla, registra el error en la fila, corta la vuelta (para no desordenar los mensajes) y reintenta con espera creciente: 1 s, 2 s, 4 s y después cada 8 s. La fila sigue pendiente hasta que el broker la confirme.
- Reemplaza al `HttpEventSender` de la Entrega 1 y a la variable `EVENT_SUBSCRIBERS`.

**Clasificación por cola** (sección 10.5 del contexto), con la variable `CLASIFICACION_MODO`:

- `async` (por defecto): el alta deja el ticket en `PENDIENTE_CLASIFICACION`, guarda `TicketCreado` y un `ClasificacionSolicitada` en el outbox y responde 201 enseguida. Entra por un puerto nuevo, `SolicitudDeClasificacionPort`.
- El consumidor de `tickets.clasificaciones` (`ConsumidorDeClasificaciones`) recibe `ClasificacionGenerada` y la aplica con `AplicarClasificacionUseCase`. La regla es la del dominio, igual que por REST: `Ticket.aplicarClasificacion` calcula prioridad, SLA y escalamiento. El dominio no cambia.
- `sync`: el plan B. El alta llama a la IA por REST dentro del POST, como en ADR-06. Se cambia con una variable, sin tocar código.
- La reclasificación manual sigue por REST (`POST /api/tickets/{id}/reclasificacion`).

**Consumidor** (reglas de `contracts/messaging/README.md`):

- Ack manual después de aplicar la clasificación.
- Idempotente: los `eventId` procesados se guardan en `mensajes_procesados` (Flyway V3), en la misma transacción que el cambio del ticket.
- Mensaje que no cumple el contrato: `basic.reject` sin reencolar, va a la DLQ. Falla transitoria (base caída, choque de versiones): reject con reencolado, hasta el `x-delivery-limit`.
- Solo aplica la respuesta si el ticket sigue `PENDIENTE_CLASIFICACION`. Si un agente ya lo reclasificó por REST, la respuesta de la cola llega tarde y no lo pisa. Si el ticket no existe, se confirma sin efectos.

## Alternativas descartadas

- **Publicar después del commit** (`@TransactionalEventListener(AFTER_COMMIT)` con `RabbitTemplate`): es lo que hacía la Entrega 1 por HTTP. Si el broker está caído o el servicio se reinicia entre el commit y el envío, el evento se pierde. Es el recorte que el plan de la Entrega 2 dejaba si no llegábamos.
- **Transacciones distribuidas (XA o 2PC) entre PostgreSQL y RabbitMQ:** RabbitMQ no las soporta, y suman mucha complejidad para un TP.
- **Change Data Capture (Debezium leyendo el log de PostgreSQL):** resuelve lo mismo sin un publicador propio, pero agrega Kafka Connect y más infraestructura.
- **Una cola por suscriptor publicada desde el caso de uso:** acopla la aplicación al broker y repite la doble escritura.

## Consecuencias

- Con el broker caído, el alta sigue respondiendo 201 y no se pierde ningún evento: quedan en el outbox y salen cuando RabbitMQ vuelve. Lo prueba `PublicacionEnRabbitMqTest` con un RabbitMQ real (Testcontainers).
- La entrega es **al menos una vez**: si el broker confirma pero la base se cae antes de marcar la fila, el mensaje sale de nuevo. Los consumidores son idempotentes por `eventId`.
- Hay una demora entre el commit y la publicación (medio segundo en condiciones normales).
- El `POST /api/tickets` responde en milisegundos y en `PENDIENTE_CLASIFICACION`. La prioridad aparece cuando llega `ClasificacionGenerada`, y el front consulta hasta verla.
- El publicador está pensado para una sola instancia de ticket-service, como en el Compose. Con varias habría que tomar las filas con `SELECT ... FOR UPDATE SKIP LOCKED`.
- La tabla `outbox` crece. Para el TP alcanza; en producción se borrarían las filas publicadas viejas.
- `TicketCreado` sale con el estado `NUEVO`, como en la Entrega 1: el dominio no cambia y el estado pendiente lo informa el ticket por REST.
