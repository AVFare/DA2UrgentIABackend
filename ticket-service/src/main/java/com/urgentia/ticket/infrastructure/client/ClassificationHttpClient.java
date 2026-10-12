package com.urgentia.ticket.infrastructure.client;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter / Anti-Corruption Layer hacia classification-service (seccion 9.2).
 * Hace POST /api/clasificaciones con timeout (CLASSIFICATION_TIMEOUT_MS, 7 s por defecto)
 * y traduce la respuesta a la Clasificacion del dominio. Ante cualquier error, timeout,
 * 4xx/5xx o respuesta que no cumple el contrato lanza ClasificacionNoDisponibleException.
 */
@Component
public class ClassificationHttpClient implements ClasificadorPort {

    static final String RUTA = "/api/clasificaciones";
    private static final Logger log = LoggerFactory.getLogger(ClassificationHttpClient.class);

    private final RestClient http;
    private final ClasificacionTraductor traductor;

    public ClassificationHttpClient(RestClient.Builder builder,
                                    @Value("${clasificacion.url}") String url,
                                    @Value("${clasificacion.timeout-ms}") long timeoutMs,
                                    Clock reloj) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout.compareTo(Duration.ofSeconds(2)) < 0 ? timeout : Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(timeout);
        this.http = builder.clone().baseUrl(url).requestFactory(fabrica).build();
        this.traductor = new ClasificacionTraductor(reloj);
    }

    @Override
    public Clasificacion clasificar(TicketId ticketId, String titulo, String descripcion) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CLAVE);
        ClasificacionRespuesta respuesta;
        try {
            respuesta = http.post()
                    .uri(RUTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (correlationId != null) {
                            headers.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    })
                    .body(new ClasificacionPedido(ticketId.valor(), titulo, descripcion))
                    .retrieve()
                    .body(ClasificacionRespuesta.class);
        } catch (RuntimeException e) {
            log.warn("La IA no pudo clasificar el ticket {}: {}", ticketId, e.getMessage());
            throw new ClasificacionNoDisponibleException("classification-service no respondio bien: "
                    + e.getMessage(), e);
        }
        try {
            Clasificacion clasificacion = traductor.traducir(respuesta, ticketId);
            log.info("Ticket {} clasificado por la IA ({}): {} / {} / {}", ticketId, clasificacion.proveedor(),
                    clasificacion.categoria(), clasificacion.urgencia(), clasificacion.impacto());
            return clasificacion;
        } catch (RespuestaInvalidaException e) {
            log.warn("La IA devolvio una respuesta que no cumple el contrato para el ticket {}: {}",
                    ticketId, e.getMessage());
            throw new ClasificacionNoDisponibleException("Respuesta invalida de la IA: " + e.getMessage(), e);
        }
    }
}
