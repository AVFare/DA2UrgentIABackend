# Manual de despliegue

Cómo levantar UrgentIA desde cero con Docker Compose, en una máquina limpia con Windows o Linux, y
cómo comprobar que funciona. Las secciones 1 a 8 cubren el backend (este repo); la
[sección 9](#9-frontend) suma el frontend, que está en su propio repo y se levanta con su propio
Compose.

El Compose levanta nueve contenedores: los seis servicios, PostgreSQL, MongoDB y RabbitMQ. Además
corre un décimo de un solo uso, `rabbitmq-topologia`, que carga las colas en RabbitMQ y termina. Desde
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
| `LLM_PROVIDER` | `mock` (por defecto): clasifica por palabras clave, sin internet ni API key. Ver [sección 6](#6-ia-y-correo) |
| `CLASIFICACION_MODO` | `async` (por defecto; si la línea no está en el `.env`, el Compose usa `async` igual): el alta responde enseguida en `PENDIENTE_CLASIFICACION` y la clasificación llega por RabbitMQ. `sync` es el plan B: ticket-service llama a la IA por REST dentro del alta, como en la entrega 1. Si se cambia, recrear ticket-service con `docker compose up -d --wait ticket-service` |
| `EMAIL_PROVIDER`, `SMTP_USER`, `SMTP_PASSWORD`, `EMAIL_DESTINO_DEMO` | `simulado` (por defecto): el mail solo se escribe en el log y las otras tres quedan vacías. Con `real` sale por Gmail y las tres son obligatorias. Ver [Correo](#correo) |
| `CORS_ALLOWED_ORIGINS` | Orígenes del front que acepta el gateway. Por defecto `http://localhost:5173`, que es el del front tanto con Vite como con su contenedor. Ver [sección 9.2](#92-configurar-el-env) |

## 4. Levantar todo

```powershell
docker compose up -d --build --wait
```

- `--build` construye las imágenes de los seis servicios.
- `-d` deja los contenedores corriendo en segundo plano y devuelve la terminal.
- `--wait` espera a que cada contenedor esté sano antes de terminar.

La primera vez tarda varios minutos, porque descarga las imágenes base y las dependencias. Las
siguientes reusan lo descargado y tardan mucho menos.

**Salió bien si** el comando termina sin error y cada contenedor aparece como `Healthy`, salvo
`rabbitmq-topologia`, que no queda corriendo: carga la topología y termina. Si termina con
`dependency failed to start`, `is unhealthy` o `didn't complete successfully`, ir a
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

`rabbitmq-topologia` no aparece en esa lista porque ya terminó. Para verlo:

```powershell
docker compose ps -a rabbitmq-topologia
docker compose logs rabbitmq-topologia
```

**Salió bien si** el estado es `Exited (0)` y el log dice `Topologia cargada: HTTP 204`.

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

La topología del [contrato de mensajería](../contracts/messaging/) la carga `rabbitmq-topologia`
en cada `docker compose up`, así que aparece aunque ningún servicio esté conectado:

4. En la pestaña **Exchanges** están `urgentia.eventos` (topic), `urgentia.clasificacion` (direct) y
   `urgentia.dlx` (direct), además de los `amq.*` que trae RabbitMQ.
5. En la pestaña **Queues and Streams** están las ocho colas, todas de tipo `quorum`:
   `notificaciones.eventos`, `reportes.eventos`, `clasificacion.solicitudes`,
   `tickets.clasificaciones` y la `.dlq` de cada una.

Los cuatro servicios que usan el broker se conectan solos al arrancar:

6. En la pestaña **Connections** hay **al menos cuatro** conexiones, todas con el usuario de
   `RABBITMQ_USER` y estado `running`: las de ticket-service, classification-service,
   notification-service y reporting-service. Las conexiones se nombran por IP y puerto, no por
   servicio. user-service y el gateway nunca tienen conexión.
7. Para ver qué servicio consume qué, en **Queues and Streams** entrar a cada cola y mirar la sección
   **Consumers**: tienen un consumidor `notificaciones.eventos` (notification-service),
   `reportes.eventos` (reporting-service), `clasificacion.solicitudes` (classification-service) y
   `tickets.clasificaciones` (ticket-service). Las `.dlq` no tienen ninguno.

ticket-service publica en `urgentia.eventos` los eventos de dominio y en `urgentia.clasificacion` el
`ClasificacionSolicitada` de cada alta, desde su Outbox (ADR-12); ya no manda eventos por HTTP.

Si hay menos de cuatro conexiones, ver el log del servicio que falta: todos reintentan solos
mientras no pueden conectarse.

## 6. IA y correo

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

notification-service manda un mail cuando un ticket se escala a la guardia (`TicketEscalado`) y
cuando se resuelve (`TicketResuelto`). Cómo lo manda depende de `EMAIL_PROVIDER`:

| `EMAIL_PROVIDER` | Qué hace |
|---|---|
| `simulado` (por defecto) | No manda nada: escribe una línea `EMAIL_SIMULADO` en el log, con el destinatario, el asunto y el mensaje. No hace falta configurar nada. Es lo que usan el CI y los tests |
| `real` | Manda el mail por SMTP con STARTTLS (Gmail) y escribe una línea `EMAIL_ENVIADO` en el log |

#### Probar el modo simulado

Correr el flujo de la demo, que crea un ticket crítico y lo escala a la guardia:

```powershell
# Windows (PowerShell)
docker run --rm -v "${PWD}/docs/demo:/etc/newman" postman/newman run urgentia-demo.postman_collection.json --folder "1. Flujo principal" --folder "2. Controles del gateway" --env-var baseUrl=http://host.docker.internal:8080
docker compose logs notification-service | Select-String EMAIL_SIMULADO
```

```bash
# Linux
docker run --rm --add-host=host.docker.internal:host-gateway -v "$PWD/docs/demo:/etc/newman" postman/newman run urgentia-demo.postman_collection.json --folder "1. Flujo principal" --folder "2. Controles del gateway" --env-var baseUrl=http://host.docker.internal:8080
docker compose logs notification-service | grep EMAIL_SIMULADO
```

**Salió bien si** newman termina sin pruebas fallidas y aparece al menos una línea `EMAIL_SIMULADO`
con `GRUPO:GUARDIA` como destinatario.

#### Configurar el mail real

Variables del `.env` (las primeras cuatro están en `.env.example`; `SMTP_HOST` y `SMTP_PORT` no,
porque el Compose ya trae los valores de Gmail):

| Variable | Por defecto | Qué poner |
|---|---|---|
| `EMAIL_PROVIDER` | `simulado` | `real` |
| `SMTP_USER` | vacía | La cuenta de Gmail que manda los mails (por ejemplo `cuenta.demo@gmail.com`). También es el remitente |
| `SMTP_PASSWORD` | vacía | Una **contraseña de aplicación** de esa cuenta (ver abajo), no la contraseña normal |
| `EMAIL_DESTINO_DEMO` | vacía | La casilla a la que llegan **todos** los mails |
| `SMTP_HOST` | `smtp.gmail.com` | Cambiarla solo para usar otro servidor SMTP |
| `SMTP_PORT` | `587` | Ídem |

Por qué una sola casilla de destino: los usuarios semilla tienen direcciones `@urgentia.local`, que no
existen. Por eso todos los mails van a `EMAIL_DESTINO_DEMO`, y el destinatario original queda al
principio del asunto y al final del mensaje.

**La contraseña de aplicación.** Gmail no acepta la contraseña normal de la cuenta para mandar por
SMTP. Hace falta una contraseña de aplicación: un código de 16 caracteres que Google genera para un
solo uso y que se puede revocar sin tocar la contraseña de la cuenta. Para crearla:

1. En la cuenta de Google que va en `SMTP_USER`, activar la **Verificación en 2 pasos** (Cuenta de
   Google → **Seguridad**). Sin ella, Google no ofrece contraseñas de aplicación.
2. Abrir https://myaccount.google.com/apppasswords, escribir un nombre (por ejemplo `UrgentIA`) y
   apretar **Crear**.
3. Copiar el código que aparece: Google lo muestra una sola vez, en cuatro grupos de cuatro letras.
   En el `.env` conviene pegarlo sin espacios; si quedan, el servicio los saca antes de usarlo.

La contraseña de aplicación va **solo en el `.env`**, que no se sube al repositorio. No ponerla en
`.env.example`, en `docker-compose.yml`, en capturas ni en el video.

Ejemplo de `.env` (los valores son de ejemplo):

```dotenv
EMAIL_PROVIDER=real
SMTP_USER=cuenta.demo@gmail.com
SMTP_PASSWORD=abcdefghijklmnop
EMAIL_DESTINO_DEMO=casilla.de.prueba@gmail.com
```

Con `EMAIL_PROVIDER=real`, si `SMTP_USER`, `SMTP_PASSWORD` o `EMAIL_DESTINO_DEMO` está vacía,
**notification-service no arranca**: el contenedor termina y su log dice qué falta, por ejemplo
`EMAIL_PROVIDER=real necesita SMTP_PASSWORD, EMAIL_DESTINO_DEMO`.

Después de cambiar el `.env`, recrear el servicio para que lo tome (reiniciarlo no alcanza):

```powershell
docker compose up -d --wait notification-service
```

**Salió bien si** el comando termina sin error y `docker compose ps notification-service` lo muestra
`Up ... (healthy)`. Si termina con un error que dice que el contenedor salió (`exited`), ver
`docker compose logs notification-service`: casi siempre es una de las tres variables vacía.

Para volver al modo simulado, poner `EMAIL_PROVIDER=simulado` y recrear el servicio de la misma
forma.

#### Verificar que el mail sale de verdad

1. Correr el flujo de la demo con newman, igual que en el modo simulado.
2. Buscar la línea del envío en el log:

   ```powershell
   # Windows (PowerShell)
   docker compose logs notification-service | Select-String EMAIL_ENVIADO
   ```

   ```bash
   # Linux
   docker compose logs notification-service | grep EMAIL_ENVIADO
   ```

   **Salió bien si** aparece al menos una línea `EMAIL_ENVIADO` con `"destinatario": "GRUPO:GUARDIA"`,
   `"para"` igual a `EMAIL_DESTINO_DEMO` y `"servidor": "smtp.gmail.com:587"`.
3. Abrir la casilla de `EMAIL_DESTINO_DEMO`. Tiene que haber un mail de `SMTP_USER` con el asunto
   `[GRUPO:GUARDIA] [P1] Ticket escalado: No puede ingresar nadie` (el título del ticket crítico de la
   demo). El mensaje termina con `Destinatario original: GRUPO:GUARDIA` y `-- UrgentIA`. Si no está
   en la bandeja de entrada, buscar en **Spam**.

newman pasa aunque el mail no salga: su prueba de la notificación solo comprueba que quedó
registrada, no que se mandó. Lo que confirma el envío es la línea `EMAIL_ENVIADO` y el mail en la
casilla.

#### Problemas comunes del correo

Si el servidor SMTP rechaza el mail o no responde, el servicio **no se cae**: guarda la notificación
con estado `FALLIDA`, da el evento por procesado y escribe en el log una línea
`No se pudo enviar la notificacion` con la excepción en el campo `error`. Para verla:

```powershell
docker compose logs notification-service | Select-String "No se pudo enviar"
```

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `docker compose up` termina con `notification-service` salido, y su log dice `EMAIL_PROVIDER=real necesita ...` | Falta alguna de las variables que nombra el mensaje | Completarla en el `.env` y recrear el servicio |
| `No se pudo enviar la notificacion` con `SMTPAuthenticationError` y `Username and Password not accepted` en `error` | Gmail rechazó el usuario o la clave: se usó la contraseña normal en vez de una de aplicación, la de aplicación se copió mal o se revocó, o `SMTP_USER` no es la cuenta que la generó | Generar una contraseña de aplicación nueva en la cuenta de `SMTP_USER`, pegarla en `SMTP_PASSWORD` y recrear el servicio |
| No aparece la opción de contraseñas de aplicación en la cuenta de Google | La cuenta no tiene la Verificación en 2 pasos activada | Activarla y volver a https://myaccount.google.com/apppasswords |
| `No se pudo enviar la notificacion` con un error de conexión o de tiempo de espera | La red bloquea la salida al puerto 587 (pasa en algunas redes corporativas o de facultad), o `SMTP_HOST` / `SMTP_PORT` están mal | Probar desde otra red, o revisar esas dos variables |
| No hay `EMAIL_ENVIADO` ni `No se pudo enviar`, solo `EMAIL_SIMULADO` | El servicio sigue con `EMAIL_PROVIDER=simulado`: se editó el `.env` pero no se recreó | `docker compose up -d --wait notification-service` |
| Hay `EMAIL_ENVIADO` pero el mail no está en la casilla | Lo filtró el correo no deseado, o `EMAIL_DESTINO_DEMO` tiene un error de tipeo (el campo `para` del log muestra adónde se mandó) | Buscar en **Spam**; si `para` está mal, corregir la variable y recrear el servicio |

## 7. Apagar y resetear

| Para | Comando | Qué pasa con los datos |
|---|---|---|
| Frenar sin borrar nada | `docker compose stop` | Se conservan. Se vuelve a levantar con `docker compose up -d --wait` |
| Bajar los contenedores | `docker compose down` | Se conservan en los volúmenes (`pgdata`, `mongodata`, `rabbitdata`) |
| Empezar de cero | `docker compose down -v` | **Se borran** las bases, los tickets, las notificaciones y lo que haya en el broker. Al levantar de nuevo se recrean las bases, los usuarios semilla y las colas |
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
| PostgreSQL o MongoDB no crean las bases y el log dice `Permission denied` sobre `init.sql` o `init.js`, o `rabbitmq-topologia` sale con `exit 26` (Linux con SELinux, como Fedora) | SELinux no deja leer los archivos montados | Agregar `,z` al montaje en `docker-compose.yml` (`:ro,z`) solo en esa máquina, sin subirlo |
| `service "rabbitmq-topologia" didn't complete successfully: exit 22` y su log dice `returned error: 401` | La clave del `.env` no es la del volumen de RabbitMQ (se cambió después de crearlo) | `docker compose down -v` y levantar de nuevo (borra los datos) |
| `service "rabbitmq-topologia" didn't complete successfully: exit 22` y su log dice `returned error: 400` | Cambió en el contrato la configuración de una cola que ya existe, y RabbitMQ no deja redefinirla | Borrar esa cola en la consola (pestaña **Queues and Streams**, entrar a la cola, **Delete**) y levantar de nuevo, o `docker compose down -v` |
| `rabbitmq-topologia` sale con `exit 26` (Windows o Linux sin SELinux) | No encuentra `contracts/messaging/rabbitmq-definitions.json`: el Compose se corrió desde otra carpeta o falta el archivo | Correr los comandos desde la raíz del repo, en `dev/entrega-2` o posterior |
| Se llena el disco | Imágenes y caché de builds viejas | `docker system df` para ver el uso, `docker builder prune` y `docker image prune` para liberar |

## 9. Frontend

El frontend está en otro repo ([DA2UrgentIAFrontend](https://github.com/Napero/DA2UrgentIAFrontend))
y tiene su propio Compose, con un solo contenedor, `frontend`: un Nginx que sirve el build de React +
Vite + TypeScript. Nginx solo entrega el HTML, el JS y el CSS; **no hace de proxy hacia el
gateway**. Las llamadas a la API las hace el navegador directo al gateway, a la URL que quedó fijada
al compilar la imagen (`VITE_API_URL`). Por eso:

- El backend tiene que estar levantado y sano (secciones 3 a 5).
- El gateway tiene que aceptar el origen del front por CORS (`CORS_ALLOWED_ORIGINS`).
- Los dos Compose no comparten red y no hace falta: el contenedor del front nunca llama al gateway.

El contenedor publica su puerto solo en `127.0.0.1`, así que el navegador tiene que estar en la
misma máquina que Docker. Ver el [diagrama de despliegue](diagrams/despliegue.png).

### 9.1 Requisitos y código

Los mismos de la [sección 1](#1-requisitos): Docker y Git. No hace falta instalar Node, porque el
Dockerfile compila dentro de una imagen `node:22-alpine`. Node 22.20.0 solo hace falta para correr
el front en modo desarrollo con Vite (`npm run dev`), que está explicado en el README del front.

Clonarlo al lado de la carpeta del backend (no adentro):

```powershell
cd ..
git clone https://github.com/Napero/DA2UrgentIAFrontend.git
cd DA2UrgentIAFrontend
git checkout dev/entrega-2
```

En el repo del front, `main` todavía no tiene el código: hay que usar `dev/entrega-2`. Todos los
comandos de esta sección se corren desde la carpeta `DA2UrgentIAFrontend`, salvo los que dicen que
van en la del backend.

### 9.2 Configurar el `.env`

Con los valores por defecto el front y el backend ya se entienden y **no hace falta crear ni tocar
ningún `.env`**. Las variables que intervienen son tres:

| Dónde | Variable | Por defecto | Para qué |
|---|---|---|---|
| `.env` del front (opcional) | `VITE_API_URL` | `http://localhost:8080` | URL del gateway que usa el navegador. Se fija **al construir la imagen** |
| `.env` del front (opcional) | `FRONTEND_PORT` | `5173` | Puerto de la máquina (en `127.0.0.1`) donde queda el front |
| `.env` del backend | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes desde los que el gateway acepta llamadas del navegador |

Para cambiar algo del lado del front, crear su `.env` desde el ejemplo y editarlo:

```powershell
# Windows (PowerShell)
Copy-Item .env.example .env
```

```bash
# Linux
cp .env.example .env
```

Reglas:

- `VITE_API_URL` apunta al **gateway**, nunca a un microservicio. Tiene que ser una URL `http` o
  `https` sin usuario ni clave, sin query y sin `#`. Las variables `VITE_*` terminan dentro del JS
  que baja el navegador: no poner contraseñas, API keys ni el `JWT_SECRET`.
- El Compose del front lee `.env`. El `.env.local` del README del front es solo para `npm run dev`;
  el contenedor no lo usa.
- Si se cambia `VITE_API_URL`, hay que **reconstruir** la imagen (ver [9.3](#93-levantar-el-frontend)):
  reiniciar el contenedor no alcanza.
- Si se cambia `FRONTEND_PORT` (por ejemplo a `3000`), el origen del front pasa a ser
  `http://localhost:3000` y hay que sumarlo en el **`.env` del backend**, separado por coma y sin
  espacios:

  ```dotenv
  CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
  ```

  y recrear el gateway, **en la carpeta del backend**:

  ```powershell
  docker compose up -d --wait api-gateway
  ```

- `http://localhost:5173` y `http://127.0.0.1:5173` son orígenes distintos para el navegador. Si se
  abre el front con `127.0.0.1`, ese origen también tiene que estar en `CORS_ALLOWED_ORIGINS`.

### 9.3 Levantar el frontend

Con Docker Desktop abierto y el backend ya levantado:

```powershell
docker compose up -d --build --wait
```

Construye la imagen (instala dependencias, verifica tipos y compila con `npm run build`) y levanta el
contenedor. La primera vez tarda unos minutos. Este Compose es otro proyecto (toma el nombre de la
carpeta), así que no toca los contenedores del backend.

**Salió bien si** el comando termina sin error y `frontend` aparece como `Healthy`.

Alternativa sin Compose, solo con el Dockerfile (útil para probar otra URL del gateway sin crear el
`.env`):

```powershell
docker build --build-arg VITE_API_URL=http://localhost:8080 -t urgentia-frontend .
docker run -d --name urgentia-frontend -p 127.0.0.1:5173:80 urgentia-frontend
```

Para bajarlo: `docker compose down` (o `docker rm -f urgentia-frontend` si se usó `docker run`). El
front no tiene volúmenes, así que no hay datos que perder.

Después de cambiar `VITE_API_URL`, volver a correr `docker compose up -d --build --wait` (o repetir el
`docker build` con el nuevo `--build-arg` y recrear el contenedor).

### 9.4 Verificar que funciona y que habla con el gateway

1. **Contenedor sano.** `docker compose ps` muestra una línea, `frontend`, con `Up ... (healthy)`. El
   healthcheck de la imagen pide `/health` cada 10 segundos.
2. **Nginx sirve la app.** En el navegador:

   | URL | Qué se tiene que ver |
   |---|---|
   | http://localhost:5173/health | `ok` |
   | http://localhost:5173 | El inicio de UrgentIA |
   | http://localhost:5173/reportes (escrita directo en la barra) | La app, no un `404 Not Found` de Nginx: Nginx devuelve `index.html` para las rutas de la SPA |

3. **La imagen tiene la URL del gateway.**

   ```powershell
   docker compose exec frontend sh -c "grep -l 'http://localhost:8080' /usr/share/nginx/html/assets/*.js"
   ```

   **Salió bien si** imprime una ruta como `/usr/share/nginx/html/assets/index-XXXX.js`. Si no imprime
   nada, la imagen se construyó con otra `VITE_API_URL` (cambiar el texto del `grep` por la URL que se
   haya configurado).
4. **El gateway acepta el origen del front (preflight de CORS).**

   ```powershell
   # Windows (PowerShell)
   $h = @{ 'Origin' = 'http://localhost:5173'; 'Access-Control-Request-Method' = 'POST'; 'Access-Control-Request-Headers' = 'content-type' }
   $r = Invoke-WebRequest -UseBasicParsing -Method Options -Uri http://localhost:8080/api/auth/login -Headers $h
   $r.StatusCode
   $r.Headers['Access-Control-Allow-Origin']
   ```

   ```bash
   # Linux
   curl -si -X OPTIONS http://localhost:8080/api/auth/login \
     -H 'Origin: http://localhost:5173' \
     -H 'Access-Control-Request-Method: POST' \
     -H 'Access-Control-Request-Headers: content-type' | grep -iE '^HTTP|access-control-allow-origin'
   ```

   **Salió bien si** responde `200` y `Access-Control-Allow-Origin` es `http://localhost:5173`. Si el
   origen no está permitido, el gateway responde `403` (en PowerShell, `Invoke-WebRequest` lo muestra
   como error).
5. **Login desde el front.** Es la prueba que confirma todo junto, porque las llamadas salen del
   navegador con el origen del front.

   1. Abrir http://localhost:5173/login. Tiene que verse la pantalla **Iniciar sesión**, con los
      campos **Email** y **Contraseña** y el botón **Ingresar**. Si en cambio abre directo otra
      pantalla, ya había una sesión guardada en el navegador: apretar **Cerrar sesión** (arriba), que
      vuelve a `/login`.
   2. Apretar `F12`, ir a la pestaña **Network** y, en el cuadro de filtro, escribir `8080` para ver
      solo las llamadas al gateway.
   3. Ingresar con el usuario semilla `solicitante@urgentia.local` y la contraseña `Usuario123!`, y
      apretar **Ingresar**.

   **Salió bien si:**

   - El navegador pasa a http://localhost:5173/mis-tickets, con el título **Mis tickets**. Si el
     solicitante todavía no creó tickets, se ve el aviso **Todavía no creaste tickets**; si ya se
     corrió la demo, aparecen sus tickets. Arriba se ve el nombre **Sofía Solicitante** con el rol
     `SOLICITANTE` y el botón **Cerrar sesión**.
   - En **Network** aparecen un `OPTIONS` y un `POST` a `login`, los dos con estado 200, y después
     las llamadas a `tickets` (un `OPTIONS` y un `GET`), también con 200.

   Con un usuario de rol `AGENTE` o `ADMIN` (por ejemplo `guardia@urgentia.local` / `Agente123!`), el
   login lleva a http://localhost:5173/tickets, la bandeja, en lugar de a `mis-tickets`.

   Si el formulario muestra **El email o la contraseña no son correctos.**, el `POST` llegó al gateway
   y user-service respondió 401: revisar el usuario y la clave, o esperar unos segundos si el backend
   recién arrancó. Si muestra **No pudimos conectarnos. Revisá tu conexión e intentá nuevamente.**, la
   llamada no llegó: ver los problemas de CORS y de conexión en [9.5](#95-problemas-comunes-del-frontend).

### 9.5 Problemas comunes del frontend

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `port is already allocated` en el 5173 | Vite (`npm run dev`) u otro contenedor ya usa el puerto | Cortar Vite con `Ctrl+C`, o ver quién lo usa con `netstat -ano \| findstr :5173`. También se puede cambiar `FRONTEND_PORT` y sumar el origen nuevo a CORS ([9.2](#92-configurar-el-env)) |
| En la consola del navegador: `blocked by CORS policy` / `No 'Access-Control-Allow-Origin' header`, o el preflight da 403 | El origen desde el que se abrió el front no está en `CORS_ALLOWED_ORIGINS` (otro puerto, o `127.0.0.1` en vez de `localhost`) | Sumarlo al `.env` del backend y recrear el gateway con `docker compose up -d --wait api-gateway` en la carpeta del backend |
| `net::ERR_CONNECTION_REFUSED` hacia `localhost:8080` | El backend no está levantado, o el gateway no está sano | Levantar el backend ([sección 4](#4-levantar-todo)) y revisar `docker compose ps` en su carpeta |
| Se cambió `VITE_API_URL` y el front sigue llamando a la URL anterior | La URL se fija al compilar | `docker compose up -d --build --wait`. Si el navegador sigue con la versión vieja, recargar con `Ctrl+F5` |
| Se cambió `.env.local` y el contenedor no lo toma | El Compose del front lee `.env`, no `.env.local` | Poner la variable en `.env` y reconstruir |
| Error `VITE_API_URL debe ser una URL HTTP o HTTPS sin credenciales, query ni fragmento.` | El valor tiene usuario o clave, `?`, `#` o no empieza con `http` | Corregir `VITE_API_URL` y reconstruir |
| La build falla en `npm ci` | Sin internet, o un proxy la bloquea | Revisar la conexión y repetir |
| La build falla en `npm run build` con errores de TypeScript | La build verifica tipos: la rama tiene un error de compilación | Avisar al responsable del front; no se arregla desde el despliegue |
| `--wait` termina con `unhealthy` | Nginx no arrancó | `docker compose logs frontend` |
| En la prueba 9.4.5, el login muestra **El email o la contraseña no son correctos.** recién levantado el backend, con un usuario semilla bien escrito | user-service todavía está cargando los usuarios semilla | Esperar unos segundos y repetir, como en la [sección 5.3](#53-login) |
