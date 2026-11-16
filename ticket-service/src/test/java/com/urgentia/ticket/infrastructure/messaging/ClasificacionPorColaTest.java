package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.Esquemas;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.MensajesDeIa;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import com.urgentia.ticket.infrastructure.messaging.ManejadorDeClasificaciones.Accion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Clasificacion por la cola (CLASIFICACION_MODO=async), sin broker: el alta deja el pedido en el
 * outbox y la respuesta de la IA entra por ManejadorDeClasificaciones, como la entregaria RabbitMQ.
 */
@TestPropertySource(properties = "clasificacion.modo=async")
class ClasificacionPorColaTest extends IntegracionBase {

    private static final String DESCRIPCION = "Producción caída, todos los usuarios bloqueados en el login desde las 9";

    @Autowired
    private ManejadorDeClasificaciones manejador;

    @Test
    void elAltaRespondePendienteSinLlamarALaIaYDejaElPedidoEnElOutbox() throws Exception {
        JsonNode ticket = leer(crearComo(SOLICITANTE, "No puede ingresar nadie", DESCRIPCION, "cid-cola")
                .andExpect(status().isCreated()));
        String id = ticket.get("id").asText();

        assertThat(ticket.get("estado").asText()).isEqualTo("PENDIENTE_CLASIFICACION");
        assertThat(ticket.get("prioridad").isNull()).isTrue();
        assertThat(ticket.get("fechaLimiteSla").isNull()).isTrue();
        assertThat(ticket.get("clasificacion").isNull()).isTrue();
        assertThat(IA.pedidos(pedido -> true)).isEmpty();

        // Primero TicketCreado (a urgentia.eventos), despues el pedido a la IA (a urgentia.clasificacion).
        List<Map<String, Object>> filas = filasDelOutbox(id);
        assertThat(filas).extracting(fila -> fila.get("event_type"))
                .containsExactly("TicketCreado", "ClasificacionSolicitada");
        Map<String, Object> pedido = filas.get(1);
        assertThat(pedido.get("exchange")).isEqualTo("urgentia.clasificacion");
        assertThat(pedido.get("routing_key")).isEqualTo("ClasificacionSolicitada");
        assertThat(pedido.get("correlation_id")).isEqualTo("cid-cola");

        JsonNode solicitud = mensajesDelOutbox("urgentia.clasificacion", id).get(0);
        Esquemas.cumpleElDeClasificacion(solicitud);
        assertThat(solicitud.get("source").asText()).isEqualTo("ticket-service");
        assertThat(solicitud.get("correlationId").asText()).isEqualTo("cid-cola");
        assertThat(solicitud.at("/payload/ticketId").asText()).isEqualTo(id);
        assertThat(solicitud.at("/payload/descripcion").asText()).isEqualTo(DESCRIPCION);
        assertThat(pedido.get("event_id").toString()).isEqualTo(solicitud.get("eventId").asText());

        // La descripcion viaja solo en el pedido privado, nunca en los eventos de dominio.
        JsonNode creado = eventosDe(id).get(0);
        Esquemas.cumpleElDeEventos(creado);
        assertThat(creado.at("/payload/ticket").has("descripcion")).isFalse();
    }

    @Test
    void laRespuestaCriticaDeLaIaDejaElTicketP1YEscalado() throws Exception {
        String id = crearPendiente("cid-demo");
        String mensaje = MensajesDeIa.critica(UUID.randomUUID(), id, "cid-demo");
        Esquemas.cumpleElDeClasificacion(json.readTree(mensaje));

        assertThat(manejar(mensaje)).isEqualTo(Accion.CONFIRMAR);

        JsonNode ticket = obtener(id);
        assertThat(ticket.get("estado").asText()).isEqualTo("ESCALADO");
        assertThat(ticket.get("prioridad").asText()).isEqualTo("P1");
        assertThat(ticket.get("motivoEscalamiento").asText()).isEqualTo("Prioridad P1");
        assertThat(ticket.get("fechaLimiteSla").asText()).isEqualTo("2026-11-20T15:03:11Z");
        assertThat(ticket.at("/clasificacion/confianza").asDouble()).isEqualTo(0.93);

        List<JsonNode> eventos = eventosDe(id);
        assertThat(tipos(eventos)).containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        eventos.forEach(Esquemas::cumpleElDeEventos);
        // Los eventos nuevos siguen la correlacion del pedido original.
        assertThat(eventos.get(2).get("correlationId").asText()).isEqualTo("cid-demo");
    }

