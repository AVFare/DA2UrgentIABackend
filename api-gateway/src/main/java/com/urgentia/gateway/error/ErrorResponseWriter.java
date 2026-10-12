package com.urgentia.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.gateway.filter.CorrelationIdFilter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Corta el pedido y responde con el formato comun de error. */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> escribir(ServerWebExchange exchange, HttpStatus status, String codigo, String mensaje) {
        String correlationId = exchange.getAttribute(CorrelationIdFilter.ATRIBUTO);
        ErrorResponse cuerpo = new ErrorResponse(
                codigo,
                mensaje,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                exchange.getRequest().getURI().getPath(),
                correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(cuerpo);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
