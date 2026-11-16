package com.urgentia.ticket.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PublicadorDelOutbox con un broker de mentira: publica en orden, marca lo confirmado y,
 * si el broker no confirma, no marca nada y lo reintenta despues. Nada se pierde.
 */
class PublicadorDelOutboxTest extends IntegracionBase {

    @Autowired
    private SpringDataOutboxRepository repositorio;

    private final BrokerFalso broker = new BrokerFalso();
    private PublicadorDelOutbox publicador;

    @BeforeEach
    void armarPublicador() {
        publicador = new PublicadorDelOutbox(repositorio, broker, Clock.systemUTC(), 100);
    }

    @Test
    void publicaLasPendientesEnOrdenYLasMarcaComoPublicadas() throws Exception {
        String id = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();

        assertThat(publicador.publicarPendientes()).isEqualTo(3);

        assertThat(broker.recibidos).extracting(MensajeSaliente::eventType)
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        assertThat(broker.recibidos).allSatisfy(mensaje -> {
            assertThat(mensaje.exchange()).isEqualTo("urgentia.eventos");
            assertThat(mensaje.routingKey()).isEqualTo(mensaje.eventType());
        });
        assertThat(filasDelOutbox(id)).allSatisfy(fila -> {
            assertThat(fila.get("publicado_en")).isNotNull();
            assertThat(fila.get("intentos")).isEqualTo(1);
        });
        // Lo ya publicado no vuelve a salir.
        assertThat(publicador.publicarPendientes()).isZero();
        assertThat(broker.recibidos).hasSize(3);
    }

    @Test
    void conElBrokerCaidoNoSePierdeNadaYSalePocoDespuesDeQueVuelve() throws Exception {
        broker.caido = true;
        String id = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();

        assertThat(publicador.publicarPendientes()).isZero();
        assertThat(publicador.publicarPendientes()).isZero();

        // Corta en la primera que falla (para no desordenar) y deja registrado el intento.
        List<Map<String, Object>> filas = filasDelOutbox(id);
        assertThat(filas).allSatisfy(fila -> assertThat(fila.get("publicado_en")).isNull());
        assertThat(filas.get(0).get("intentos")).isEqualTo(2);
        assertThat(filas.get(0).get("ultimo_error")).isEqualTo("Connection refused (simulado)");
        assertThat(filas.get(1).get("intentos")).isEqualTo(0);

        broker.caido = false;
        assertThat(publicador.publicarPendientes()).isEqualTo(3);
        assertThat(broker.recibidos).extracting(MensajeSaliente::eventType)
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
        assertThat(filasDelOutbox(id)).allSatisfy(fila -> {
            assertThat(fila.get("publicado_en")).isNotNull();
            assertThat(fila.get("ultimo_error")).isNull();
        });
    }

    @Test
    void siFallaUnoDelMedioLosAnterioresQuedanPublicadosYElRestoEspera() throws Exception {
        IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        String id = leer(crear("No puede ingresar nadie", "Producción caída desde las 9 de la mañana"))
                .get("id").asText();
        broker.fallarDesde = 2;

        assertThat(publicador.publicarPendientes()).isEqualTo(1);

        List<Map<String, Object>> filas = filasDelOutbox(id);
        assertThat(filas.get(0).get("publicado_en")).isNotNull();
        assertThat(filas.get(1).get("publicado_en")).isNull();
        assertThat(filas.get(2).get("publicado_en")).isNull();

        broker.fallarDesde = Integer.MAX_VALUE;
        assertThat(publicador.publicarPendientes()).isEqualTo(2);
        assertThat(broker.recibidos).extracting(MensajeSaliente::eventType)
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
    }

    /** Broker de mentira: guarda lo que recibe o falla como si estuviera caido. */
    private static final class BrokerFalso implements EnviadorDeMensajes {

        final List<MensajeSaliente> recibidos = new ArrayList<>();
        boolean caido;
        int fallarDesde = Integer.MAX_VALUE;
        private int llamadas;

        @Override
        public void enviar(MensajeSaliente mensaje) {
            llamadas++;
            if (caido || llamadas >= fallarDesde) {
                throw new PublicacionFallidaException("Connection refused (simulado)");
            }
            recibidos.add(mensaje);
        }
    }
}
