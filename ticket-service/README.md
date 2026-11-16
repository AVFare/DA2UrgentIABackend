# ticket-service

**Core Domain** de UrgentIA. Recibe los tickets, le pide a la IA que los clasifique y, con
eso, el dominio calcula la prioridad (P1 a P4), el SLA y decide si hay que escalar. También
maneja la asignación y los cambios de estado, y publica en RabbitMQ los eventos que consumen
notification-service y reporting-service.

> La IA **sugiere** (categoría, urgencia, impacto, módulo) y el dominio **decide** (prioridad,
> SLA, escalamiento). La IA nunca devuelve la prioridad.

- **Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, Spring AMQP (RabbitMQ), Maven.
- **Puerto:** 8081 (no se publica: se entra por el gateway en 8080).
- **Base de datos:** PostgreSQL `tickets_db` (usuario `tickets_user`).
- **Dueño:** P2 · revisa PRs: P3.

## Cómo correrlo

### Con Docker Compose (lo normal)

Desde la raíz del repositorio:

```
cp .env.example .env
docker compose up -d --build --wait
```

Levanta todo, RabbitMQ incluido, con la topología de `contracts/messaging/` ya cargada. Con
`CLASIFICACION_MODO=async` (el valor de `.env.example`), un ticket nuevo responde
`PENDIENTE_CLASIFICACION` y en uno o dos segundos aparece clasificado (`GET /api/tickets/{id}`).

### Desde el IDE

1. Levantar la base y el broker: `docker compose up -d postgres rabbitmq rabbitmq-topologia`.
2. Abrir `ticket-service/pom.xml` como proyecto Maven, con JDK 21.
3. En la configuración de ejecución de `TicketServiceApplication`, agregar las variables de
   entorno `DB_PASSWORD=tickets_pass` (la de `.env.example`; sin ella no arranca, a propósito) y
   `RABBITMQ_URL=amqp://urgentia:rabbit_pass@localhost:5672`.
4. Ejecutar `TicketServiceApplication`. Flyway crea las tablas `tickets`, `outbox` y
   `mensajes_procesados` la primera vez.

### Tests

```
cd ticket-service
mvn verify
```

Corre todos los tests y exige **80 % de cobertura de líneas en el paquete `domain`** con JaCoCo.
El reporte queda en `target/site/jacoco/index.html`. Casi todos usan H2 en memoria y un servidor
HTTP de mentira para la IA: los mensajes quedan en la tabla `outbox` y los tests los leen de ahí.
Los de RabbitMQ (`*RabbitMqTest`) levantan un RabbitMQ real con Testcontainers y cargan la
topología del contrato; si no hay Docker se saltean solos (en el CI siempre corren).

Desde Windows, sin instalar Java ni Maven (los tests con RabbitMQ se saltean):

```
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/app" -w /app/ticket-service maven:3.9-eclipse-temurin-21 mvn -B verify
```

| Qué se prueba | Dónde |
|---|---|
| Matriz de prioridad (las 9 combinaciones), SLA, escalamiento, confianza baja | `domain/...` |
| Tabla de transiciones completa (los 64 pares desde/hacia) y reglas de agente y motivo | `TicketTransicionesTest` |
| Que `domain` y `application` no importen Spring, JPA ni Jackson | `DominioSinFrameworksTest` |
| Casos de uso con dobles de los puertos (sin Spring) | `CasosDeUsoTest` |
| API completa: alta, demo P1, IA caída / lenta / respuesta inválida, reclasificación, estados, errores, listado, permisos del solicitante | `TicketApiTest` |
| Outbox: cada caso de uso deja sus eventos en orden, en la misma transacción, con el sobre de la sección 10.1 y validados contra `contracts/events/ticket-events.schema.json`; una operación rechazada no deja nada | `EventosDeTicketTest` |
| Publicador del outbox: publica en orden, marca lo confirmado, con el broker caído no pierde nada y reintenta | `PublicadorDelOutboxTest` |
| Propiedades AMQP de las reglas para quien publica | `EnviadorRabbitTest` |
| Clasificación por cola: el alta responde pendiente y pide la clasificación; aplica la respuesta, ignora repetidos y respuestas tardías, descarta lo que no cumple el contrato; error de la IA y reclasificación | `ClasificacionPorColaTest` |
| Con RabbitMQ real: los eventos llegan a cada cola según sus bindings; con el broker apagado el alta responde 201 y los eventos salen cuando vuelve | `PublicacionEnRabbitMqTest` |
| Con RabbitMQ real: crear → pedido a la IA → respuesta → P1 y escalado → aviso a la guardia; mensaje repetido sin efectos; mensaje inválido a la DLQ | `ClasificacionPorColaEnRabbitMqTest` |
| Lo que publica springdoc coincide con `contracts/ticket-service.yaml` | `ContratoOpenApiTest` |
| Persistencia y control de concurrencia optimista | `JpaTicketRepositoryAdapterTest` |

