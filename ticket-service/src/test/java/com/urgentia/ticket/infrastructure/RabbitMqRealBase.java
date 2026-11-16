package com.urgentia.ticket.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests con un RabbitMQ real (Testcontainers), con la topologia del contrato
 * (contracts/messaging/rabbitmq-definitions.json) declarada antes de arrancar.
 * Prende el publicador del Outbox y el consumidor. Sin Docker, estos tests se saltean
 * (en el CI siempre corren).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class RabbitMqRealBase extends IntegracionBase {

    protected static final List<String> COLAS = List.of(
            "notificaciones.eventos", "reportes.eventos", "clasificacion.solicitudes", "tickets.clasificaciones",
            "notificaciones.eventos.dlq", "reportes.eventos.dlq", "clasificacion.solicitudes.dlq",
            "tickets.clasificaciones.dlq");

    private static final Path DEFINICIONES = Path.of("../contracts/messaging/rabbitmq-definitions.json");

    private static RabbitMQContainer rabbit;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin admin;

    /**
     * Un solo broker para todas las clases que extienden esta base (comparten el contexto de Spring).
     * Se arranca recien cuando Spring arma el contexto, asi sin Docker no se intenta nada.
     */
    protected static synchronized RabbitMQContainer rabbit() {
        if (rabbit == null) {
            rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
            rabbit.start();
            cargarTopologia(rabbit);
        }
        return rabbit;
    }

    @DynamicPropertySource
    static void brokerReal(DynamicPropertyRegistry propiedades) {
        // Base H2 propia: el publicador de este contexto corre en segundo plano y no tiene que
        // tocar el outbox de los otros tests (que esperan las filas sin publicar).
        propiedades.add("spring.datasource.url", () -> "jdbc:h2:mem:tickets-rabbit;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        propiedades.add("spring.rabbitmq.addresses", () -> "amqp://" + rabbit().getAdminUsername() + ":"
                + rabbit().getAdminPassword() + "@" + rabbit().getHost() + ":" + rabbit().getAmqpPort());
        propiedades.add("spring.rabbitmq.connection-timeout", () -> "2s");
        propiedades.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        propiedades.add("outbox.publicador.habilitado", () -> "true");
        propiedades.add("outbox.publicador.intervalo-ms", () -> "100");
        propiedades.add("outbox.publicador.timeout-confirmacion-ms", () -> "2000");
    }

    @BeforeEach
    void vaciarColas() {
        COLAS.forEach(cola -> admin.purgeQueue(cola, false));
    }

    /** Espera hasta tener la cantidad de mensajes pedida en la cola (los saca de la cola). */
    protected List<Message> esperarMensajes(String cola, int cantidad, Duration limite) {
        List<Message> recibidos = new ArrayList<>();
        long fin = System.nanoTime() + limite.toNanos();
        while (recibidos.size() < cantidad && System.nanoTime() < fin) {
            Message mensaje = rabbitTemplate.receive(cola, 200);
            if (mensaje != null) {
                recibidos.add(mensaje);
            }
        }
        return recibidos;
    }

    /** Lo que hay en una cola sin esperar: sirve para comprobar que no llego nada mas. */
    protected List<Message> mensajesEn(String cola) {
        return esperarMensajes(cola, Integer.MAX_VALUE, Duration.ofMillis(500));
    }

    protected static String texto(Message mensaje) {
        return new String(mensaje.getBody(), StandardCharsets.UTF_8);
    }

    /** Apaga la aplicacion RabbitMQ dentro del contenedor: el puerto deja de aceptar conexiones. */
    protected static void apagarBroker() throws Exception {
        rabbit().execInContainer("rabbitmqctl", "stop_app");
    }

    /** La vuelve a prender en el mismo puerto, con sus colas durables y sus mensajes persistentes. */
    protected static void prenderBroker() throws Exception {
        rabbit().execInContainer("rabbitmqctl", "start_app");
        rabbit().execInContainer("rabbitmqctl", "await_startup");
    }

    /**
     * Declara por AMQP los exchanges, las colas y los bindings de
     * contracts/messaging/rabbitmq-definitions.json, tal cual estan en el archivo (mismos tipos y
     * argumentos: colas quorum, x-delivery-limit, dead-letter). Al final comprueba que cada cola exista.
     */
    private static void cargarTopologia(RabbitMQContainer contenedor) {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(contenedor.getHost());
        fabrica.setPort(contenedor.getAmqpPort());
        fabrica.setUsername(contenedor.getAdminUsername());
        fabrica.setPassword(contenedor.getAdminPassword());
        try (Connection conexion = fabrica.newConnection(); Channel canal = conexion.createChannel()) {
            JsonNode definiciones = new ObjectMapper().readTree(DEFINICIONES.toFile());
            for (JsonNode exchange : definiciones.path("exchanges")) {
                canal.exchangeDeclare(exchange.get("name").asText(), exchange.get("type").asText(),
                        exchange.path("durable").asBoolean(true), exchange.path("auto_delete").asBoolean(false),
                        exchange.path("internal").asBoolean(false), argumentos(exchange.path("arguments")));
            }
            for (JsonNode cola : definiciones.path("queues")) {
                canal.queueDeclare(cola.get("name").asText(), cola.path("durable").asBoolean(true), false,
                        cola.path("auto_delete").asBoolean(false), argumentos(cola.path("arguments")));
            }
            for (JsonNode binding : definiciones.path("bindings")) {
                if ("queue".equals(binding.path("destination_type").asText())) {
                    canal.queueBind(binding.get("destination").asText(), binding.get("source").asText(),
                            binding.path("routing_key").asText(), argumentos(binding.path("arguments")));
                } else {
                    canal.exchangeBind(binding.get("destination").asText(), binding.get("source").asText(),
                            binding.path("routing_key").asText(), argumentos(binding.path("arguments")));
                }
            }
            for (String cola : COLAS) {
                canal.queueDeclarePassive(cola);
            }
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("No se pudo cargar la topologia de " + DEFINICIONES, e);
        }
    }

    private static Map<String, Object> argumentos(JsonNode nodo) {
        Map<String, Object> argumentos = new HashMap<>();
        nodo.properties().forEach(campo -> {
            JsonNode valor = campo.getValue();
            if (valor.isInt()) {
                argumentos.put(campo.getKey(), valor.intValue());
            } else if (valor.isLong()) {
                argumentos.put(campo.getKey(), valor.longValue());
            } else if (valor.isBoolean()) {
                argumentos.put(campo.getKey(), valor.booleanValue());
            } else {
                argumentos.put(campo.getKey(), valor.asText());
            }
        });
        return argumentos;
    }
}
