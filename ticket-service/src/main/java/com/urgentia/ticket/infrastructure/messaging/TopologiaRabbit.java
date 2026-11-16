package com.urgentia.ticket.infrastructure.messaging;

/**
 * Nombres de la topologia de RabbitMQ que usa ticket-service, tal cual estan en
 * contracts/messaging/ (seccion 10.4 del contexto). La topologia la carga el Compose
 * (rabbitmq-topologia): ticket-service no declara exchanges ni colas.
 */
public final class TopologiaRabbit {

    /** Exchange topic de los eventos de dominio; la routing key es el eventType. */
    public static final String EXCHANGE_EVENTOS = "urgentia.eventos";

    /** Exchange direct privado de la clasificacion (lleva la descripcion del ticket). */
    public static final String EXCHANGE_CLASIFICACION = "urgentia.clasificacion";

    /** Cola de la que ticket-service consume ClasificacionGenerada. */
    public static final String COLA_CLASIFICACIONES = "tickets.clasificaciones";

    public static final String CLASIFICACION_SOLICITADA = "ClasificacionSolicitada";
    public static final String CLASIFICACION_GENERADA = "ClasificacionGenerada";

    private TopologiaRabbit() {
    }
}