### Comprobar que anda

| URL | Qué muestra |
|---|---|
| `http://localhost:8080/swagger-ui.html` → `ticket-service` | Swagger, a través del gateway (con el botón Authorize) |
| `GET /health` | `{"status":"UP","service":"ticket-service","version":"0.1.0"}` |

Llamado directo al servicio (sin gateway, solo desarrollo): hay que mandar los headers que
normalmente pone el gateway.

```
curl -X POST http://localhost:8081/api/tickets \
  -H "Content-Type: application/json" \
  -H "X-User-Id: b1c2d3e4-0000-4000-8000-000000000004" \
  -H "X-User-Rol: SOLICITANTE" \
  -d '{"titulo":"No puede ingresar nadie","descripcion":"Producción caída, todos los usuarios bloqueados en el login desde las 9"}'
```

## Configuración

Todo por variables de entorno; no hay secretos en el código.

| Variable | Obligatoria | Valor por defecto | Uso |
|---|---|---|---|
| `DB_PASSWORD` | Sí | — | Contraseña de `tickets_user` |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/tickets_db` | Base de datos |
| `DB_USER` | No | `tickets_user` | Usuario de la base |
| `RABBITMQ_URL` | No | `amqp://guest:guest@localhost:5672` | Broker. En el Compose: `amqp://urgentia:rabbit_pass@rabbitmq:5672` |
| `CLASIFICACION_MODO` | No | `async` | `async`: el alta pide la clasificación por la cola. `sync` (plan B): la pide por REST dentro del POST |
| `CLASSIFICATION_URL` | No | `http://localhost:8082` | Dónde está classification-service (modo `sync` y reclasificación) |
| `CLASSIFICATION_TIMEOUT_MS` | No | `7000` | Timeout de la llamada REST a la IA |
| `LOG_LEVEL` | No | `INFO` | Nivel de log |

## API

Contrato completo: [`contracts/ticket-service.yaml`](../contracts/ticket-service.yaml).

| Método y ruta | Qué hace | Errores |
|---|---|---|
| `POST /api/tickets` | Crea el ticket. En `async` responde `PENDIENTE_CLASIFICACION` y pide la clasificación por la cola; en `sync` clasifica, calcula prioridad y SLA, y escala si corresponde | 400 |
| `GET /api/tickets?estado=&prioridad=&categoria=&page=&size=` | Lista (P1 primero; sin prioridad al final). Un SOLICITANTE ve solo los suyos | 400 |
| `GET /api/tickets/{id}` | Detalle. Un SOLICITANTE solo los suyos | 400, 404 |
| `PATCH /api/tickets/{id}/asignacion` | Asigna un agente | 400, 404, 409 |
| `PATCH /api/tickets/{id}/estado` | Cambia el estado (motivo obligatorio para ESCALADO) | 400, 404, 409 |
| `POST /api/tickets/{id}/reclasificacion` | Vuelve a pedir la clasificación, por REST | 404, 409, 503 |

