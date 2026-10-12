package com.urgentia.user.security;

import com.urgentia.user.model.Usuario;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite el JWT que valida el gateway (seccion 6 del contexto). HS256 explicito; la clave son
 * los bytes UTF-8 de JWT_SECRET tal cual (sin Base64), igual que hace el gateway para validarlo.
 * Falla al arrancar si el secreto tiene menos de 32 caracteres.
 */
@Component
public class JwtService {

    private static final String ISSUER = "urgentia-user-service";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.expirationMinutes = expirationMinutes;
    }

    public String generar(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol().name())
                .issuer(ISSUER)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(Duration.ofMinutes(expirationMinutes))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long expiracionEnSegundos() {
        return Duration.ofMinutes(expirationMinutes).toSeconds();
    }
}
