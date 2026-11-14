# ADR-11: Mensajería con RabbitMQ

**Estado:** aceptada (reunión del 4/11). Reemplaza el transporte HTTP del ADR-07; el formato de los eventos no cambia.

## Contexto

En la Entrega 1, ticket-service manda cada evento por HTTP a cada suscriptor (`HttpEventSender`, ADR-07), con tres reintentos. Si un suscriptor está caído más de 7 segundos, el evento se pierde. El TP pide además una cola de mensajes con productores y consumidores, y la clasificación con IA tiene que dejar de bloquear el `POST /api/tickets`.

## Decisión

- **Broker: RabbitMQ 4** (`rabbitmq:4-management`), con la consola de administración en el puerto 15672.
- **Contract-first** (ADR-09): la topología está en `contracts/messaging/` (AsyncAPI 3.0 y `rabbitmq-definitions.json`), escrita antes del código.
- **Eventos de dominio** por un exchange **topic** `urgentia.eventos`, con **routing key = `eventType`**. Cada consumidor tiene su cola y elige qué eventos escuchar con sus bindings: notification-service, los tres que notifican; reporting-service, todos (`#`). El sobre y los nombres de los eventos son los de la Entrega 1.
- **Clasificación asincrónica** por un exchange **direct** privado `urgentia.clasificacion`: ticket-service publica `ClasificacionSolicitada` y classification-service responde `ClasificacionGenerada`, con el mismo sobre. Va aparte porque el pedido lleva la descripción del ticket, que no sale en los eventos de dominio.
- **Colas quorum** durables y mensajes persistentes.
- **Dead-letter:** cada cola de trabajo tiene `x-delivery-limit: 5` y manda a `urgentia.dlx` lo que se rechaza sin reencolar o lo que agota los reintentos. Cada cola tiene su `<cola>.dlq`.
- **Al menos una vez:** el productor usa publisher confirms (en ticket-service, desde el Outbox) y el consumidor hace ack después de procesar y es idempotente por `eventId`.

## Alternativas descartadas

- **Kafka:** guarda el historial y escala mejor, pero suma más infraestructura (KRaft o ZooKeeper, particiones, offsets por consumidor) para el volumen de un TP, y no trae dead-letter ni reintentos por mensaje: habría que armarlos a mano.
- **Seguir con HTTP:** no cumple la consigna, y un consumidor caído hace perder eventos.
- **Una sola cola para todos:** obliga a cada consumidor a filtrar lo que no le interesa y mezcla la descripción del ticket con los eventos públicos.
- **Colas clásicas con reintentos contados por el consumidor:** cada servicio tendría que llevar la cuenta en sus propios headers, en tres lenguajes distintos. Las colas quorum la llevan solas (`x-delivery-limit`).

## Consecuencias

- Un consumidor caído ya no hace perder eventos: los mensajes esperan en su cola y se procesan cuando vuelve.
- `POST /api/tickets` responde en `PENDIENTE_CLASIFICACION` y la prioridad aparece cuando llega `ClasificacionGenerada`; el front consulta hasta verla.
- Un servicio más en el Compose, del que dependen ticket-service, classification-service, notification-service y reporting-service.
- El orden entre eventos de un mismo ticket no está garantizado; los consumidores ya lo toleran (idempotencia y descarte de eventos viejos en reportes).
- Las DLQ hay que mirarlas: un mensaje ahí es un error a revisar a mano.
- `POST /api/eventos` de notification-service y reporting-service se quita cuando el broker esté estable (con P6).