Todos los errores salen con el formato común de la sección 5.3 (`codigo`, `mensaje`,
`detalles`, `timestamp`, `path`, `correlationId`).

## Mensajería con RabbitMQ (Entrega 2)

Decisiones en [ADR-11](../docs/adr/ADR-11-mensajeria-rabbitmq.md) (RabbitMQ) y
[ADR-12](../docs/adr/ADR-12-transactional-outbox.md) (Outbox y clasificación por cola). Contrato en
[`contracts/messaging/`](../contracts/messaging/). La topología la carga el Compose: ticket-service no
declara exchanges ni colas.

| Qué | Exchange / cola | Routing key | Clase |
|---|---|---|---|
| Publica los 6 eventos de dominio | `urgentia.eventos` (topic) | el `eventType` | `OutboxEventPublisherAdapter` + `PublicadorDelOutbox` |
| Publica el pedido de clasificación | `urgentia.clasificacion` (direct) | `ClasificacionSolicitada` | `OutboxSolicitudDeClasificacionAdapter` + `PublicadorDelOutbox` |
| Consume la respuesta de la IA | `tickets.clasificaciones` | `ClasificacionGenerada` | `ConsumidorDeClasificaciones` + `ManejadorDeClasificaciones` |

![Outbox](../docs/diagrams/secuencia-outbox.png)

**Transactional Outbox:** nada se publica directo desde el caso de uso. Los mensajes se guardan en la
tabla `outbox` en la misma transacción que el ticket, y `PublicadorDelOutbox` los lleva al broker cada
500 ms, en orden, con publisher confirms. Una fila queda publicada recién cuando RabbitMQ la confirma.
Con el broker caído, el alta responde 201 igual y los mensajes esperan en la tabla: se reintenta a 1, 2
y 4 s y después cada 8 s. Para verlo: `SELECT event_type, publicado_en, intentos, ultimo_error FROM outbox`.

**Consumidor de clasificaciones:** ack manual después de guardar. Es idempotente: los `eventId` ya
aplicados quedan en `mensajes_procesados`, en la misma transacción. Un mensaje que no cumple el contrato
va directo a `tickets.clasificaciones.dlq`; una falla transitoria se reintenta (hasta 5 veces, después
va a la DLQ). Solo aplica la respuesta si el ticket sigue pendiente: si un agente ya lo reclasificó por
REST, no lo pisa. Con `resultado: ERROR`, el ticket sigue `PENDIENTE_CLASIFICACION` y se puede reclasificar.

## Servicio SOAP (Entrega 2)

Consulta del estado de un ticket por SOAP, pensada para un sistema legado que no habla REST
(decisión en [ADR-10](../docs/adr/ADR-10-soap-consulta-de-estado.md)). Es de solo lectura.

Contrato: [`contracts/soap/tickets.wsdl`](../contracts/soap/tickets.wsdl) y
[`tickets-schema.xsd`](../contracts/soap/tickets-schema.xsd) (SOAP 1.1, document/literal).

| Qué | Dónde |
|---|---|
| Endpoint | `POST /ws/tickets` (operación `ConsultarEstadoTicket`) |
| WSDL | `GET /ws/tickets.wsdl` |
| XSD | `GET /ws/tickets-schema.xsd` |

**Pedido:** `ticketId` (UUID). **Respuesta:** `ticketId`, `estado`, `prioridad` y `fechaLimiteSla`. Los
dos últimos faltan si el ticket todavía no está clasificado.

```xml
<t:ConsultarEstadoTicketRequest xmlns:t="http://urgentia.local/ws/tickets">
  <t:ticketId>7c9e6679-7425-40de-944b-e07fc1f90ae7</t:ticketId>
</t:ConsultarEstadoTicketRequest>
```

