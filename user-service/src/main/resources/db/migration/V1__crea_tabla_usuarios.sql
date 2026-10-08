-- Tabla del agregado Usuario (user-service, base users_db).
CREATE TABLE usuarios (
    id            UUID                     NOT NULL PRIMARY KEY,
    nombre        VARCHAR(120)             NOT NULL,
    email         VARCHAR(254)             NOT NULL UNIQUE,
    password_hash VARCHAR(100)             NOT NULL,
    rol           VARCHAR(20)              NOT NULL,
    activo        BOOLEAN                  NOT NULL DEFAULT TRUE,
    fecha_alta    TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Login (findByEmailIgnoreCase/existsByEmailIgnoreCase) y listado filtrado por rol.
CREATE INDEX idx_usuarios_rol ON usuarios (rol);
