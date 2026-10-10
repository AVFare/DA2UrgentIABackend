# user-service

**Identidad y acceso** de UrgentIA (subdominio de soporte, no Core: capas simples, sin DDD
táctico). Da de alta usuarios, los lista y emite el JWT que el resto de los servicios usa para
saber quién llama y con qué rol — el gateway es el único que lo valida.

- **Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, Maven.
- **Puerto:** 8083 (no se publica: se entra por el gateway en 8080).
- **Base de datos:** PostgreSQL `users_db` (usuario `users_user`).
- **Dueño:** P3 · revisa PRs: P2.

## Cómo correrlo

### Con Docker Compose (lo normal)

Desde la raíz del repositorio:

```
cp .env.example .env
docker compose up -d --build --wait
```

Levanta PostgreSQL, el gateway y user-service (entre otros). Flyway crea la tabla `usuarios`
y el servicio carga los 5 usuarios semilla la primera vez que arranca.

### Desde el IDE

1. Levantar solo la base: `docker compose up -d postgres`.
2. Abrir `user-service/pom.xml` como proyecto Maven, con JDK 21.
3. En la configuración de ejecución de `UserServiceApplication`, agregar las variables de
   entorno `DB_PASSWORD=users_pass` y `JWT_SECRET=cambiar-por-un-secreto-de-al-menos-32-caracteres`
   (las de `.env.example`). Sin ellas no arranca, a propósito: `DB_PASSWORD` no tiene valor por
   defecto y `JwtService` falla si el secreto tiene menos de 32 caracteres.
4. Ejecutar `UserServiceApplication`.

### Tests

```
cd user-service
mvn verify
```

No hace falta Docker: usa H2 en memoria. Corre con `@Transactional` (cada test hace rollback
al terminar) para no pisar los usuarios semilla.

| Qué se prueba | Dónde |
|---|---|
| Login correcto, password incorrecta, email inexistente (mismo 401) y el contenido del JWT | `AuthApiTest` |
| Alta (sin exponer la contraseña), email duplicado, validación, listado por rol, id inexistente | `UsuarioApiTest` |
| El seed es idempotente | `DatosSemillaTest` |
| El contexto levanta y `/health` responde | `HealthApiTest` |

### Comprobar que anda

| URL | Qué muestra |
|---|---|
| `http://localhost:8080/swagger-ui.html` → `user-service` | Swagger, a través del gateway |
| `GET /health` | `{"status":"UP","service":"user-service","version":"0.1.0"}` |

```
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"solicitante@urgentia.local","password":"Usuario123!"}'
```

## Configuración

Todo por variables de entorno; no hay secretos en el código.

| Variable | Obligatoria | Valor por defecto | Uso |
|---|---|---|---|
| `DB_PASSWORD` | Sí | — | Contraseña de `users_user` |
| `JWT_SECRET` | Sí | — | Firma el JWT (HS256). Falla al arrancar si tiene menos de 32 caracteres |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/users_db` | Base de datos |
| `DB_USER` | No | `users_user` | Usuario de la base |
| `JWT_EXPIRATION_MINUTES` | No | `60` | Duración del token |
| `LOG_LEVEL` | No | `INFO` | Nivel de log |

## Usuarios semilla

Se cargan al arrancar si no existen (de forma idempotente), con contraseña en BCrypt. Son
datos de desarrollo, documentados en el contexto del proyecto.

| Email | Contraseña | Rol |
|---|---|---|
| `admin@urgentia.local` | `Admin123!` | ADMIN |
| `guardia@urgentia.local` | `Agente123!` | AGENTE |
| `soporte@urgentia.local` | `Agente123!` | AGENTE |
| `solicitante@urgentia.local` | `Usuario123!` | SOLICITANTE |
| `solicitante2@urgentia.local` | `Usuario123!` | SOLICITANTE |

## API

Contrato completo: [`contracts/user-service.yaml`](../contracts/user-service.yaml).

| Método y ruta | Qué hace | Errores |
|---|---|---|
| `POST /api/auth/login` | Devuelve el JWT | 400, 401 |
| `POST /api/usuarios` | Crea un usuario (solo ADMIN, según el gateway) | 400, 409 |
| `GET /api/usuarios?rol=&page=&size=` | Lista paginado (`size` hasta 100) | 400 |
| `GET /api/usuarios/{id}` | Detalle | 404 |
| `GET /health` | Estado del servicio | — |

Todos los errores salen con el formato común (`codigo`, `mensaje`, `detalles`, `timestamp`,
`path`, `correlationId`). El rol de cada ruta lo controla el gateway; este servicio confía en
la red interna y no vuelve a chequearlo (`permitAll`).

## Cómo está armado

Capas simples (no hexagonal, a diferencia de `ticket-service`): es un subdominio de soporte,
no el Core del negocio.

```
com.urgentia.user
├── controller/   AuthController, UsuarioController, HealthController, GlobalExceptionHandler
├── service/      AuthService, UsuarioService (y las excepciones de negocio)
├── repository/   UsuarioRepository
├── model/        Usuario (entidad JPA), Rol
├── dto/          LoginRequest/Response, CrearUsuarioRequest, UsuarioResponse, PaginaResponse, ErrorResponse
├── security/     JwtService, SecurityConfig
└── config/       DatosSemilla, OpenApiConfig, LogJsonFormatter, CorrelationIdFilter
```

## Patrones aplicados

| Patrón | Clase / archivo | Qué problema resuelve |
|---|---|---|
| **Repository** | `UsuarioRepository` | El resto del código guarda y busca usuarios (por email, por rol) sin saber que hay JPA y PostgreSQL detrás; en los tests, la misma interfaz corre contra H2 |

## Pendiente de acordar

- `CrearUsuarioRequest`: longitud máxima de `nombre` (120) y `email` (254), y mínimo de
  `password` (8 caracteres) — propuestos por P3; el contexto no los fija.
