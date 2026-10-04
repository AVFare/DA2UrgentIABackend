package com.urgentia.ticket.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.ticket.infrastructure.ServidorFalso.Pedido;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base de los tests de integracion: levanta el servicio completo (H2 en memoria) y dos
 * servidores de mentira, uno que hace de classification-service y otro que hace de los
 * dos suscriptores de eventos (notification-service y reporting-service).
 * Todas las clases que la extienden comparten el mismo contexto de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracionBase {

    protected static final ServidorFalso IA = new ServidorFalso();
    protected static final ServidorFalso SUSCRIPTORES = new ServidorFalso();
    protected static final String RUTA_IA = "/api/clasificaciones";
    protected static final String RUTA_NOTIFICACIONES = "/notificaciones/api/eventos";
    protected static final String RUTA_REPORTES = "/reportes/api/eventos";

    protected static final String SOLICITANTE = "b1c2d3e4-0000-4000-8000-000000000004";
    protected static final String GUARDIA = "b1c2d3e4-0000-4000-8000-000000000002";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacciones;

    @DynamicPropertySource
    static void servidoresFalsos(DynamicPropertyRegistry propiedades) {
        propiedades.add("clasificacion.url", IA::url);
        propiedades.add("eventos.suscriptores",
                () -> SUSCRIPTORES.url() + RUTA_NOTIFICACIONES + "," + SUSCRIPTORES.url() + RUTA_REPORTES);
    }

    @BeforeEach
    void empezarDeCero() {
        IA.limpiar();
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.comun()));
        SUSCRIPTORES.limpiar();
        SUSCRIPTORES.responderPorDefecto(Respuesta.json(202, "{\"resultado\":\"PROCESADO\"}"));
        // Con auto-commit apagado, el borrado tiene que ir dentro de una transaccion.
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> jdbc.update("DELETE FROM tickets"));
    }

    /** POST /api/tickets como el solicitante de la demo. */
    protected ResultActions crear(String titulo, String descripcion) throws Exception {
        return crearComo(SOLICITANTE, titulo, descripcion, "cid-" + UUID.randomUUID());
    }

    protected ResultActions crearComo(String usuarioId, String titulo, String descripcion, String correlationId)
            throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("titulo", titulo, "descripcion", descripcion));
        return mvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", usuarioId)
                .header("X-User-Rol", "SOLICITANTE")
                .header("X-Correlation-Id", correlationId)
                .content(cuerpo));
    }

    /** Crea el ticket de la demo con la IA respondiendo lo que se le pase y devuelve el JSON de la respuesta. */
    protected JsonNode crearConIa(String respuestaIa) throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, respuestaIa));
        String cuerpo = crear("No puede ingresar nadie",
                "Producción caída, todos los usuarios bloqueados en el login desde las 9")
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo);
    }

    protected JsonNode leer(ResultActions resultado) throws Exception {
        // El JSON viaja en UTF-8 (MockMvc por defecto lo leeria como ISO-8859-1).
        return json.readTree(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Eventos (ya parseados) que llegaron a un suscriptor para un ticket, en orden de llegada. */
    protected List<JsonNode> eventosDe(String ruta, String ticketId, int esperados) {
        List<Pedido> pedidos = SUSCRIPTORES.esperarPedidos(
                pedido -> pedido.ruta().equals(ruta) && pedido.cuerpo().contains(ticketId),
                esperados, Duration.ofSeconds(5));
        return pedidos.stream().map(pedido -> {
            try {
                return json.readTree(pedido.cuerpo());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    protected static List<String> tipos(List<JsonNode> eventos) {
        return eventos.stream().map(evento -> evento.get("eventType").asText()).toList();
    }
}