```xml
<ns2:ConsultarEstadoTicketResponse xmlns:ns2="http://urgentia.local/ws/tickets">
  <ns2:ticketId>7c9e6679-7425-40de-944b-e07fc1f90ae7</ns2:ticketId>
  <ns2:estado>ESCALADO</ns2:estado>
  <ns2:prioridad>P1</ns2:prioridad>
  <ns2:fechaLimiteSla>2026-10-05T15:03:11Z</ns2:fechaLimiteSla>
</ns2:ConsultarEstadoTicketResponse>
```

**Errores (SOAP Fault, HTTP 500):** el detalle trae `codigo`, `mensaje` y el `correlationId` del pedido.

| `codigo` | `faultcode` | Cuándo |
|---|---|---|
| `NO_ENCONTRADO` | `soap:Client` | El ticket no existe, o un SOLICITANTE pide uno ajeno |
| `VALIDACION` | `soap:Client` | `ticketId` ausente o que no es un UUID; `X-User-Id` o `X-User-Rol` inválidos |
| `ERROR_INTERNO` | `soap:Server` | Cualquier otra falla (sin detalles internos) |

**Quién consulta:** igual que el REST, usa `X-User-Id` y `X-User-Rol` (los pone el gateway): un
SOLICITANTE solo ve sus tickets. Sin esos headers (llamada directa al 8081) no se restringe nada.

**Cómo probarlo:** [`docs/demo/soap-demo.http`](../docs/demo/soap-demo.http) (VS Code, extensión REST Client)
o la colección de Postman [`docs/demo/urgentia-soap.postman_collection.json`](../docs/demo/urgentia-soap.postman_collection.json).
También se puede importar `http://localhost:8081/ws/tickets.wsdl` en SoapUI.

**Dónde está el código:** `infrastructure/soap/` (`TicketsEndpoint`, `FaultExceptionResolver`,
`UsuarioDelPedidoSoap`, `SoapConfig`). Las clases del pedido y la respuesta se generan desde el XSD al
compilar (`target/generated-sources/xjc`). El servlet, la ruta `/ws` y la publicación del WSDL salen de
`spring.webservices` en `application.yml`.

**La copia del contrato:** la imagen Docker se arma solo con esta carpeta y no ve `contracts/`, por eso
hay una copia en `src/main/resources/soap/`. `ContratoSoapTest` falla si deja de ser igual a
`contracts/soap/`. Si cambia el contrato, se edita en `contracts/soap/` y se copia acá.

## Cómo está armado (arquitectura hexagonal)

![Capas](../docs/diagrams/capas-ticket-service.png)

```
com.urgentia.ticket
├── domain/            Java puro: reglas de negocio (sección 8)
│   ├── model/         Ticket, TicketId, Clasificacion, Sla y los enums
│   ├── event/         DomainEvent y los 6 eventos
│   ├── service/       PrioridadStrategy, MatrizItilStrategy
│   ├── factory/       TicketFactory
│   └── exception/     TransicionInvalidaException, ReglaDeNegocioException
├── application/       Casos de uso: orquestan, no deciden (tampoco usan Spring)
│   ├── port/in/       CrearTicketUseCase, AsignarTicketUseCase, CambiarEstadoUseCase,
│   │                  ReclasificarTicketUseCase, ConsultarTicketsUseCase, AplicarClasificacionUseCase
│   ├── port/out/      TicketRepository, ClasificadorPort, EventPublisherPort,
│   │                  SolicitudDeClasificacionPort, MensajesProcesadosPort
│   └── service/       Una implementación por caso de uso, y ModoClasificacion (sync o async)
└── infrastructure/    Adaptadores (Spring)
    ├── rest/          TicketController, DTOs, GlobalExceptionHandler, CorrelationIdFilter, HealthController
    ├── persistence/   TicketJpaEntity, JpaTicketRepositoryAdapter, JpaMensajesProcesadosAdapter (+ Flyway)
    ├── client/        ClassificationHttpClient + ClasificacionTraductor (ACL)
    ├── messaging/     OutboxEventPublisherAdapter, OutboxSolicitudDeClasificacionAdapter,
    │   │              ConsumidorDeClasificaciones, ManejadorDeClasificaciones (ACL del mensaje)
    │   └── outbox/    Outbox, PublicadorDelOutbox, EnviadorRabbit (publisher confirms)
    ├── soap/          TicketsEndpoint y su manejo de errores
    └── config/        TicketServiceConfig, CasosDeUsoTransaccionales, MensajeriaConfig, OpenApiConfig, logs JSON
```

