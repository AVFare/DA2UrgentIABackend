# Contrato de mensajería (RabbitMQ)

Dueño: P5. Lo consumen P1 (Compose), P2 (ticket-service), P4 (classification-service), P5 (notification-service) y P6 (reporting-service). Cambiarlo sigue la regla de contract-first: aviso en el grupo y aprobación de quien lo consume.

| Archivo | Qué es |
|---|---|
| [`asyncapi.yaml`](asyncapi.yaml) | El contrato (AsyncAPI 3.0): exchanges, colas, quién publica y quién consume cada mensaje |
| [`rabbitmq-definitions.json`](rabbitmq-definitions.json) | La topología lista para cargar en RabbitMQ (exchanges, colas y bindings) |
| [`../events/ticket-events.schema.json`](../events/ticket-events.schema.json) | Sobre y payload de los 6 eventos de dominio (no cambia desde la Entrega 1) |
| [`../events/clasificacion-events.schema.json`](../events/clasificacion-events.schema.json) | `ClasificacionSolicitada` y `ClasificacionGenerada`, con el mismo sobre |

## Topología

```
ticket-service ──(TicketCreado … TicketResuelto)──▶ urgentia.eventos (topic, routing key = eventType)
                                                      ├─ TicketEscalado | TicketAsignado | TicketResuelto ─▶ notificaciones.eventos ─▶ notification-service
                                                      └─ #  (todos) ──────────────────────────────────────▶ reportes.eventos ──────▶ reporting-service

ticket-service ──(ClasificacionSolicitada)──▶ urgentia.clasificacion (direct) ─▶ clasificacion.solicitudes ─▶ classification-service
classification-service ──(ClasificacionGenerada)──▶ urgentia.clasificacion ───▶ tickets.clasificaciones ──▶ ticket-service

cada cola ──(rechazo sin reencolar o 5 reintentos agotados)──▶ urgentia.dlx (direct) ─▶ <cola>.dlq
```

| Exchange | Tipo | Routing key | Publica |
|---|---|---|---|
| `urgentia.eventos` | topic | `eventType` (`TicketCreado`, `TicketEscalado`, …) | ticket-service |
| `urgentia.clasificacion` | direct | `ClasificacionSolicitada` / `ClasificacionGenerada` | ticket-service / classification-service |
| `urgentia.dlx` | direct | nombre de la DLQ | RabbitMQ (dead-letter) |

| Cola | Bindings | Consume | DLQ |
|---|---|---|---|
| `notificaciones.eventos` | `urgentia.eventos`: `TicketEscalado`, `TicketAsignado`, `TicketResuelto` | notification-service | `notificaciones.eventos.dlq` |
| `reportes.eventos` | `urgentia.eventos`: `#` | reporting-service | `reportes.eventos.dlq` |
| `clasificacion.solicitudes` | `urgentia.clasificacion`: `ClasificacionSolicitada` | classification-service | `clasificacion.solicitudes.dlq` |
| `tickets.clasificaciones` | `urgentia.clasificacion`: `ClasificacionGenerada` | ticket-service | `tickets.clasificaciones.dlq` |

Todas las colas son **quorum** y durables. Las colas de trabajo tienen `x-delivery-limit: 5` y `x-dead-letter-exchange: urgentia.dlx`.

- La clasificación va por un exchange aparte porque `ClasificacionSolicitada` lleva la **descripción** del ticket, que nunca sale en los eventos de dominio (ADR-07). Así reporting-service, que escucha `#`, no la recibe.
- notification-service solo enlaza los tres eventos que notifican; los otros ni le llegan.

## Reglas para quien publica

