package com.urgentia.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Fila de la tabla mensajes_procesados (Flyway V3): un mensaje de RabbitMQ que ya se aplico.
 * Implementa Persistable para que Spring Data siempre haga INSERT (persist): con un id asignado
 * a mano haria merge, y un eventId repetido pisaria la fila en vez de fallar.
 */
@Entity
@Table(name = "mensajes_procesados")
public class MensajeProcesadoJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tipo", nullable = false, length = 60)
    private String tipo;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected MensajeProcesadoJpaEntity() {
        // Para JPA
    }

    MensajeProcesadoJpaEntity(UUID eventId, String tipo, Instant procesadoEn) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.procesadoEn = procesadoEn;
    }

    public UUID getEventId() {
        return eventId;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    /** Las filas solo se insertan, nunca se actualizan. */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