Las dependencias apuntan siempre hacia adentro: `infrastructure` → `application` → `domain`.
`DominioSinFrameworksTest` hace fallar el build si alguien importa Spring, JPA o Jackson en
`domain` o en `application`.

Diagramas: [clases](../docs/diagrams/clases-ticket.png) ·
[estados](../docs/diagrams/estados-ticket.png) · [capas](../docs/diagrams/capas-ticket-service.png) ·
[Outbox y clasificación por cola](../docs/diagrams/secuencia-outbox.png).

### Qué pasa al crear un ticket (`CLASIFICACION_MODO=async`)

1. `TicketController` valida el body y toma el solicitante de `X-User-Id`.
2. `CasosDeUsoTransaccionales` abre una transacción y llama a `CrearTicketService`.
3. `TicketFactory` crea el `Ticket` en `NUEVO` (registra `TicketCreado`) y pasa a
   `PENDIENTE_CLASIFICACION`.
4. Se guarda el ticket y, **en la misma transacción**, quedan en el outbox `TicketCreado` y un
   `ClasificacionSolicitada` con el título y la descripción.
5. Se responde `201` con el ticket pendiente, sin esperar a la IA ni al broker.
6. `PublicadorDelOutbox` publica las dos filas. El worker de classification-service clasifica y
   contesta `ClasificacionGenerada`.
7. `ConsumidorDeClasificaciones` la recibe y `AplicarClasificacionService`, en otra transacción, llama a
   `Ticket.aplicarClasificacion()`: prioridad con `MatrizItilStrategy` (P1 si la IA lo marcó crítico),
   SLA y, si queda P1, escalamiento. `TicketClasificado` y `TicketEscalado` van al outbox y de ahí a
   notification-service y reporting-service.

Con `CLASIFICACION_MODO=sync` (plan B), los pasos 4 a 7 pasan dentro del POST, como en la Entrega 1:
`ClassificationHttpClient` llama a la IA (timeout 7 s) y, si falla, el ticket queda
`PENDIENTE_CLASIFICACION`. Los eventos salen igual por el outbox.

### Detalles de diseño que conviene saber

- **Una transacción por caso de uso, puesta por la infraestructura** (`CasosDeUsoTransaccionales`,
  patrón Decorator). Así `application` no depende de Spring.
- **No se ocupa una conexión mientras se espera a la IA:** Hikari con `auto-commit: false` y
  Hibernate con `provider_disables_autocommit`, así la conexión se toma recién al guardar.
- **Concurrencia optimista** (`@Version`): si dos agentes cambian el mismo ticket a la vez, el
  segundo recibe 409 `REGLA_DE_NEGOCIO` en lugar de pisar el cambio del primero.
- **Mensajes en orden y sin perderse:** el outbox se publica por orden de llegada y una fila que falla
  frena las siguientes hasta que el broker la confirma. La entrega es "al menos una vez": los
  consumidores son idempotentes por `eventId`.
- **Fechas en UTC y en segundos** (`2026-10-05T14:03:11Z`): el reloj del servicio avanza de a
  segundos.
- **Logs:** una línea JSON por evento con `timestamp`, `level`, `service`, `correlationId` y
  `message`. El `correlationId` se reenvía a la IA, viaja en los eventos y en las propiedades AMQP, y el
consumidor lo retoma del mensaje, así los eventos que produce una clasificación siguen la correlación
del pedido original.

## Patrones aplicados

