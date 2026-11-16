-- Mensajes de RabbitMQ que ticket-service ya proceso (hoy, ClasificacionGenerada).
-- La entrega es "al menos una vez": si un mensaje llega de nuevo, su eventId ya esta aca y
-- el consumidor lo confirma sin volver a aplicarlo. Se guarda en la misma transaccion que
-- los cambios del ticket, asi o queda todo o no queda nada.
CREATE TABLE mensajes_procesados (
    event_id     UUID                     NOT NULL PRIMARY KEY,
    tipo         VARCHAR(60)              NOT NULL,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL
);
