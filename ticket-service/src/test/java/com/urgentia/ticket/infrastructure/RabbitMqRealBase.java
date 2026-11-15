package com.urgentia.ticket.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
 * cargada igual que en el Compose: POST /api/definitions con contracts/messaging/rabbitmq-definitions.json.
 * Prende el publicador del Outbox y el consumidor. Sin Docker, estos tests se saltean
 * (en el CI siempre corren).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class RabbitMqRealBase extends IntegracionBase {

    protected static final List<String> COLAS = List.of(
            "notificaciones.eventos", "reportes.eventos", "clasificacion.solicitudes", "tickets.clasificaciones",
            "notificaciones.eventos.dlq", "reportes.eventos.dlq", "clasificacion.solicitudes.dlq",
            "tickets.clasificaciones.dlq");

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

    private static void cargarTopologia(RabbitMQContainer contenedor) {
        try {
            String credenciales = Base64.getEncoder().encodeToString(
                    (contenedor.getAdminUsername() + ":" + contenedor.getAdminPassword())
                            .getBytes(StandardCharsets.UTF_8));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(contenedor.getHttpUrl() + "/api/definitions"))
                    .header("Authorization", "Basic " + credenciales)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofFile(
                            Path.of("../contracts/messaging/rabbitmq-definitions.json")))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> respuesta = HttpClient.newHttpClient()
                    .send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                throw new IllegalStateException("No se pudo cargar la topologia: " + respuesta.statusCode() + " "
                        + respuesta.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la topologia", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio la carga de la topologia", e);
        }
    }
}
