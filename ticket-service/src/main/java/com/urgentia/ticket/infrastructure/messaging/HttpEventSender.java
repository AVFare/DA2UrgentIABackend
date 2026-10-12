package com.urgentia.ticket.infrastructure.messaging;

import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Entrega los eventos por HTTP a cada URL de EVENT_SUBSCRIBERS (seccion 10.3).
 *
 * <ul>
 *   <li>Asincronico: no bloquea la respuesta al cliente.</li>
 *   <li>En paralelo entre suscriptores, pero en orden para cada uno: cada suscriptor tiene
 *       su propia cola de un hilo, asi TicketCreado llega antes que TicketClasificado.</li>
 *   <li>Reintentos: 3, esperando 1 s, 2 s y 4 s. Si fallan todos, log ERROR con el eventId
 *       (en la Defensa 1 se acepta la perdida; en la parte 2 lo resuelve el Transactional Outbox).</li>
 *   <li>Un 4xx no se reintenta: el suscriptor rechazo el sobre y repetirlo no lo arregla.</li>
 * </ul>
 */
@Component
public class HttpEventSender implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HttpEventSender.class);

    private final Map<String, ExecutorService> colas = new LinkedHashMap<>();
    private final List<Duration> esperas;
    private final RestClient http;

    public HttpEventSender(RestClient.Builder builder,
                           @Value("${eventos.suscriptores:}") String suscriptores,
                           @Value("${eventos.reintentos-ms:1000,2000,4000}") String reintentosMs,
                           @Value("${eventos.timeout-ms:5000}") long timeoutMs) {
        this.esperas = Arrays.stream(reintentosMs.split(","))
                .map(String::strip)
                .filter(valor -> !valor.isEmpty())
                .map(valor -> Duration.ofMillis(Long.parseLong(valor)))
                .toList();
        Duration timeout = Duration.ofMillis(timeoutMs);
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build());
        fabrica.setReadTimeout(timeout);
        this.http = builder.clone().requestFactory(fabrica).build();

        List<String> urls = Arrays.stream(suscriptores.split(","))
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .distinct()
                .toList();
        for (int i = 0; i < urls.size(); i++) {
            String nombreHilo = "eventos-" + (i + 1);
            colas.put(urls.get(i), Executors.newSingleThreadExecutor(tarea -> {
                Thread hilo = new Thread(tarea, nombreHilo);
                hilo.setDaemon(true);
                return hilo;
            }));
        }
        if (colas.isEmpty()) {
            log.warn("EVENT_SUBSCRIBERS esta vacio: los eventos no se van a enviar a nadie");
        } else {
            log.info("Suscriptores de eventos: {}", colas.keySet());
        }
    }

    /** Encola el evento para cada suscriptor y vuelve enseguida. */
    public void enviar(EventoEnvelope sobre) {
        colas.forEach((url, cola) -> cola.execute(() -> entregar(url, sobre)));
    }

    /** Suscriptores configurados (para los tests y el log de arranque). */
    public List<String> suscriptores() {
        return List.copyOf(colas.keySet());
    }

    private void entregar(String url, EventoEnvelope sobre) {
        MDC.put(CorrelationIdFilter.MDC_CLAVE, sobre.correlationId());
        try {
            for (int intento = 1; ; intento++) {
                try {
                    http.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(CorrelationIdFilter.HEADER, sobre.correlationId())
                            .body(sobre)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Evento {} {} entregado a {}", sobre.eventType(), sobre.eventId(), url);
                    return;
                } catch (HttpClientErrorException e) {
                    log.error("El suscriptor {} rechazo el evento {} {} con {}: no se reintenta",
                            url, sobre.eventType(), sobre.eventId(), e.getStatusCode());
                    return;
                } catch (RuntimeException e) {
                    if (intento > esperas.size()) {
                        log.error("No se pudo entregar el evento {} {} a {} despues de {} intentos: {}",
                                sobre.eventType(), sobre.eventId(), url, intento, e.getMessage());
                        return;
                    }
                    Duration espera = esperas.get(intento - 1);
                    log.warn("Fallo el envio del evento {} {} a {} (intento {}): {}. Reintento en {} ms",
                            sobre.eventType(), sobre.eventId(), url, intento, e.getMessage(), espera.toMillis());
                    if (!esperar(espera)) {
                        log.error("Se interrumpio el envio del evento {} {} a {}", sobre.eventType(),
                                sobre.eventId(), url);
                        return;
                    }
                }
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_CLAVE);
        }
    }

    private static boolean esperar(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Al apagar el servicio, deja terminar los envios en curso (hasta 10 s). */
    @Override
    public void destroy() throws InterruptedException {
        colas.values().forEach(ExecutorService::shutdown);
        for (ExecutorService cola : colas.values()) {
            if (!cola.awaitTermination(10, TimeUnit.SECONDS)) {
                cola.shutdownNow();
            }
        }
    }
}
