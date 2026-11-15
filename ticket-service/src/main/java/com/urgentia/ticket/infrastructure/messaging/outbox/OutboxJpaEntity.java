package com.urgentia.ticket.infrastructure.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla outbox: un mensaje listo para publicar en RabbitMQ, con el sobre ya armado
 * en JSON y a que exchange y con que routing key va. La tabla la crea Flyway (V2).
 */
@Entity
@Table(name = "outbox")
public class OutboxJpaEntity {

    static final int ERROR_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "exchange", nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "cuerpo", nullable = false, length = 8000)
    private String cuerpo;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = ERROR_MAX)
    private String ultimoError;

    protected OutboxJpaEntity() {
        // Para JPA
    }

    OutboxJpaEntity(MensajeSaliente mensaje, Instant creadoEn) {
        this.eventId = mensaje.eventId();
        this.eventType = mensaje.eventType();
        this.exchange = mensaje.exchange();
        this.routingKey = mensaje.routingKey();
        this.correlationId = mensaje.correlationId();
        this.ticketId = mensaje.ticketId();
        this.cuerpo = mensaje.cuerpo();
        this.ocurridoEn = mensaje.ocurridoEn();
        this.creadoEn = creadoEn;
        this.intentos = 0;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public int getIntentos() {
        return intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }
}
