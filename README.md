# DA2UrgentIABackend

Backend de **UrgentIA**, una mesa de ayuda que prioriza sola. El solicitante carga un ticket
en texto libre; un modelo de lenguaje estima categoría, urgencia, impacto y módulo afectado;
el dominio calcula la prioridad (P1 a P4) con una matriz fija y, si el caso es crítico,
escala el ticket y avisa a la guardia.

La IA sugiere y el dominio decide: la IA nunca devuelve la prioridad.

Trabajo práctico de Desarrollo de Aplicaciones II (UADE).

## Arquitectura

Seis servicios, cada uno con su propia base de datos, detrás de un gateway que es la única
entrada desde afuera.

| Servicio | Puerto | Tecnología | Base de datos | Qué hace |
|---|---|---|---|---|
| `api-gateway` | 8080 | Java 21, Spring Cloud Gateway | — | Entrada única: valida el token, controla el rol y rutea |
| `ticket-service` | 8081 | Java 21, Spring Boot | PostgreSQL | Tickets, prioridad, SLA, estados y escalamiento |
| `classification-service` | 8082 | Python 3.12, FastAPI | MongoDB | Clasifica el texto del ticket con un LLM |
| `user-service` | 8083 | Java 21, Spring Boot | PostgreSQL | Usuarios, roles y login |
| `notification-service` | 8084 | Python 3.12, FastAPI | MongoDB | Notifica a partir de los eventos del ticket |
| `reporting-service` | 8085 | Node 20, NestJS | MongoDB | Reportes y cumplimiento de SLA |

![Diagrama de despliegue](docs/diagrams/despliegue.png)

El detalle completo (contratos, enums, reglas de negocio, eventos) está en
[`CONTEXTO_PROYECTO.md`](CONTEXTO_PROYECTO.md), que es la fuente de verdad técnica.

### Arquitectura en capas

![Arquitectura en capas](docs/diagrams/arquitectura-capas.png)

### Diagramas

Todos están en [`docs/diagrams/`](docs/diagrams), como fuente PlantUML (`.puml`) y como imagen (`.png`).

| Diagrama | Archivo |
|---|---|
| Arquitectura en capas de la solución | [`arquitectura-capas`](docs/diagrams/arquitectura-capas.png) |
| Componentes | [`componentes-reporting`](docs/diagrams/componentes-reporting.png) |
| Despliegue (Docker Compose) | [`despliegue`](docs/diagrams/despliegue.png) |
| Mapa de contextos (DDD estratégico) | [`context-map`](docs/diagrams/context-map.png) |
| Proceso de atención de un ticket (BPMN simplificado) | [`bpmn-atencion-ticket`](docs/diagrams/bpmn-atencion-ticket.png) |
| Secuencia: crear un ticket crítico | [`secuencia-crear-ticket-critico`](docs/diagrams/secuencia-crear-ticket-critico.png) |
| Secuencia: IA caída y reclasificación | [`secuencia-ia-caida`](docs/diagrams/secuencia-ia-caida.png) |
| Clases del dominio de tickets | [`clases-ticket`](docs/diagrams/clases-ticket.png) |
| Estados del ticket | [`estados-ticket`](docs/diagrams/estados-ticket.png) |
| Capas de ticket-service (hexagonal) | [`capas-ticket-service`](docs/diagrams/capas-ticket-service.png) |

### Patrones aplicados

Cada servicio documenta en su README, en la sección "Patrones aplicados", la clase o el archivo donde aplica cada patrón y qué problema resuelve.

