-- Tabla del agregado Ticket (ticket-service, base tickets_db).
-- Las columnas de la clasificacion quedan en null mientras el ticket no este clasificado.
CREATE TABLE tickets (
    id                       UUID                     NOT NULL PRIMARY KEY,
    titulo                   VARCHAR(120)             NOT NULL,
    descripcion              VARCHAR(2000)            NOT NULL,
    solicitante_id           UUID                     NOT NULL,
    agente_asignado_id       UUID,
    estado                   VARCHAR(30)              NOT NULL,
    prioridad                VARCHAR(2),
    fecha_limite_sla         TIMESTAMP WITH TIME ZONE,
    requiere_revision_manual BOOLEAN                  NOT NULL,
    motivo_escalamiento      VARCHAR(500),
    categoria                VARCHAR(20),
    urgencia                 VARCHAR(10),
    impacto                  VARCHAR(10),
    modulo_afectado          VARCHAR(30),
    requiere_escalamiento    BOOLEAN,
    confianza                DOUBLE PRECISION,
    justificacion            VARCHAR(300),
    proveedor                VARCHAR(100),
    fecha_clasificacion      TIMESTAMP WITH TIME ZONE,
    fecha_creacion           TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_actualizacion      TIMESTAMP WITH TIME ZONE NOT NULL,
    version                  BIGINT                   NOT NULL
);

-- Filtros del listado (GET /api/tickets) y vista "mis tickets" del solicitante.
CREATE INDEX idx_tickets_estado ON tickets (estado);
CREATE INDEX idx_tickets_prioridad_fecha ON tickets (prioridad, fecha_creacion);
CREATE INDEX idx_tickets_solicitante ON tickets (solicitante_id);
