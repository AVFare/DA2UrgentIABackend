package com.urgentia.ticket.infrastructure.messaging.outbox;

import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lleva los mensajes del Outbox a RabbitMQ (ADR-12). Reemplaza al HttpEventSender de la Entrega 1.
 *
 * <ul>
 *   <li>Cada medio segundo lee las filas pendientes, de la mas vieja a la mas nueva.</li>
 *   <li>Publica una por vez y espera la confirmacion del broker; recien ahi la marca como publicada.</li>
 *   <li>Si una falla (broker caido, sin confirmacion, sin cola de destino), registra el error, corta la
 *       vuelta para no desordenar los mensajes y espera antes de reintentar (1 s, 2 s, 4 s y despues cada 8 s).
 *       Nada se pierde: la fila sigue pendiente hasta que el broker la confirme.</li>
 *   <li>Entrega "al menos una vez": si el broker confirma pero se cae la base antes de marcar la fila,
 *       el mensaje se publica de nuevo. Los consumidores son idempotentes por eventId.</li>
 * </ul>
 *
 * <p>Pensado para una sola instancia de ticket-service, como en el Compose. Con varias habria que
 * tomar las filas con SELECT ... FOR UPDATE SKIP LOCKED.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publicador", name = "habilitado", havingValue = "true", matchIfMissing = true)
public class PublicadorDelOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDelOutbox.class);
    private static final Duration ESPERA_INICIAL = Duration.ofSeconds(1);
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(8);

    private final SpringDataOutboxRepository repositorio;
    private final EnviadorDeMensajes enviador;
    private final Clock reloj;
    private final int lote;

    private Duration espera = Duration.ZERO;
    private Instant proximoIntento = Instant.MIN;

    public PublicadorDelOutbox(SpringDataOutboxRepository repositorio, EnviadorDeMensajes enviador, Clock reloj,
                               @Value("${outbox.publicador.lote:100}") int lote) {
        this.repositorio = repositorio;
        this.enviador = enviador;
        this.reloj = reloj;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${outbox.publicador.intervalo-ms:500}",
            initialDelayString = "${outbox.publicador.intervalo-ms:500}")
    public void publicarProgramado() {
        if (Instant.now(reloj).isBefore(proximoIntento)) {
            return;
        }
        try {
            publicarPendientes();
        } catch (RuntimeException e) {
            // Por ejemplo, la base caida al leer las pendientes: se reintenta en la proxima vuelta.
            log.error("No se pudo leer el outbox: {}", e.getMessage());
            esperarAntesDeReintentar();
        }
    }

    /**
     * Publica las pendientes en orden hasta terminar o hasta la primera que falle.
     *
     * @return cuantas publico
     */
    public synchronized int publicarPendientes() {
        List<OutboxJpaEntity> pendientes = repositorio.findByPublicadoEnIsNullOrderByIdAsc(Limit.of(lote));
        int publicadas = 0;
        for (OutboxJpaEntity fila : pendientes) {
            MDC.put(CorrelationIdFilter.MDC_CLAVE, fila.getCorrelationId());
            try {
                enviador.enviar(aMensaje(fila));
                repositorio.marcarPublicado(fila.getId(), Instant.now(reloj));
                publicadas++;
                log.info("Publicado {} {} en {} (routing key {})", fila.getEventType(), fila.getEventId(),
                        fila.getExchange(), fila.getRoutingKey());
            } catch (RuntimeException e) {
                String motivo = recortar(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                repositorio.registrarFallo(fila.getId(), motivo);
                log.warn("No se pudo publicar {} {} (intento {}): {}. Queda pendiente en el outbox",
                        fila.getEventType(), fila.getEventId(), fila.getIntentos() + 1, motivo);
                esperarAntesDeReintentar();
                return publicadas;
            } finally {
                MDC.remove(CorrelationIdFilter.MDC_CLAVE);
            }
        }
        if (publicadas > 0 && espera.compareTo(Duration.ZERO) > 0) {
            log.info("El broker volvio a confirmar: se publicaron {} mensajes pendientes", publicadas);
        }
        espera = Duration.ZERO;
        proximoIntento = Instant.MIN;
        return publicadas;
    }

    private void esperarAntesDeReintentar() {
        espera = espera.isZero() ? ESPERA_INICIAL : min(espera.multipliedBy(2), ESPERA_MAXIMA);
        proximoIntento = Instant.now(reloj).plus(espera);
    }

    private static MensajeSaliente aMensaje(OutboxJpaEntity fila) {
        return new MensajeSaliente(fila.getEventId(), fila.getEventType(), fila.getExchange(), fila.getRoutingKey(),
                fila.getCorrelationId(), fila.getTicketId(), fila.getCuerpo(), fila.getOcurridoEn());
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String recortar(String texto) {
        return texto.length() <= OutboxJpaEntity.ERROR_MAX ? texto : texto.substring(0, OutboxJpaEntity.ERROR_MAX);
    }
}