| Patrón | Dónde | Detalle |
|---|---|---|
| Factory / Factory Method | `TicketFactory` (ticket), `PlantillaFactory` (notification), `LlmProviderFactory` (classification) | [ticket](ticket-service/README.md#patrones-aplicados), [notification](notification-service/README.md#patrones-aplicados), [classification](classification-service/README.md#patrones-aplicados) |
| Repository | Los cinco servicios con base de datos | Sección "Patrones aplicados" de cada README |
| Strategy | `PrioridadStrategy` / `MatrizItilStrategy` (ticket), `LlmProvider` (classification), canales (notification), proyectores (reporting) | [ticket](ticket-service/README.md#patrones-aplicados), [classification](classification-service/README.md#patrones-aplicados), [notification](notification-service/README.md#patrones-aplicados), [reporting](reporting-service/README.md#patrones-aplicados) |
| Observer | Eventos de dominio: `ticket-service` los publica; `notification-service` y `reporting-service` los consumen | [ticket](ticket-service/README.md#patrones-aplicados), [notification](notification-service/README.md#patrones-aplicados) |
| Facade | `ClasificacionFacade` (classification) | [classification](classification-service/README.md#patrones-aplicados) |
| Adapter / Anti-Corruption Layer | `ClassificationHttpClient` + `ClasificacionTraductor` (ticket), proveedores LLM y `response_parser` (classification) | [ticket](ticket-service/README.md#patrones-aplicados), [classification](classification-service/README.md#patrones-aplicados) |
| Chain of Responsibility, Proxy | Filtros y ruteo del gateway | [api-gateway](api-gateway/README.md#patrones-aplicados) |
| CQRS (lado de lectura) | `reporting-service` | [reporting](reporting-service/README.md#patrones-aplicados) |
| Aggregate, Value Object, Domain Event, Decorator, Ports & Adapters | `ticket-service` | [ticket](ticket-service/README.md#patrones-aplicados) |

### Decisiones de arquitectura

Las decisiones están registradas como ADR en [`docs/adr/`](docs/adr):

1. [Microservicios desde la primera entrega](docs/adr/ADR-01-microservicios.md)
2. [Base de datos propia por servicio](docs/adr/ADR-02-base-de-datos-por-servicio.md)
3. [Stack políglota](docs/adr/ADR-03-stack-poliglota.md)
4. [PostgreSQL y MongoDB](docs/adr/ADR-04-postgresql-y-mongodb.md)
5. [API Gateway como única entrada](docs/adr/ADR-05-api-gateway.md)
6. [Clasificación por REST con timeout y fallback](docs/adr/ADR-06-clasificacion-rest-con-fallback.md)
7. [Eventos de dominio por HTTP con sobre estándar](docs/adr/ADR-07-eventos-por-http.md)
8. [Hexagonal y DDD táctico solo en el Core](docs/adr/ADR-08-hexagonal-solo-en-el-core.md)
9. [Contract-first](docs/adr/ADR-09-contract-first.md)

## Cómo ejecutar

Hace falta Docker Desktop y Git. No hace falta instalar Java, Python ni Node.

```bash
git clone https://github.com/AVFare/DA2UrgentIABackend.git
cd DA2UrgentIABackend
cp .env.example .env
docker compose up -d --build --wait
```

El último comando construye las imágenes, levanta todo y espera a que cada contenedor esté
sano. La primera vez tarda varios minutos.

El Compose incluye los seis servicios y las dos bases de datos. Las consultas de reportes
se acceden por el gateway con un JWT de AGENTE o ADMIN; los eventos de P6 quedan en la red interna.

| Para | Abrir o correr |
|---|---|
| Usar la API desde el navegador | http://localhost:8080/swagger-ui.html |
| Ver que el gateway responde | http://localhost:8080/health |
| Ver el estado de los contenedores | `docker compose ps` |
| Ver los logs de un servicio | `docker compose logs api-gateway` |
| Frenar todo | `docker compose down` |
| Frenar y borrar los datos | `docker compose down -v` |

Los valores de `.env.example` sirven para desarrollo. El archivo `.env` no se sube al
repositorio.

## Demo

Con todo levantado, el flujo de la demo (login, ticket crítico, notificación a la guardia,
reportes y controles del gateway) se puede correr de cuatro formas:

| Con | Cómo |
   |---|---|
| Docker (sin instalar nada) | `docker run --rm -v "${PWD}/docs/demo:/etc/newman" postman/newman run urgentia-demo.postman_collection.json --env-var baseUrl=http://host.docker.internal:8080` |
| Postman | Importar [`docs/demo/urgentia-demo.postman_collection.json`](docs/demo/urgentia-demo.postman_collection.json) y correr las carpetas 1 y 2 |
| IntelliJ IDEA Ultimate | Abrir [`demo.http`](demo.http) y ejecutar los pedidos en orden |
| Swagger | Seguir [`docs/demo/demo-swagger.md`](docs/demo/demo-swagger.md) |

## Estructura del repositorio

```
├── api-gateway/              Java (Maven)
├── ticket-service/           Java (Maven)
├── user-service/             Java (Maven)
├── classification-service/   Python (pip)
├── notification-service/     Python (pip)
├── reporting-service/        Node (npm)
├── contracts/                OpenAPI de cada servicio y esquema de los eventos
├── docs/                     Diagramas y decisiones de arquitectura
├── infra/                    Scripts de inicio de PostgreSQL y MongoDB
├── demo.http                 Flujo de la demo para IntelliJ
├── docker-compose.yml
├── .env.example
└── CONTEXTO_PROYECTO.md
```

Cada servicio tiene su propio README con cómo correrlo, sus tests y los patrones de diseño
que aplica.

## 🌿 Convención de ramas

Para mantener organizado el repositorio, las ramas deberán nombrarse utilizando la siguiente convención:

```text
tipo/descripcion-corta
```

Se utilizarán los siguientes prefijos:

* `feat/` → Desarrollo de una nueva funcionalidad.
* `fix/` → Corrección de errores.
* `refactor/` → Modificación o reorganización de código sin agregar una nueva funcionalidad.
* `chore/` → Configuraciones, mantenimiento o cambios que no afectan la lógica de la aplicación.
* `docs/` → Cambios relacionados con documentación.

### Ejemplos

```text
feat/ticket-matriz-prioridad
feat/gateway-rutas
feat/user-login
feat/notification-canales
fix/gateway-jwt
refactor/ticket-casos-de-uso
docs/update-readme
chore/project-setup
```

La rama `main` contendrá la versión estable del proyecto. El desarrollo de nuevas funcionalidades se realizará en ramas independientes y, una vez finalizadas, se integrarán a `dev/entrega-1` mediante Pull Request. Al cierre de la entrega, `dev/entrega-1` pasa a `main`.

## Integración continua

Cada Pull Request y cada push a `main` o a ramas `dev/**` corre el pipeline de
`.github/workflows/ci.yml`: los tests de cada servicio, la construcción de su imagen Docker
y el arranque completo con Docker Compose. Un servicio que todavía no tiene proyecto se
saltea.