    @Test
    void unMensajeRepetidoSeConfirmaSinDuplicarEfectos() throws Exception {
        String id = crearPendiente("cid-repetido");
        String mensaje = MensajesDeIa.critica(UUID.randomUUID(), id, "cid-repetido");
        manejar(mensaje);
        int filas = filasDelOutbox(id).size();
        long version = version(id);

        assertThat(manejar(mensaje)).isEqualTo(Accion.CONFIRMAR);

        assertThat(filasDelOutbox(id)).hasSize(filas);
        assertThat(version(id)).isEqualTo(version);
    }

    @Test
    void conErrorDeLaIaSiguePendienteYSePuedeReclasificarPorRest() throws Exception {
        String id = crearPendiente("cid-error");
        String mensaje = MensajesDeIa.conError(UUID.randomUUID(), id, "cid-error");
        Esquemas.cumpleElDeClasificacion(json.readTree(mensaje));

        assertThat(manejar(mensaje)).isEqualTo(Accion.CONFIRMAR);
        assertThat(obtener(id).get("estado").asText()).isEqualTo("PENDIENTE_CLASIFICACION");
        assertThat(tipos(eventosDe(id))).containsExactly("TicketCreado");

        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.comun()));
        JsonNode reclasificado = leer(mvc.perform(post("/api/tickets/" + id + "/reclasificacion")
                .header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE")).andExpect(status().isOk()));
        assertThat(reclasificado.get("estado").asText()).isEqualTo("CLASIFICADO");
    }

    @Test
    void siYaSeReclasificoPorRestLaRespuestaDeLaColaSeIgnora() throws Exception {
        String id = crearPendiente("cid-tarde");
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.comun()));
        mvc.perform(post("/api/tickets/" + id + "/reclasificacion")
                .header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE")).andExpect(status().isOk());

        assertThat(manejar(MensajesDeIa.critica(UUID.randomUUID(), id, "cid-tarde"))).isEqualTo(Accion.CONFIRMAR);

        assertThat(obtener(id).get("estado").asText()).isEqualTo("CLASIFICADO");
        assertThat(tipos(eventosDe(id))).containsExactly("TicketCreado", "TicketClasificado");
    }

    @Test
    void loQueNoCumpleElContratoSeDescartaSinTocarElTicket() throws Exception {
        String id = crearPendiente("cid-invalido");
        String valido = MensajesDeIa.critica(UUID.randomUUID(), id, "cid-invalido");

        assertThat(manejar("esto no es JSON")).isEqualTo(Accion.DESCARTAR);
        assertThat(manejar("[1, 2]")).isEqualTo(Accion.DESCARTAR);
        assertThat(manejar(valido.replace("\"ClasificacionGenerada\"", "\"TicketCreado\""))).isEqualTo(Accion.DESCARTAR);
        assertThat(manejar(valido.replace("\"classification-service\"", "\"otro\""))).isEqualTo(Accion.DESCARTAR);
        assertThat(manejar(valido.replace("\"urgencia\": \"ALTA\"", "\"urgencia\": \"ALTISIMA\"")))
                .isEqualTo(Accion.DESCARTAR);
        assertThat(manejar(valido.replace("\"confianza\": 0.93", "\"confianza\": 1.7"))).isEqualTo(Accion.DESCARTAR);
        assertThat(manejar(valido.replace("\"resultado\": \"CLASIFICADO\"", "\"resultado\": \"OK\"")))
                .isEqualTo(Accion.DESCARTAR);

        assertThat(obtener(id).get("estado").asText()).isEqualTo("PENDIENTE_CLASIFICACION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensajes_procesados", Long.class)).isZero();
    }


    @Test
    void unaRespuestaParaUnTicketQueNoExisteSeConfirmaSinEfectos() {
        // Por ejemplo, un ClasificacionSolicitada publicado a mano en una prueba de la mensajeria.
        assertThat(manejar(MensajesDeIa.critica(UUID.randomUUID(), UUID.randomUUID().toString(), "cid-x")))
                .isEqualTo(Accion.CONFIRMAR);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Long.class)).isZero();
    }

    private String crearPendiente(String correlationId) throws Exception {
        return leer(crearComo(SOLICITANTE, "No puede ingresar nadie", DESCRIPCION, correlationId)
                .andExpect(status().isCreated())).get("id").asText();
    }

    private Accion manejar(String mensaje) {
        return manejador.manejar(mensaje.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode obtener(String id) throws Exception {
        return leer(mvc.perform(get("/api/tickets/" + id).header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE"))
                .andExpect(status().isOk()));
    }

    private long version(String id) {
        return jdbc.queryForObject("SELECT version FROM tickets WHERE id = ?", Long.class, UUID.fromString(id));
    }
}
