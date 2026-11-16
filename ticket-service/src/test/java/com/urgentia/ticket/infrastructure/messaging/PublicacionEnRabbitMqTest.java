package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.Esquemas;
import com.urgentia.ticket.infrastructure.RabbitMqRealBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

/**
 * Con un RabbitMQ real y la topologia del contrato: los eventos del Outbox llegan a las colas
 * de notification-service y reporting-service, y con el broker caido no se pierde ninguno.
 */
class PublicacionEnRabbitMqTest extends RabbitMqRealBase {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Test
    void losEventosLleganACadaColaSegunSusBindings() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        String id = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", "cid-rabbit"))
                .get("id").asText();

        // reportes.eventos escucha todos (#); notificaciones.eventos, solo los que notifican.
        List<Message> reportes = esperarMensajes("reportes.eventos", 3, ESPERA);
        assertThat(reportes).extracting(mensaje -> mensaje.getMessageProperties().getType())
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        List<Message> notificaciones = esperarMensajes("notificaciones.eventos", 1, ESPERA);
        assertThat(notificaciones).extracting(mensaje -> mensaje.getMessageProperties().getType())
                .containsExactly("TicketEscalado");

        for (Message mensaje : reportes) {
            JsonNode sobre = json.readTree(texto(mensaje));
            Esquemas.cumpleElDeEventos(sobre);
            assertThat(sobre.at("/payload/ticket/ticketId").asText()).isEqualTo(id);
            MessageProperties propiedades = mensaje.getMessageProperties();
            assertThat(propiedades.getMessageId()).isEqualTo(sobre.get("eventId").asText());
            assertThat(propiedades.getCorrelationId()).isEqualTo("cid-rabbit");
            assertThat(propiedades.getAppId()).isEqualTo("ticket-service");
            assertThat(propiedades.getContentType()).isEqualTo("application/json");
            assertThat(propiedades.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(propiedades.getReceivedRoutingKey()).isEqualTo(sobre.get("eventType").asText());
        }
        assertThat(filasDelOutbox(id)).allSatisfy(fila -> assertThat(fila.get("publicado_en")).isNotNull());
    }

    @Test
    void conElBrokerCaidoElTicketSeCreaIgualYLosEventosSalenCuandoVuelve() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        apagarBroker();
        String id;
        try {
            // El alta no depende del broker: responde 201 y deja los eventos en el outbox.
            id = leer(crear("No puede ingresar nadie", "Producción caída, nadie puede entrar desde las 9"))
                    .get("id").asText();
            esperarIntentoFallido(id);
            assertThat(filasDelOutbox(id)).hasSize(3)
                    .allSatisfy(fila -> assertThat(fila.get("publicado_en")).isNull());
        } finally {
            prenderBroker();
        }

        List<Message> reportes = esperarMensajes("reportes.eventos", 3, Duration.ofSeconds(40));
        assertThat(reportes).extracting(mensaje -> json.readTree(texto(mensaje)).at("/payload/ticket/ticketId").asText())
                .containsOnly(id);
        assertThat(reportes).extracting(mensaje -> mensaje.getMessageProperties().getType())
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        assertThat(esperarMensajes("notificaciones.eventos", 1, ESPERA)).hasSize(1);
        assertThat(filasDelOutbox(id)).allSatisfy(fila -> assertThat(fila.get("publicado_en")).isNotNull());
    }

    private void esperarIntentoFallido(String ticketId) throws InterruptedException {
        long fin = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < fin) {
            List<Map<String, Object>> filas = filasDelOutbox(ticketId);
            if (!filas.isEmpty() && ((Number) filas.get(0).get("intentos")).intValue() > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El publicador nunca intento mandar los eventos del ticket " + ticketId);
    }
}
