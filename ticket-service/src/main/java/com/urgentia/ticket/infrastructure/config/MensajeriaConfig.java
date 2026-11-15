package com.urgentia.ticket.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mensajeria con RabbitMQ (ADR-11 y ADR-12). La conexion, los publisher confirms y el ack
 * manual se configuran en application.yml (spring.rabbitmq). La topologia no se declara aca:
 * la carga el Compose desde contracts/messaging/rabbitmq-definitions.json.
 *
 * <p>Habilita las tareas programadas, que usa PublicadorDelOutbox para leer el outbox.
 */
@Configuration
@EnableScheduling
public class MensajeriaConfig {
}