1. **Cuerpo:** el sobre JSON del esquema, en UTF-8. Mismos campos y nombres que en la Entrega 1.
2. **Routing key:** el `eventType`.
3. **Propiedades AMQP** (las usa la consola de RabbitMQ y ayudan a depurar):

   | Propiedad | Valor |
   |---|---|
   | `content_type` | `application/json` |
   | `content_encoding` | `utf-8` |
   | `delivery_mode` | `2` (persistente) |
   | `message_id` | `eventId` |
   | `correlation_id` | `correlationId` |
   | `type` | `eventType` |
   | `app_id` | `source` |
   | `timestamp` | `occurredAt`, en segundos Unix |

4. **Publisher confirms** activados y `mandatory=true`: el mensaje se da por enviado recién cuando el broker lo confirma. En ticket-service esto lo hace el publicador del Outbox, que marca la fila como enviada después del confirm.

## Reglas para quien consume

1. **Ack manual, después de procesar** (guardar en la base). Nunca auto-ack.
2. **Idempotencia por `eventId`:** la entrega es "al menos una vez". Un mensaje repetido se confirma (ack) sin efectos. notification-service y reporting-service ya tienen su colección `eventos_procesados`.
3. **Mensaje inválido** (no cumple el esquema): `nack` **sin** reencolar → va directo a la DLQ. Reintentarlo no lo arregla.
4. **Falla transitoria** (base caída, timeout): `nack` **con** reencolado. RabbitMQ cuenta las entregas y, cuando se agotan los 5 reintentos (6 entregas en total), lo manda solo a la DLQ. El consumidor no lleva la cuenta.
5. **`prefetch` = 10** como punto de partida.
6. **Logs con el `correlationId`** del sobre en cada línea mientras se procesa el mensaje.
7. **Orden:** no está garantizado entre eventos de un mismo ticket. reporting-service descarta los eventos más viejos que su proyección.

Una DLQ con mensajes es una alarma: se revisa a mano en la consola (`http://localhost:15672`), se corrige y, si corresponde, se reenvía.

## Cargar la topología en el Compose (para P1)

**Ojo:** si RabbitMQ carga definiciones **al arrancar** (`load_definitions` en `rabbitmq.conf`), **no crea el usuario por defecto** (`RABBITMQ_DEFAULT_USER`), porque el archivo no trae usuarios, y nadie se puede conectar. En el log aparece: `Will not seed default virtual host and user: have definitions to load`.

Lo que sí funciona, probado con `rabbitmq:4-management`:

1. RabbitMQ arranca con `RABBITMQ_DEFAULT_USER` y `RABBITMQ_DEFAULT_PASS` del `.env`, sin `load_definitions`.
2. Un servicio de un solo uso, cuando RabbitMQ está sano, carga el archivo por la API de administración:
   ```bash
   curl -fsS -u "$RABBITMQ_USER:$RABBITMQ_PASS" -H 'content-type: application/json' \
        -X POST http://rabbitmq:15672/api/definitions -d @/definitions.json
   ```
   Responde `204`. Es idempotente: se puede correr en cada arranque.
3. Los servicios que publican o consumen dependen de ese servicio con `condition: service_completed_successfully`.

Los usuarios y las contraseñas van solo en el `.env`, nunca en `rabbitmq-definitions.json`.

En Linux con SELinux (Fedora), los archivos montados en el contenedor necesitan el sufijo `:z` (`./contracts/messaging/rabbitmq-definitions.json:/definitions.json:ro,z`).

## Comportamiento verificado

Con la topología cargada en un `rabbitmq:4-management` local:

| Prueba | Resultado |
|---|---|
| Publicar `TicketEscalado` en `urgentia.eventos` | Llega a `notificaciones.eventos` y a `reportes.eventos` |
| Publicar `TicketCreado` | Llega solo a `reportes.eventos` |
| Publicar `ClasificacionSolicitada` en `urgentia.clasificacion` | Llega a `clasificacion.solicitudes` |
| Rechazar sin reencolar | El mensaje pasa a `<cola>.dlq` |
| Rechazar con reencolado 6 veces | Las entregas 2 a 6 traen `x-delivery-count` 1 a 5; después pasa solo a `<cola>.dlq` |
