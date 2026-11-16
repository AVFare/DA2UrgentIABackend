package com.urgentia.ticket.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Outbox (ADR-12): guarda los mensajes a publicar en la tabla outbox.
 *
 * <p>Exige una transaccion abierta (MANDATORY): la del caso de uso que guarda el ticket. Asi el
 * ticket y sus mensajes se confirman o se deshacen juntos, y ningun mensaje sale antes del commit.
 * La publicacion en RabbitMQ la hace despues {@link PublicadorDelOutbox}.
 */
@Component
public class Outbox {

    private final SpringDataOutboxRepository repositorio;
    private final Clock reloj;

    public Outbox(SpringDataOutboxRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(MensajeSaliente mensaje) {
        repositorio.save(new OutboxJpaEntity(mensaje, Instant.now(reloj)));
    }
}
