package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Los eventos salen despues del commit, en orden, a los dos suscriptores, con el sobre
 * de la seccion 10.1 y cumpliendo contracts/events/ticket-events.schema.json.
 */
class EventosDeTicketTest extends IntegracionBase {

    private static final Path ESQUEMA = Path.of("../contracts/events/ticket-events.schema.json");
    private static JsonSchema esquema;

    @BeforeAll
    static void cargarEsquema() throws IOException {
        try (InputStream entrada = Files.newInputStream(ESQUEMA)) {
            esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        }
    }

    @Test
    void elCasoDeLaDemoPublicaCreadoClasificadoYEscaladoAAmbosSuscriptores() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        JsonNode ticket = leer(crearComo(SOLICITANTE, "No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9", "cid-eventos"));
        String id = ticket.get("id").asText();

        for (String ruta : List.of(RUTA_NOTIFICACIONES, RUTA_REPORTES)) {
            List<JsonNode> eventos = eventosDe(ruta, id, 3);
            assertThat(tipos(eventos)).as(ruta)
                    .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
            eventos.forEach(this::cumpleElEsquema);

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
        assertThat(SUSCRIPTORES.pedidos(pedido -> pedido.cuerpo().contains(id)))
                .allSatisfy(pedido -> assertThat(pedido.header("X-Correlation-Id")).isEqualTo("cid-eventos"));
    }

    @Test
    void conLaIaCaidaSoloSaleTicketCreado() throws Exception {
        IA.responder(RUTA_IA, Respuesta.vacia(500));
        String id = leer(crear("No puede ingresar nadie", "Nadie puede entrar al sistema desde las 9"))
                .get("id").asText();

        List<JsonNode> eventos = eventosDe(RUTA_REPORTES, id, 1);
        Thread.sleep(300);
        eventos = eventosDe(RUTA_REPORTES, id, 1);
        assertThat(tipos(eventos)).containsExactly("TicketCreado");
        assertThat(eventos.get(0).at("/payload/ticket/estado").asText()).isEqualTo("NUEVO");
        cumpleElEsquema(eventos.get(0));
    }

    @Test
    void asignarYResolverPublicanLosEventosDeLaSeccion85() throws Exception {
        String id = crearConIa(RespuestasIa.comun()).get("id").asText();
        eventosDe(RUTA_NOTIFICACIONES, id, 2);

        patchJson("/api/tickets/" + id + "/asignacion", "{\"agenteId\":\"" + GUARDIA + "\"}");
        patchJson("/api/tickets/" + id + "/estado", "{\"estado\":\"EN_CURSO\"}");
        patchJson("/api/tickets/" + id + "/estado", "{\"estado\":\"RESUELTO\"}");

        List<JsonNode> eventos = eventosDe(RUTA_NOTIFICACIONES, id, 7);
        assertThat(tipos(eventos)).containsExactly(
                "TicketCreado", "TicketClasificado",
                "TicketAsignado", "TicketEstadoCambiado",
                "TicketEstadoCambiado",
                "TicketEstadoCambiado", "TicketResuelto");
        eventos.forEach(this::cumpleElEsquema);
        assertThat(eventos.get(2).at("/payload/ticket/agenteAsignadoId").asText()).isEqualTo(GUARDIA);
        assertThat(eventos.get(6).at("/payload/estadoAnterior").asText()).isEqualTo("EN_CURSO");
    }

    @Test
    void unaOperacionRechazadaNoPublicaNada() throws Exception {
        String id = crearConIa(RespuestasIa.comun()).get("id").asText();
        eventosDe(RUTA_REPORTES, id, 2);

        mvc.perform(patch("/api/tickets/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CERRADO\"}"))
                .andExpect(status().isConflict());

        Thread.sleep(300);
        assertThat(tipos(eventosDe(RUTA_REPORTES, id, 2))).containsExactly("TicketCreado", "TicketClasificado");
    }

    private void patchJson(String ruta, String cuerpo) throws Exception {
        mvc.perform(patch(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
    }

    private void cumpleElEsquema(JsonNode evento) {
        Set<ValidationMessage> errores = esquema.validate(evento);
        assertThat(errores).as("errores de esquema en " + evento).isEmpty();
    }
}
