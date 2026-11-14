# Manual de despliegue (backend)

Cómo levantar el backend de UrgentIA desde cero con Docker Compose, en una máquina limpia con
Windows o Linux, y cómo comprobar que funciona. El frontend se suma a este manual cuando tenga
su Dockerfile.

El Compose levanta nueve contenedores: los seis servicios, PostgreSQL, MongoDB y RabbitMQ. Desde
afuera solo se usa el gateway (puerto 8080); PostgreSQL, MongoDB y RabbitMQ publican sus puertos
solo en `127.0.0.1`, para usarlos desde el IDE. El detalle de servicios y variables está en las
secciones [3](../CONTEXTO_PROYECTO.md#3-servicios) y
[14](../CONTEXTO_PROYECTO.md#14-configuración-y-ejecución) de `CONTEXTO_PROYECTO.md`.

## 1. Requisitos

No hace falta instalar Java, Python ni Node: todo se compila dentro de Docker.

| | Windows 10/11 | Linux |
|---|---|---|
| Docker | Docker Desktop, con el motor WSL 2 (la opción por defecto al instalarlo) | Docker Engine y el plugin `docker compose` |
| Git | Git for Windows | El paquete `git` de la distribución |
| Terminal | PowerShell | Bash |
| Internet | La primera vez descarga imágenes y dependencias (Maven, pip, npm) | Igual |

Comprobar que están, en una terminal nueva:

```powershell
docker --version
docker compose version
git --version
```

Los tres tienen que responder con un número de versión. `docker compose version` tiene que decir
`Docker Compose version v2...` o posterior. Si dice `docker: command not found` o
`unknown command: compose`, falta instalar Docker o el plugin.

- **Windows:** Docker Desktop tiene que estar abierto (ícono de la ballena en la barra de tareas,
  sin el cartel "Starting"). Si está cerrado, los comandos fallan con un error que menciona
  `dockerDesktopLinuxEngine` o `pipe`.
- **Linux:** para usar `docker` sin `sudo`, agregar el usuario al grupo `docker` y volver a iniciar
  sesión: `sudo usermod -aG docker $USER`.

## 2. Obtener el código

```powershell
git clone https://github.com/AVFare/DA2UrgentIABackend.git
cd DA2UrgentIABackend
```

`git clone` deja la rama `main`. Para levantar lo último de la entrega 2 antes de que pase a
`main`: `git checkout dev/entrega-2`.

Todos los comandos que siguen se corren desde la carpeta `DA2UrgentIABackend`.

## 3. Configurar el `.env`

El Compose lee las contraseñas y la configuración de un archivo `.env` en la raíz, que no se sube
al repositorio. Se crea copiando el ejemplo:

```powershell
# Windows (PowerShell)
Copy-Item .env.example .env
```

```bash
# Linux
cp .env.example .env
```

Los valores de `.env.example` sirven tal cual para desarrollo y para la demo. Qué conviene saber de
cada grupo (la lista completa de variables está en la
[sección 14.1](../CONTEXTO_PROYECTO.md#141-envexample) del contexto):

| Variables | Qué tener en cuenta |
|---|---|
| `JWT_SECRET` | Al menos 32 caracteres. El gateway y user-service tienen que usar el mismo, y el Compose se los pasa a los dos |
| `POSTGRES_PASSWORD`, `*_DB_PASSWORD`, `MONGO_ROOT_PASSWORD` | Se usan **solo la primera vez**, cuando se crean los volúmenes. Si se cambian después, ver [Problemas comunes](#8-problemas-comunes) |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Usuario y clave del broker y de su consola. **Sin caracteres especiales** (`@ : / # ?`): el Compose los arma en la URL `amqp://usuario:clave@rabbitmq:5672`. También se usan solo la primera vez |
| `LLM_PROVIDER` | `mock` (por defecto): clasifica por palabras clave, sin internet ni API key. Ver [sección 6](#6-ia-y-correo-en-modo-de-prueba) |
| `CORS_ALLOWED_ORIGINS` | Orígenes del front que acepta el gateway. Por defecto `http://localhost:5173` (Vite) |

## 4. Levantar todo

```powershell
docker compose up -d --build --wait
```

- `--build` construye las imágenes de los seis servicios.
- `-d` deja los contenedores corriendo en segundo plano y devuelve la terminal.
- `--wait` espera a que cada contenedor esté sano antes de terminar.

La primera vez tarda varios minutos, porque descarga las imágenes base y las dependencias. Las
siguientes reusan lo descargado y tardan mucho menos.

**Salió bien si** el comando termina sin error y cada contenedor aparece como `Healthy`. Si
termina con `dependency failed to start` o `is unhealthy`, ir a
[Problemas comunes](#8-problemas-comunes).

Para ver los logs mientras arranca, en otra terminal: `docker compose logs -f`. Se sale con `Ctrl+C`
(eso cierra los logs, no los contenedores).

## 5. Verificar que funciona

### 5.1 Estado de los contenedores

```powershell
docker compose ps
```

Tienen que aparecer nueve líneas (`postgres`, `mongo`, `rabbitmq`, `api-gateway`, `user-service`,
`ticket-service`, `classification-service`, `notification-service`, `reporting-service`), todas
con `Up ... (healthy)` en la columna `STATUS`. Cada servicio da `healthy` cuando su `GET /health`
responde 200; las bases y el broker, cuando aceptan conexiones.

Si uno dice `(unhealthy)`, `Restarting` o `Exited`, ver su log: `docker compose logs <servicio>`
(por ejemplo `docker compose logs ticket-service`).

### 5.2 Gateway, Swagger y SOAP

Abrir en el navegador:

| URL | Qué se tiene que ver |
|---|---|
| http://localhost:8080/health | `{"status":"UP","service":"api-gateway","version":"..."}` |
| http://localhost:8080/swagger-ui.html | El Swagger unificado, con un selector arriba a la derecha para elegir cada servicio |
| http://localhost:8080/ws/tickets.wsdl | El WSDL del servicio SOAP de consulta de estado (un XML) |

### 5.3 Login

Prueba que el gateway, user-service y PostgreSQL funcionan juntos. Usa un usuario semilla (la
lista completa está en la [sección 6.2](../CONTEXTO_PROYECTO.md#62-usuarios-semilla-user-service)
del contexto).

```powershell
# Windows (PowerShell)
$body = '{"email":"solicitante@urgentia.local","password":"Usuario123!"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' -Body $body
```

```bash
# Linux
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"solicitante@urgentia.local","password":"Usuario123!"}'
```

**Salió bien si** la respuesta trae `accessToken`, `tokenType` = `Bearer` y el `usuario` con rol
`SOLICITANTE`. Si da 401 justo después de levantar, esperar unos segundos y repetir: user-service
da `healthy` un momento antes de terminar de cargar los usuarios semilla.

### 5.4 Consola de RabbitMQ

1. Abrir http://localhost:15672.
2. Entrar con `RABBITMQ_USER` y `RABBITMQ_PASSWORD` del `.env` (con el ejemplo: `urgentia` /
   `rabbit_pass`). El usuario `guest` no existe.
3. En la pestaña **Overview**, la sección **Nodes** muestra un nodo `rabbit@rabbitmq` en verde.

En la pestaña **Connections** aparece una conexión por cada servicio que usa el broker, y en
**Queues and Streams** las colas del
[contrato de mensajería](../contracts/messaging/). Mientras los servicios no tengan su código
AMQP, las dos pestañas están vacías, y eso es lo esperado.

## 6. IA y correo en modo de prueba

### Clasificación (`LLM_PROVIDER`)

| Valor | Qué hace | Qué hace falta |
|---|---|---|
| `mock` (por defecto) | Clasifica con reglas por palabras clave. Siempre responde igual, sin internet | Nada. Es lo que usan el CI, los tests y el plan B de la demo |
| `groq` | Llama a la API de Groq | `LLM_API_KEY` con una key propia (free tier en console.groq.com) |
| `ollama` | Llama a un modelo local con Ollama instalado en la máquina | Ollama corriendo y `LLM_MODEL` con un modelo descargado |

Las reglas del mock y los modelos de cada proveedor están en la
[sección 12.1](../CONTEXTO_PROYECTO.md#121-proveedores) del contexto. Después de cambiar
`LLM_PROVIDER` o la key, hay que recrear el servicio para que tome el `.env` nuevo:

```powershell
docker compose up -d --wait classification-service
```

El log de arranque confirma qué proveedor quedó: `docker compose logs classification-service`
muestra `Listo: LLM_PROVIDER=...`.

### Correo

Hoy notification-service **simula** el envío: en lugar de mandar un mail escribe una línea
`EMAIL_SIMULADO` en su log, con el destinatario, el asunto y el mensaje. No hace falta configurar
nada. El envío real (variable `EMAIL_PROVIDER`) se suma con el trabajo de P5.

Para ver un correo simulado, correr el flujo de la demo, que crea un ticket crítico y lo escala a
la guardia:

```powershell
# Windows (PowerShell)
docker run --rm -v "${PWD}/docs/demo:/etc/newman" postman/newman run urgentia-demo.postman_collection.json --env-var baseUrl=http://host.docker.internal:8080
docker compose logs notification-service | Select-String EMAIL_SIMULADO
```

```bash
# Linux
docker run --rm --add-host=host.docker.internal:host-gateway -v "$PWD/docs/demo:/etc/newman" postman/newman run urgentia-demo.postman_collection.json --env-var baseUrl=http://host.docker.internal:8080
docker compose logs notification-service | grep EMAIL_SIMULADO
```

**Salió bien si** newman termina sin pruebas fallidas y aparece al menos una línea `EMAIL_SIMULADO`
con `GRUPO:GUARDIA` como destinatario.

## 7. Apagar y resetear

| Para | Comando | Qué pasa con los datos |
|---|---|---|
| Frenar sin borrar nada | `docker compose stop` | Se conservan. Se vuelve a levantar con `docker compose up -d --wait` |
| Bajar los contenedores | `docker compose down` | Se conservan en los volúmenes (`pgdata`, `mongodata`, `rabbitdata`) |
| Empezar de cero | `docker compose down -v` | **Se borran** las bases, los tickets, las notificaciones y lo que haya en el broker. Al levantar de nuevo se recrean las bases y los usuarios semilla |
| Reconstruir un servicio después de cambiar su código | `docker compose up -d --build --wait <servicio>` | Se conservan |
| Reconstruir todo sin caché | `docker compose build --no-cache` y después `docker compose up -d --wait` | Se conservan |

Para ver los volúmenes: `docker volume ls`. Llevan delante el nombre de la carpeta del proyecto en
minúsculas (por ejemplo `da2urgentiabackend_pgdata`).

## 8. Problemas comunes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `error during connect` o un error que menciona `dockerDesktopLinuxEngine` (Windows) | Docker Desktop está cerrado o todavía arrancando | Abrirlo y esperar a que diga "Engine running" |
| `permission denied ... docker.sock` (Linux) | El usuario no está en el grupo `docker` | `sudo usermod -aG docker $USER` y volver a iniciar sesión |
| `unknown flag: --wait` | Docker Compose viejo | Actualizar Docker Desktop, o el plugin `docker-compose-plugin` en Linux |
| `port is already allocated` o `address already in use` (8080, 5432, 27017, 5672 o 15672) | Otro programa usa el puerto: un PostgreSQL o MongoDB instalado, otro Compose, un servicio corriendo en el IDE | Ver quién lo usa: `netstat -ano \| findstr :5432` (Windows) o `sudo ss -ltnp \| grep 5432` (Linux). Frenarlo, o bajar el otro Compose con `docker compose down` en su carpeta |
| La primera vez `--wait` corta por tiempo | La build y las descargas tardan | Repetir el comando: lo ya descargado no se vuelve a bajar. También se puede dar más tiempo con `--wait-timeout 600` |
| `dependency failed to start: container ... is unhealthy` | Un servicio no arrancó, o la base o el broker del que depende | `docker compose ps -a` para ver cuál, y `docker compose logs <servicio>` para ver por qué |
| Un contenedor se reinicia solo, o sale con código `137` | Le falta memoria | En Windows, darle más memoria a WSL: en `%UserProfile%\.wslconfig` poner `[wsl2]` y debajo `memory=6GB` (o lo que permita la máquina), correr `wsl --shutdown` y abrir Docker Desktop de nuevo |
| `password authentication failed` (PostgreSQL), `Authentication failed` (MongoDB) o `ACCESS_REFUSED` (RabbitMQ) después de cambiar el `.env` | Las bases y el broker crean usuarios y claves solo cuando el volumen está vacío; los volúmenes viejos tienen las claves anteriores | `docker compose down -v` y levantar de nuevo (borra los datos) |
| La consola de RabbitMQ no acepta el usuario | Se usó `guest`, o la clave cambió después de crear el volumen | Usar `RABBITMQ_USER` / `RABBITMQ_PASSWORD` del `.env`; si se cambiaron, `docker compose down -v` |
| Los servicios no se conectan a RabbitMQ después de cambiar la clave | La clave tiene caracteres especiales y rompe `RABBITMQ_URL` | Usar solo letras, números, `-` y `_` |
| El login da 401 recién levantado | user-service todavía está cargando los usuarios semilla | Esperar unos segundos y repetir |
| La build falla al descargar dependencias (Maven, pip o npm) | Sin internet, o un proxy o firewall la bloquea | Revisar la conexión y repetir `docker compose up -d --build --wait` |
| newman en Linux: `getaddrinfo ENOTFOUND host.docker.internal` | En Linux ese nombre no existe por defecto | Agregar `--add-host=host.docker.internal:host-gateway` al `docker run`, como en la [sección 6](#correo) |
| PostgreSQL o MongoDB no crean las bases y el log dice `Permission denied` sobre `init.sql` o `init.js` (Linux con SELinux, como Fedora) | SELinux no deja leer los archivos montados | Agregar `,z` al montaje en `docker-compose.yml` (`:ro,z`) solo en esa máquina, sin subirlo |
| Se llena el disco | Imágenes y caché de builds viejas | `docker system df` para ver el uso, `docker builder prune` y `docker image prune` para liberar |
