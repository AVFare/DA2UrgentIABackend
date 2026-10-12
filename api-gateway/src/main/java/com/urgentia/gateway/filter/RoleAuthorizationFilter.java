package com.urgentia.gateway.filter;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

import com.urgentia.gateway.error.ErrorResponseWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Tercer eslabon de la cadena: controla que el rol del usuario alcance para
 * la operacion pedida, segun la tabla de la seccion 6.1 del contexto.
 * Lo que no figura en la tabla se rechaza.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RoleAuthorizationFilter implements WebFilter {

    private static final Set<String> TODOS = Set.of("SOLICITANTE", "AGENTE", "ADMIN");
    private static final Set<String> AGENTE_O_ADMIN = Set.of("AGENTE", "ADMIN");
    private static final Set<String> SOLO_ADMIN = Set.of("ADMIN");

    private static final List<Regla> REGLAS = List.of(
            regla(Set.of(POST), SOLO_ADMIN, "/api/usuarios"),
            regla(Set.of(GET), AGENTE_O_ADMIN, "/api/usuarios", "/api/usuarios/**"),
            regla(Set.of(GET, POST), TODOS, "/api/tickets"),
            regla(Set.of(GET), TODOS, "/api/tickets/{id}"),
            regla(Set.of(PATCH), AGENTE_O_ADMIN, "/api/tickets/{id}/asignacion", "/api/tickets/{id}/estado"),
            regla(Set.of(POST), AGENTE_O_ADMIN, "/api/tickets/{id}/reclasificacion"),
            regla(Set.of(GET, POST), SOLO_ADMIN, "/api/clasificaciones", "/api/clasificaciones/**"),
            regla(Set.of(GET), AGENTE_O_ADMIN, "/api/notificaciones", "/api/notificaciones/**"),
            regla(Set.of(GET), AGENTE_O_ADMIN, "/api/reportes", "/api/reportes/**"));

    private final ErrorResponseWriter errores;

    public RoleAuthorizationFilter(ErrorResponseWriter errores) {
        this.errores = errores;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // JwtAuthFilter solo pone este header cuando valido un token.
        // Si no esta, la ruta es publica y no hay rol que controlar.
        String rol = request.getHeaders().getFirst(JwtAuthFilter.HEADER_USER_ROL);
        if (rol == null) {
            return chain.filter(exchange);
        }

        PathContainer path = request.getPath().pathWithinApplication();
        boolean permitido = REGLAS.stream()
                .filter(regla -> regla.aplica(request.getMethod(), path))
                .findFirst()
                .map(regla -> regla.roles().contains(rol))
                .orElse(false);

        if (!permitido) {
            return errores.escribir(exchange, HttpStatus.FORBIDDEN, "SIN_PERMISO",
                    "El rol no alcanza para esta operacion");
        }
        return chain.filter(exchange);
    }

    private static Regla regla(Set<HttpMethod> metodos, Set<String> roles, String... rutas) {
        List<PathPattern> patrones = Stream.of(rutas)
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
        return new Regla(metodos, patrones, roles);
    }

    private record Regla(Set<HttpMethod> metodos, List<PathPattern> rutas, Set<String> roles) {

        boolean aplica(HttpMethod metodo, PathContainer path) {
            return metodos.contains(metodo) && rutas.stream().anyMatch(ruta -> ruta.matches(path));
        }
    }
}
