# api-gateway

Puerta de entrada única de UrgentIA. Todo pedido de afuera entra por acá: el gateway
identifica al usuario, controla que su rol alcance y recién entonces reenvía el pedido
al servicio que corresponde. Los servicios internos no publican ningún puerto.

- **Stack:** Java 21, Spring Boot 3.5, Spring Cloud Gateway (WebFlux), Maven.
- **Puerto:** 8080.
- **Base de datos:** no tiene.

## Cómo correrlo

### Desde el IDE

1. Abrir `api-gateway/pom.xml` como proyecto Maven, con JDK 21.
2. En la configuración de ejecución de `ApiGatewayApplication`, agregar la variable de
   entorno `JWT_SECRET` (mínimo 32 caracteres). Sin ella el gateway no arranca, a propósito.
3. Ejecutar `ApiGatewayApplication`.

### Con Docker

Desde la raíz del repositorio:

```
docker build -t urgentia-api-gateway ./api-gateway
docker run --rm -p 8080:8080 -e JWT_SECRET=<secreto de 32 caracteres o más> urgentia-api-gateway
```

### Comprobar que anda

| URL | Qué muestra |
|---|---|
| `http://localhost:8080/health` | `{"status":"UP","service":"api-gateway","version":"0.1.0"}` |
| `http://localhost:8080/swagger-ui.html` | Swagger unificado, con un selector por servicio |

## Configuración

Todo se configura por variables de entorno; no hay secretos en el código.

| Variable | Obligatoria | Valor por defecto | Uso |
|---|---|---|---|
| `JWT_SECRET` | Sí | — | Clave para validar la firma de los tokens. La misma que usa user-service para firmarlos |
| `USER_SERVICE_URL` | No | `http://localhost:8083` | Dónde está user-service |
| `TICKET_SERVICE_URL` | No | `http://localhost:8081` | Dónde está ticket-service |
| `CLASSIFICATION_SERVICE_URL` | No | `http://localhost:8082` | Dónde está classification-service |
| `NOTIFICATION_SERVICE_URL` | No | `http://localhost:8084` | Dónde está notification-service |
| `REPORTING_SERVICE_URL` | No | `http://localhost:8085` | Dónde está reporting-service |

Los valores por defecto sirven para correr todo desde el IDE. Dentro de Docker Compose hay
que definir las cinco URLs con el nombre de cada servicio (por ejemplo
`http://ticket-service:8081`).

## Qué pasa con cada pedido

Cada pedido atraviesa tres filtros, siempre en este orden, antes de llegar a un servicio:

| Orden | Filtro | Qué hace | Si no pasa |
|---|---|---|---|
| 1 | `CorrelationIdFilter` | Asegura que el pedido tenga `X-Correlation-Id` (lo genera si no viene), lo reenvía al servicio y lo devuelve en la respuesta | — |
| 2 | `JwtAuthFilter` | Exige un JWT válido en todo lo que no sea público. Le pasa al servicio `X-User-Id` y `X-User-Rol`, y borra esos headers si los manda el cliente | 401 `NO_AUTENTICADO` |
| 3 | `RoleAuthorizationFilter` | Controla que el rol alcance para la operación. Lo que no figura en la tabla de roles se rechaza | 403 `SIN_PERMISO` |

### Rutas públicas (no piden token)

- `POST /api/auth/login`
- `GET /health`
- `GET /swagger-ui.html`, `/swagger-ui/**`, `/webjars/**`, `/v3/api-docs/**`, `/docs/**`

### Tabla de roles

| Operación | Roles permitidos |
|---|---|
| `POST /api/usuarios` | ADMIN |
| `GET /api/usuarios/**` | AGENTE, ADMIN |
| `GET` y `POST /api/tickets`, `GET /api/tickets/{id}` | SOLICITANTE, AGENTE, ADMIN |
| `PATCH /api/tickets/{id}/asignacion`, `PATCH /api/tickets/{id}/estado`, `POST /api/tickets/{id}/reclasificacion` | AGENTE, ADMIN |
| `GET` y `POST /api/clasificaciones/**` | ADMIN |
| `GET /api/notificaciones/**`, `GET /api/reportes/**` | AGENTE, ADMIN |

Que un SOLICITANTE vea solo sus propios tickets lo resuelve ticket-service con `X-User-Id`.

### A qué servicio va cada ruta

| Ruta | Servicio |
|---|---|
| `/api/auth/**`, `/api/usuarios/**` | user-service |
| `/api/tickets/**` | ticket-service |
| `/api/clasificaciones/**` | classification-service |
| `/api/notificaciones/**` | notification-service |
| `/api/reportes/**` | reporting-service |
| `/v3/api-docs/<servicio>` | Documentación OpenAPI de ese servicio |

`/api/eventos` no tiene ruta: los eventos viajan solo por la red interna.

### Formato de error

Los errores que corta el gateway usan el formato común del proyecto:

```json
{
  "codigo": "NO_AUTENTICADO",
  "mensaje": "Falta el token de acceso",
  "timestamp": "2026-10-05T14:03:11Z",
  "path": "/api/tickets",
  "correlationId": "a8e1b2c3-..."
}
```

## Tests

```
mvn test
```

Son 49 tests de integración. Levantan el gateway completo y, en lugar de los servicios
reales, un servicio de mentira (`ServicioFalso`) que registra qué le llegó. Cubren las
rutas, los tres filtros, los errores 401 y 403, `/health` y el Swagger unificado.

## Patrones aplicados

### Chain of Responsibility

- **Dónde:** `filter/CorrelationIdFilter`, `filter/JwtAuthFilter` y
  `filter/RoleAuthorizationFilter`. Cada uno implementa `WebFilter` y el orden lo fija `@Order`.
- **Problema que resuelve:** antes de reenviar un pedido hay que hacer varios controles
  independientes (trazabilidad, autenticación, autorización). Con un único bloque de código
  esos controles quedarían mezclados y sería difícil sumar o quitar uno.
- **Cómo se ve en el código:** cada filtro recibe el pedido y decide una de dos cosas: lo
  corta y responde él mismo (por ejemplo, un 401) o se lo pasa al siguiente eslabón con
  `chain.filter(exchange)`. Ningún filtro conoce a los demás.

### Proxy

- **Dónde:** el gateway en su conjunto; las rutas están en `config/RutasConfig`.
- **Problema que resuelve:** los clientes no deben conocer ni alcanzar a los cinco servicios
  internos. El gateway se pone en el lugar de ellos: expone las mismas rutas, controla el
  acceso y recién entonces delega en el servicio real.
- **Cómo se ve en el código:** `RutasConfig` asocia cada grupo de rutas con la URL del
  servicio real. El cliente siempre habla con `localhost:8080` y no sabe qué servicio
  respondió.
