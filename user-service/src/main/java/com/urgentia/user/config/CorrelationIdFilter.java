package com.urgentia.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma el X-Correlation-Id que pone el gateway (o genera uno si no viene), lo deja en el
 * MDC para que salga en cada log y lo devuelve en la respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_CLAVE = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        String correlationId = (recibido == null || recibido.isBlank()) ? UUID.randomUUID().toString() : recibido;
        MDC.put(MDC_CLAVE, correlationId);
        response.setHeader(HEADER, correlationId);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!"/health".equals(request.getRequestURI())) {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - inicio) / 1_000_000);
            }
            MDC.remove(MDC_CLAVE);
        }
    }
}
