package com.urgentia.gateway.filter;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Segundo eslabon de la cadena: deja que el front, que corre en otro origen,
 * llame al gateway desde el navegador. Los origenes salen de CORS_ALLOWED_ORIGINS.
 *
 * Va antes que JwtAuthFilter porque el navegador manda el preflight (OPTIONS) sin
 * token: si llegara a JwtAuthFilter, lo cortaria con 401. Este filtro lo responde
 * el mismo. En los pedidos reales agrega los headers antes de seguir, asi que
 * tambien los llevan los 401 y 403 del gateway y el front los puede leer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CorsFilter implements WebFilter {

    private final CorsWebFilter delegado;

    public CorsFilter(@Value("${cors.origenes-permitidos}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CorrelationIdFilter.HEADER));
        config.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        this.delegado = new CorsWebFilter(fuente);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return delegado.filter(exchange, chain);
    }
}
