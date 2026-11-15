package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.Esquemas;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Transactional Outbox: cada caso de uso deja sus eventos en la tabla outbox, en la misma
 * transaccion que el ticket y en orden, con el sobre de la seccion 10.1, cumpliendo
 * contracts/events/ticket-events.schema.json y listos para urgentia.eventos (routing key = eventType).
 */
class EventosDeTicketTest extends IntegracionBase {

    @Test
    void elCasoDeLaDemoDejaCreadoClasificadoYEscaladoEnElOutbox() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        JsonNode ticket = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", "cid-eventos"));
        String id = ticket.get("id").asText();

        List<JsonNode> eventos = eventosDe(id);
        assertThat(tipos(eventos)).containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        eventos.forEach(Esquemas::cumpleElDeEventos);

        JsonNode escalado = eventos.get(2);
        assertThat(escalado.get("version").asInt()).isEqualTo(1);
        assertThat(escalado.get("source").asText()).isEqualTo("ticket-service");
        assertThat(escalado.get("correlationId").asText()).isEqualTo("cid-eventos");
        assertThat(escalado.at("/payload/estadoAnterior").asText()).isEqualTo("CLASIFICADO");
        assertThat(escalado.at("/payload/motivo").asText()).isEqualTo("Prioridad P1");
        assertThat(escalado.at("/payload/ticket/estado").asText()).isEqualTo("ESCALADO");
        assertThat(escalado.at("/payload/ticket/prioridad").asText()).isEqualTo("P1");
        assertThat(escalado.at("/payload/ticket/fechaLimiteSla").asText()).isEqualTo("2026-10-05T15:03:11Z");
        assertThat(escalado.at("/payload/ticket").has("descripcion")).isFalse();

        JsonNode creado = eventos.get(0);
        assertThat(creado.at("/payload/ticket/estado").asText()).isEqualTo("NUEVO");
        assertThat(creado.get("payload").has("estadoAnterior")).isFalse();
        assertThat(creado.at("/payload/ticket").has("prioridad")).isTrue();
        assertThat(creado.at("/payload/ticket/prioridad").isNull()).isTrue();

        // Cada evento tiene su propio id (los consumidores lo usan para la idempotencia).
        assertThat(eventos.stream().map(evento -> evento.get("eventId").asText()).distinct()).hasSize(3);
    }

    @Test
    void cadaFilaSabeAQueExchangeVaYConQueRoutingKey() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        String id = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", "cid-filas"))
                .get("id").asText();

        List<Map<String, Object>> filas = filasDelOutbox(id);
        assertThat(filas).hasSize(3).allSatisfy(fila -> {
            JsonNode sobre = json.readTree((String) fila.get("cuerpo"));
            assertThat(fila.get("exchange")).isEqualTo("urgentia.eventos");
            assertThat(fila.get("routing_key")).isEqualTo(sobre.get("eventType").asText());
            assertThat(fila.get("event_type")).isEqualTo(sobre.get("eventType").asText());
            assertThat(fila.get("event_id").toString()).isEqualTo(sobre.get("eventId").asText());
            assertThat(fila.get("correlation_id")).isEqualTo("cid-filas");
            // Todavia no salio: lo publica PublicadorDelOutbox cuando el broker lo confirma.
            assertThat(fila.get("publicado_en")).isNull();
            assertThat(fila.get("intentos")).isEqualTo(0);
        });
    }

    @Test
    void conLaIaCaidaSoloQuedaTicketCreado() throws Exception {
        IA.responder(RUTA_IA, Respuesta.vacia(500));
        String id = leer(crear("No puede ingresar nadie", "Nadie puede entrar al sistema desde las 9"))
                .get("id").asText();

        List<JsonNode> eventos = eventosDe(id);
        assertThat(tipos(eventos)).containsExactly("TicketCreado");
        assertThat(eventos.get(0).at("/payload/ticket/estado").asText()).isEqualTo("NUEVO");
        Esquemas.cumpleElDeEventos(eventos.get(0));
    }

    @Test
    void asignarYResolverDejanLosEventosDeLaSeccion85() throws Exception {
        String id = crearConIa(RespuestasIa.comun()).get("id").asText();

        patchJson("/api/tickets/" + id + "/asignacion", "{\"agenteId\":\"" + GUARDIA + "\"}");
        patchJson("/api/tickets/" + id + "/estado", "{\"estado\":\"EN_CURSO\"}");
        patchJson("/api/tickets/" + id + "/estado", "{\"estado\":\"RESUELTO\"}");

        List<JsonNode> eventos = eventosDe(id);
        assertThat(tipos(eventos)).containsExactly(
                "TicketCreado", "TicketClasificado",
                "TicketAsignado", "TicketEstadoCambiado",
                "TicketEstadoCambiado",
                "TicketEstadoCambiado", "TicketResuelto");
        eventos.forEach(Esquemas::cumpleElDeEventos);
        assertThat(eventos.get(2).at("/payload/ticket/agenteAsignadoId").asText()).isEqualTo(GUARDIA);
        assertThat(eventos.get(6).at("/payload/estadoAnterior").asText()).isEqualTo("EN_CURSO");
    }

    @Test
    void unaOperacionRechazadaNoDejaNadaEnElOutbox() throws Exception {
        String id = crearConIa(RespuestasIa.comun()).get("id").asText();

        mvc.perform(patch("/api/tickets/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CERRADO\"}"))
                .andExpect(status().isConflict());

        // La transaccion se deshizo: no hay ningun evento nuevo esperando salir.
        assertThat(tipos(eventosDe(id))).containsExactly("TicketCreado", "TicketClasificado");
    }

    private void patchJson(String ruta, String cuerpo) throws Exception {
        mvc.perform(patch(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
    }
}
