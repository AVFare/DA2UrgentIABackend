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
| `LLM_PROVIDER` | `mock` (por defecto): clasifica por palabras clave, sin internet ni API key. Ver [sección 6](#6-ia-y-correo-en-modo-de-prueba) |
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

En la pestaña **Connections** aparece una conexión por cada servicio que ya tiene su código AMQP.
Mientras no lo tengan, está vacía, y eso es lo esperado.

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
5. **Llamada real desde el front.** Es la prueba que confirma todo junto, porque sale del navegador
   con el origen del front. Abrir http://localhost:5173, apretar `F12`, ir a la pestaña **Console**,
   pegar esto y apretar Enter:

   ```js
   await fetch('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'solicitante@urgentia.local', password: 'Usuario123!' }) }).then(r => r.status)
   ```

   Chrome y Edge pueden bloquear el pegado la primera vez: escribir `allow pasting`, Enter, y volver a
   pegar. **Salió bien si** devuelve `200`; en la pestaña **Network** aparecen un `OPTIONS` y un `POST`
   a `login`, los dos con estado 200. Cuando el login del front esté integrado, la prueba equivalente
   es entrar por http://localhost:5173/login con ese mismo usuario.

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
| La llamada de la prueba 9.4.5 da `401` recién levantado el backend | user-service todavía está cargando los usuarios semilla | Esperar unos segundos y repetir, como en la [sección 5.3](#53-login) |