| Patrón | Clase / archivo | Qué problema resuelve |
|---|---|---|
| **Aggregate** (DDD) | `domain/model/Ticket` | Un solo lugar donde viven las invariantes del ticket; sin setters, todo cambio pasa por un método de negocio que valida la transición |
| **Value Object** (DDD) | `TicketId`, `Clasificacion`, `Sla` | Conceptos inmutables que se validan al crearse (por ejemplo, confianza entre 0 y 1) |
| **Domain Event** (DDD) | `domain/event/*` | El agregado registra lo que pasó; otros servicios reaccionan sin que el dominio los conozca |
| **Factory** | `domain/factory/TicketFactory` | Crea el ticket válido con su id y su fecha (del reloj inyectado, testeable) |
| **Strategy** | `PrioridadStrategy` / `MatrizItilStrategy` | La regla de prioridad se puede cambiar sin tocar el agregado (los tests usan otra estrategia) |
| **Repository** | `application/port/out/TicketRepository` + `JpaTicketRepositoryAdapter` | La aplicación guarda y busca tickets sin saber que hay JPA y PostgreSQL |
| **Adapter / Anti-Corruption Layer** | `ClassificationHttpClient` + `ClasificacionTraductor` | Traduce el JSON de la IA al `Clasificacion` del dominio y rechaza lo que no cumple el contrato (enums inválidos, confianza fuera de rango) |
| **Transactional Outbox** | `Outbox`, `OutboxEventPublisherAdapter`, `PublicadorDelOutbox` | Guarda el ticket y sus mensajes en una sola transacción y los publica después con confirmación del broker: no salen eventos de algo que no pasó y no se pierden si el broker está caído |
| **Observer** (publicar / suscribir) | `EventPublisherPort` → exchange `urgentia.eventos` → colas de cada consumidor | ticket-service avisa "pasó algo" y no sabe quién lo escucha: cada consumidor elige qué eventos recibe con sus bindings |
| **Idempotent Consumer** | `ConsumidorDeClasificaciones` + `MensajesProcesadosPort` | Un mensaje repetido (la entrega es "al menos una vez") se confirma sin volver a aplicarse |
| **Adapter / ACL del mensaje** | `ManejadorDeClasificaciones` | Valida `ClasificacionGenerada` contra el contrato y la traduce al dominio con el mismo `ClasificacionTraductor` que la respuesta REST |
| **Decorator** | `CasosDeUsoTransaccionales` | Agrega la transacción alrededor de cada caso de uso sin meter Spring en la capa de aplicación |
| **Ports & Adapters** (hexagonal) | `application/port/in`, `application/port/out`, `infrastructure/*` | El núcleo no depende de la tecnología: pasar de HTTP a RabbitMQ en la Entrega 2 fue cambiar adaptadores, sin tocar el dominio |
| **Strategy** (modo de clasificación) | `ModoClasificacion` en `CrearTicketService` | Elegir entre la cola y REST con una variable de entorno (`CLASIFICACION_MODO`), sin tocar código |
| **Adapter de entrada** (SOAP) | `infrastructure/soap/TicketsEndpoint` | Un cliente SOAP consulta el estado reusando `ConsultarTicketsUseCase`: traduce el XML al caso de uso y el resultado a XML, sin reglas propias |
| **Builder** (tests) | `src/test/.../TicketBuilder`, `ClasificacionBuilder` | Armar tickets en cualquier estado para los tests sin constructores largos |

## Pendiente de acordar

- SOAP: la ruta `/ws/**` y su regla de roles en el api-gateway (hoy el servicio solo se alcanza directo en el
  puerto 8081), y la justificación del servicio, que es un sistema legado ficticio (ADR-10, propuesta).
- `motivo` del cambio de estado: se propuso un máximo de 500 caracteres (el contexto no fija uno).
- `contracts/events/ticket-events.schema.json`: lo usan notification-service y reporting-service tal
  cual; falta dejar escrita la aprobación de P5 (dueño del formato) y P6.
