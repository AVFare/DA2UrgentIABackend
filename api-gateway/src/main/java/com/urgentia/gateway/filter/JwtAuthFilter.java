package com.urgentia.gateway.filter;

import com.urgentia.gateway.error.ErrorResponseWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
 * Segundo eslabon de la cadena: exige un JWT valido en todo pedido que no sea
 * publico. Si el token es valido, le pasa al servicio destino quien es el
 * usuario (X-User-Id) y que rol tiene (X-User-Rol).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter implements WebFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROL = "X-User-Rol";

    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String CODIGO = "NO_AUTENTICADO";

    // Rutas publicas de la seccion 6.1 del contexto. Todo lo demas exige token.
    private static final PathPattern LOGIN = PathPatternParser.defaultInstance.parse("/api/auth/login");
    private static final List<PathPattern> PUBLICAS_GET = Stream.of(
                    "/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/docs/**")
            .map(PathPatternParser.defaultInstance::parse)
            .toList();

    private final JwtParser parser;
    private final ErrorResponseWriter errores;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret, ErrorResponseWriter errores) {
        // La clave son los bytes UTF-8 del secreto, tal cual (sin decodificar Base64).
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.parser = Jwts.parser()
                .verifyWith(new SecretKeySpec(bytes, "HmacSHA256"))
                .build();
        this.errores = errores;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (esPublica(request)) {
            return chain.filter(conUsuario(exchange, null, null));
        }

        String autorizacion = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (autorizacion == null || !autorizacion.startsWith(PREFIJO_BEARER)) {
            return errores.escribir(exchange, HttpStatus.UNAUTHORIZED, CODIGO, "Falta el token de acceso");
        }

        Claims claims;
        try {
            String token = autorizacion.substring(PREFIJO_BEARER.length()).trim();
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return errores.escribir(exchange, HttpStatus.UNAUTHORIZED, CODIGO, "El token es invalido o esta vencido");
        }

        String userId = claims.getSubject();
        String rol = claims.get("rol", String.class);
        if (userId == null || rol == null) {
            return errores.escribir(exchange, HttpStatus.UNAUTHORIZED, CODIGO, "El token no identifica al usuario");
        }

        return chain.filter(conUsuario(exchange, userId, rol));
    }

    private boolean esPublica(ServerHttpRequest request) {
        PathContainer path = request.getPath().pathWithinApplication();
        if (HttpMethod.POST.equals(request.getMethod())) {
            return LOGIN.matches(path);
        }
        if (HttpMethod.GET.equals(request.getMethod())) {
            return PUBLICAS_GET.stream().anyMatch(patron -> patron.matches(path));
        }
        return false;
    }

    /** Borra los X-User-* que mande el cliente y, si hay usuario, pone los del token. */
    private ServerWebExchange conUsuario(ServerWebExchange exchange, String userId, String rol) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROL);
                    if (userId != null) {
                        headers.set(HEADER_USER_ID, userId);
                        headers.set(HEADER_USER_ROL, rol);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }
}
