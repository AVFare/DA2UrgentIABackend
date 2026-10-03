package com.urgentia.gateway;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Fabrica tokens para los tests, firmados como los firmaria user-service. */
final class TokensDePrueba {

    private TokensDePrueba() {
    }

    static String valido(String secreto, String userId, String rol) {
        return crear(secreto, userId, rol, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    static String vencido(String secreto, String userId, String rol) {
        return crear(secreto, userId, rol, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private static String crear(String secreto, String userId, String rol, Instant vencimiento) {
        SecretKey clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .subject(userId)
                .claim("rol", rol)
                .expiration(Date.from(vencimiento))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }
}
