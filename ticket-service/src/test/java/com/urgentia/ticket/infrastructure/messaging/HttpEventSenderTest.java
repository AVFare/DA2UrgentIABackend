package com.urgentia.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.urgentia.ticket.domain.TicketBuilder;
import com.urgentia.ticket.domain.event.TicketCreado;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.infrastructure.ServidorFalso;
import com.urgentia.ticket.infrastructure.ServidorFalso.Pedido;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/** Entrega por HTTP: orden por suscriptor, reintentos 1-2-4 (aca acelerados) y 4xx sin reintento. */
class HttpEventSenderTest {

    private static final String RUTA = "/api/eventos";

    private final ServidorFalso suscriptor = new ServidorFalso();
    private HttpEventSender enviador;

    @AfterEach
    void apagar() throws InterruptedException {
        if (enviador != null) {
            enviador.destroy();
        }
        suscriptor.detener();
    }

    @Test
    void entregaLosEventosEnElOrdenEnQueSePublicaron() {
        suscriptor.responder(RUTA, Respuesta.vacia(202));
        enviador = enviador(suscriptor.url() + RUTA);
        List<String> enviados = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            EventoEnvelope sobre = sobre();
            enviados.add(sobre.eventId().toString());
            enviador.enviar(sobre);
        }

        List<Pedido> recibidos = suscriptor.esperarPedidos(pedido -> true, 10, Duration.ofSeconds(5));
        assertThat(recibidos).extracting(pedido -> idDe(pedido)).containsExactlyElementsOf(enviados);
        assertThat(recibidos.get(0).header("Content-Type")).startsWith("application/json");
        assertThat(recibidos.get(0).header("X-Correlation-Id")).isEqualTo("cid-test");
        assertThat(recibidos.get(0).cuerpo()).contains("\"fechaCreacion\":\"2026-10-05T14:03:10Z\"");
    }

    @Test
    void reintentaHastaQueElSuscriptorResponde() {
        suscriptor.responderEnOrden(RUTA, Respuesta.vacia(503), Respuesta.vacia(500), Respuesta.vacia(202));
        enviador = enviador(suscriptor.url() + RUTA);

        enviador.enviar(sobre());

        assertThat(suscriptor.esperarPedidos(pedido -> true, 3, Duration.ofSeconds(5))).hasSize(3);
        esperar(300);
        assertThat(suscriptor.pedidos()).hasSize(3);
    }

    @Test
    void despuesDeTresReintentosSeRinde() {
        suscriptor.responder(RUTA, Respuesta.vacia(500));
        enviador = enviador(suscriptor.url() + RUTA);

        enviador.enviar(sobre());

        assertThat(suscriptor.esperarPedidos(pedido -> true, 4, Duration.ofSeconds(5))).hasSize(4);
        esperar(300);
        assertThat(suscriptor.pedidos()).as("1 intento + 3 reintentos").hasSize(4);
    }

    @Test
    void unRechazo4xxNoSeReintenta() {
        suscriptor.responder(RUTA, Respuesta.json(400, "{\"codigo\":\"VALIDACION\"}"));
        enviador = enviador(suscriptor.url() + RUTA);

        enviador.enviar(sobre());

        assertThat(suscriptor.esperarPedidos(pedido -> true, 1, Duration.ofSeconds(5))).hasSize(1);
        esperar(300);
        assertThat(suscriptor.pedidos()).hasSize(1);
    }

    @Test
    void siUnSuscriptorEstaCaidoElOtroIgualRecibe() {
        suscriptor.responder(RUTA, Respuesta.vacia(202));
        enviador = enviador("http://127.0.0.1:1/api/eventos," + suscriptor.url() + RUTA);

        enviador.enviar(sobre());

        assertThat(suscriptor.esperarPedidos(pedido -> true, 1, Duration.ofSeconds(5))).hasSize(1);
        assertThat(enviador.suscriptores()).hasSize(2);
    }

    @Test
    void sinSuscriptoresNoHaceNada() {
        enviador = enviador("  ");

        enviador.enviar(sobre());

        assertThat(enviador.suscriptores()).isEmpty();
    }

    private static HttpEventSender enviador(String suscriptores) {
        // Mismo ObjectMapper que arma Spring Boot: fechas como texto ISO-8601.
        MappingJackson2HttpMessageConverter conversor = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build());
        RestClient.Builder builder = RestClient.builder().messageConverters(lista -> lista.add(0, conversor));
        return new HttpEventSender(builder, suscriptores, "10,10,10", 1000);
    }

    private static EventoEnvelope sobre() {
        Ticket ticket = TicketBuilder.unTicket().crear();
        TicketCreado creado = (TicketCreado) ticket.extraerEventos().get(0);
        return EventoEnvelope.desde(creado, "cid-test");
    }

    private static String idDe(Pedido pedido) {
        int inicio = pedido.cuerpo().indexOf("\"eventId\":\"") + 11;
        return pedido.cuerpo().substring(inicio, inicio + 36);
    }

    private static void esperar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
