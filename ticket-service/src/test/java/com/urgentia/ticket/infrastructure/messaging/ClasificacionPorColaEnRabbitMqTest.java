package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.Esquemas;
import com.urgentia.ticket.infrastructure.MensajesDeIa;
import com.urgentia.ticket.infrastructure.RabbitMqRealBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.context.TestPropertySource;

/**
 * Clasificacion por la cola con un RabbitMQ real (CLASIFICACION_MODO=async). El test hace de worker
 * de classification-service: toma el ClasificacionSolicitada y contesta un ClasificacionGenerada.
 */
@TestPropertySource(properties = {"clasificacion.modo=async", "clasificacion.cola.espera-reintento-ms=50"})
class ClasificacionPorColaEnRabbitMqTest extends RabbitMqRealBase {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Test
    void crearClasificarEscalarYAvisarDePuntaAPunta() throws Exception {
        String id = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", "cid-punta")
                .andExpect(status().isCreated())).get("id").asText();
        assertThat(estado(id)).isEqualTo("PENDIENTE_CLASIFICACION");

        // 1. El pedido llega a la cola del worker de IA, con la descripcion y las propiedades del contrato.
        List<Message> solicitudes = esperarMensajes("clasificacion.solicitudes", 1, ESPERA);
        assertThat(solicitudes).hasSize(1);
        JsonNode solicitud = json.readTree(texto(solicitudes.get(0)));
        Esquemas.cumpleElDeClasificacion(solicitud);
        assertThat(solicitud.at("/payload/ticketId").asText()).isEqualTo(id);
        MessageProperties propiedades = solicitudes.get(0).getMessageProperties();
        assertThat(propiedades.getType()).isEqualTo("ClasificacionSolicitada");
        assertThat(propiedades.getMessageId()).isEqualTo(solicitud.get("eventId").asText());
        assertThat(propiedades.getCorrelationId()).isEqualTo("cid-punta");

        // 2. El "worker" contesta y ticket-service aplica la clasificacion: P1 y escalado.
        publicarRespuesta(MensajesDeIa.critica(UUID.randomUUID(), id, "cid-punta"));
        esperarEstado(id, "ESCALADO");

        // 3. Los eventos salen por urgentia.eventos: la guardia recibe el aviso y reportes ve todo.
        assertThat(esperarMensajes("notificaciones.eventos", 1, ESPERA))
                .extracting(mensaje -> mensaje.getMessageProperties().getType()).containsExactly("TicketEscalado");
        List<Message> reportes = esperarMensajes("reportes.eventos", 3, ESPERA);
        assertThat(reportes).extracting(mensaje -> mensaje.getMessageProperties().getType())
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        for (Message mensaje : reportes) {
            JsonNode evento = json.readTree(texto(mensaje));
            Esquemas.cumpleElDeEventos(evento);
            assertThat(evento.get("correlationId").asText()).isEqualTo("cid-punta");
            // La descripcion no sale nunca por urgentia.eventos.
            assertThat(evento.at("/payload/ticket").has("descripcion")).isFalse();
        }
        assertThat(mensajesEn("tickets.clasificaciones.dlq")).isEmpty();
    }

    @Test
    void unaRespuestaRepetidaNoDuplicaEfectos() throws Exception {
        String id = crearYTomarPedido("cid-repetido");
        String respuesta = MensajesDeIa.critica(UUID.randomUUID(), id, "cid-repetido");

        publicarRespuesta(respuesta);
        esperarEstado(id, "ESCALADO");
        assertThat(esperarMensajes("reportes.eventos", 3, ESPERA)).hasSize(3);
        long version = version(id);

        publicarRespuesta(respuesta);

        // Se confirma (la cola queda vacia), sin cambiar el ticket ni publicar eventos de nuevo.
        esperarColaVacia("tickets.clasificaciones");
        assertThat(mensajesEn("reportes.eventos")).isEmpty();
        assertThat(version(id)).isEqualTo(version);
        assertThat(mensajesEn("tickets.clasificaciones.dlq")).isEmpty();
    }

    @Test
    void unMensajeQueNoCumpleElContratoVaALaDeadLetter() throws Exception {
        String id = crearYTomarPedido("cid-invalido");

        publicarRespuesta("esto no es JSON");

        List<Message> muertos = esperarMensajes("tickets.clasificaciones.dlq", 1, ESPERA);
        assertThat(muertos).extracting(RabbitMqRealBase::texto).containsExactly("esto no es JSON");
        assertThat(estado(id)).isEqualTo("PENDIENTE_CLASIFICACION");
    }

    @Test
    void siLaIaNoPuedeClasificarElTicketSiguePendiente() throws Exception {
        String id = crearYTomarPedido("cid-error");

        publicarRespuesta(MensajesDeIa.conError(UUID.randomUUID(), id, "cid-error"));

        esperarColaVacia("tickets.clasificaciones");
        assertThat(estado(id)).isEqualTo("PENDIENTE_CLASIFICACION");
        assertThat(mensajesEn("tickets.clasificaciones.dlq")).isEmpty();
    }

    private String crearYTomarPedido(String correlationId) throws Exception {
        String id = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", correlationId)
                .andExpect(status().isCreated())).get("id").asText();
        assertThat(esperarMensajes("clasificacion.solicitudes", 1, ESPERA)).hasSize(1);
        return id;
    }

    /** Publica como el worker: exchange urgentia.clasificacion, routing key ClasificacionGenerada. */
    private void publicarRespuesta(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propiedades.setType("ClasificacionGenerada");
        propiedades.setAppId("classification-service");
        rabbitTemplate.send("urgentia.clasificacion", "ClasificacionGenerada",
                new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades));
    }

    private String estado(String id) throws Exception {
        return leer(mvc.perform(get("/api/tickets/" + id).header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE"))
                .andExpect(status().isOk())).get("estado").asText();
    }

    private void esperarEstado(String id, String esperado) throws Exception {
        long fin = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < fin) {
            if (esperado.equals(estado(id))) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(estado(id)).as("estado del ticket " + id).isEqualTo(esperado);
    }

    /** Espera a que el consumidor tome todo lo de la cola y le da un segundo para terminar de procesarlo. */
    private void esperarColaVacia(String cola) throws InterruptedException {
        long fin = System.nanoTime() + ESPERA.toNanos();
        while (System.nanoTime() < fin) {
            Integer listos = rabbitTemplate.execute(canal -> canal.queueDeclarePassive(cola).getMessageCount());
            if (listos != null && listos == 0) {
                Thread.sleep(1000);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("La cola " + cola + " no se vacio");
    }

    private long version(String id) {
        return jdbc.queryForObject("SELECT version FROM tickets WHERE id = ?", Long.class, UUID.fromString(id));
    }
}
