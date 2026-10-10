package com.urgentia.gateway.filter;

import com.urgentia.gateway.config.LogJsonFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Primer eslabon de la cadena de filtros: asegura que todo pedido tenga un
 * X-Correlation-Id. Si no viene, lo genera. Lo reenvia al servicio destino
 * y lo devuelve en la respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATRIBUTO = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (recibido == null || recibido.isBlank())
                ? UUID.randomUUID().toString()
                : recibido;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        // El servicio tambien devuelve este header y el proxy puede agregarlo
        // a la respuesta. Al confirmarla, conservar un unico valor del pedido.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });
        exchange.getAttributes().put(ATRIBUTO, correlationId);

        // El correlationId va como dato del log; LogJsonFormatter lo escribe como campo propio.
        log.atInfo()
                .addKeyValue(LogJsonFormatter.CORRELATION_ID, correlationId)
                .log("{} {}", request.getMethod(), request.getURI().getPath());

        return chain.filter(exchange.mutate().request(request).build());
    }
}
