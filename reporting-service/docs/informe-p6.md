# UrgentIA · informe de P6

Borrador para integrar al informe del equipo · revisión del 10 de octubre de 2026.
Las numeraciones siguen CONTEXTO_PROYECTO.md; el índice académico final no estaba
disponible y debe confirmarse antes de integrar estas secciones.

## Integrantes

- Francisco Nappa (P6; autor de commits: Francisco Eduardo Nappa).
- Ana Fare.
- Florencia Quiñonez.
- Juan Lopez Korach.
- Santiago Gil Ishikawa.
- Gustavo Forcherio.

Las asignaciones P1–P5 deben ser confirmadas por el equipo.

## 1. Resumen del proyecto

UrgentIA es una mesa de ayuda donde el solicitante describe un problema en
texto libre. Un modelo de lenguaje sugiere categoría, urgencia, impacto y módulo.
El dominio de tickets decide prioridad y SLA con reglas deterministas. Los casos
críticos se escalan y generan eventos para notificaciones y reportes.

P6 implementa reporting-service: recibe esos eventos, mantiene una vista de
lectura y responde consultas de resumen, tickets y cumplimiento de SLA. Permite
observar el resultado operativo sin consultar ni modificar el agregado de tickets.
La demo local de P6 emite eventos de ejemplo; no certifica el flujo de creación,
clasificación ni envío de notificaciones de los demás servicios.

## 2. Arquitectura y decisiones

La arquitectura acordada utiliza seis microservicios y bases independientes.
El gateway es la entrada pública y valida JWT. Tickets y usuarios utilizan
Java/PostgreSQL, clasificación y notificaciones Python 3.12/FastAPI/MongoDB,
y reportes Node 20/NestJS/MongoDB, según las implementaciones integradas.

Para la primera defensa, los eventos viajan por HTTP. reporting-service escucha
en el puerto 8085 y almacena exclusivamente en reporting_db. POST /api/eventos
es interno; GET /api/reportes/* se expone mediante el gateway.

En P6 el controlador valida el sobre. EventosService registra un inbox persistente,
selecciona una Strategy y aplica un upsert atómico. TicketViewRepository actualiza
solo snapshots con fecha igual o posterior a la vista. ReportesRepository usa
agregaciones MongoDB para distribuir estados, prioridades y categorías, paginar
consultas y agrupar el cumplimiento del SLA.

El índice único por eventId evita duplicados. Guardar PENDIENTE antes de proyectar
y confirmar después permite reintentar ante fallos sin perder el evento por una
confirmación prematura. Los índices se inicializan antes de atender solicitudes.
La implementación funciona con MongoDB standalone, como la infraestructura actual.

El sobre no contiene una secuencia ni fechaResolucion en el snapshot. Los empates
de occurredAt dependen del orden de llegada. Si el cierre llega primero y la
resolución después como evento viejo, su fecha permanece desconocida. El dato se
excluye del porcentaje; se propone coordinar una revisión del contrato con P2/P5.

## 6. Seguridad y trazabilidad

El gateway autentica y autoriza consultas de reportes para AGENTE/ADMIN. El servicio
interno confía en la red privada y no duplica la validación JWT. No debe publicarse
POST /api/eventos por el gateway. El Compose propio publica localhost únicamente
para desarrollo y demostración.

MONGO_URI se configura por entorno y es obligatoria. No se incluyen credenciales
reales en código, ejemplos ni evidencias. Cada servicio utiliza su propio usuario
de base de datos en el despliegue compartido. La demo temporal es independiente.

Los DTO rechazan campos adicionales, enums desconocidos, UUID inválidos y fechas
fuera del formato acordado. El snapshot no incluye descripcion. Los mensajes de
error internos no exponen stacktrace ni detalles de conexión. Los logs son líneas
JSON con timestamp, level, service, correlationId y message. La correlación es
un string no vacío; el contrato compartido no exige que sea UUID. Swagger declara
JWT en las consultas para utilizar la autorización del gateway.

## 13. Patrones aplicados

CQRS: ticket-service conserva el modelo de escritura y reporting-service mantiene
su propia vista. La propagación asíncrona implica consistencia eventual: la consulta
puede tardar en reflejar una operación reciente.

Strategy: hay un proyector por cada evento, a través de Proyector. Los seis comparten
la transformación del snapshot, evitando recalcular prioridad o SLA. La política de
resolución contempla cierre y reapertura.

Repository: TicketViewRepository, EventoProcesadoRepository y ReportesRepository
encapsulan escritura condicional, inbox y agregaciones. Controladores y servicios
no necesitan manejar el modelo Mongo directamente.

## 16. Trabajo con Git y validación

La implementación conserva los diez commits originales de feat/reporting-proyecto.
Se integró origin/dev/entrega-1 mediante merge y se preparó un commit de correcciones
para el PR a dev/entrega-1. Incluye P6, su contrato y materiales, la entrada de P6
en el Compose raíz, verificaciones de CI y ajustes del README compartido. Los
archivos locales de trabajo quedan excluidos. No se realiza push ni se publica un PR.

La validación comprende compilación, TypeScript estricto, 22 pruebas unitarias,
5 pruebas del verificador de integración y 44 pruebas HTTP con MongoDB 7 real.
Incluye concurrencia, entrega
duplicada y desordenada, recuperación, reaperturas, errores comunes, paginación,
health y coherencia del Swagger con OpenAPI. Una demo adicional ejecuta dist/main.js
y captura respuestas reales para el anexo de evidencias.

El Compose raíz ya incluye P6 y el CI ejecuta test:e2e y una comprobación de login,
ticket crítico con IA mock, notificación y reportes por el gateway. El build de las
imágenes y la ejecución de ese flujo completo quedan pendientes del CI del PR:
Docker Desktop no tiene un engine disponible en esta sesión. Las pruebas del
verificador usan un gateway de prueba y no acreditan el flujo de los seis servicios.
También quedan pendientes las correcciones de P2 al publicar el estado de IA caída,
la recuperación de notificaciones de P5, las revisiones del equipo, la incorporación
de las evidencias de evaluación del proveedor real y los ensayos cronometrados.

## Anexo de evidencias

El archivo evidencias.json registra fecha, entorno, método, ruta, estado HTTP y
respuesta de la demo local. La exportación PDF incluye una representación de esas
respuestas, rotulada como evidencia local de P6. No reemplaza las capturas del
gateway, la clasificación real ni la demo completa del equipo.

## Fuentes

- CONTEXTO_PROYECTO.md y exportación local de Trello.
- NestJS: https://docs.nestjs.com/techniques/mongodb
- Validación NestJS: https://docs.nestjs.com/techniques/validation
- MongoDB updateOne: https://www.mongodb.com/docs/manual/reference/method/db.collection.updateOne/
