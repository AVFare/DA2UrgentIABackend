package com.urgentia.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Login: casos correctos e incorrectos, y el contenido del JWT (seccion 6 del contexto). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    private static final String SOLICITANTE_EMAIL = "solicitante@urgentia.local";
    private static final String SOLICITANTE_PASSWORD = "Usuario123!";
    private static final String SOLICITANTE_ID = "b1c2d3e4-0000-4000-8000-000000000004";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void loginCorrectoDevuelveElTokenYElUsuario() throws Exception {
        String cuerpo = login(SOLICITANTE_EMAIL, SOLICITANTE_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.usuario.id").value(SOLICITANTE_ID))
                .andExpect(jsonPath("$.usuario.email").value(SOLICITANTE_EMAIL))
                .andExpect(jsonPath("$.usuario.rol").value("SOLICITANTE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode respuesta = json.readTree(cuerpo);
        Claims claims = decodificar(respuesta.get("accessToken").asText());
        assertThat(claims.getSubject()).isEqualTo(SOLICITANTE_ID);
        assertThat(claims.get("email", String.class)).isEqualTo(SOLICITANTE_EMAIL);
        assertThat(claims.get("rol", String.class)).isEqualTo("SOLICITANTE");
        assertThat(claims.getIssuer()).isEqualTo("urgentia-user-service");
        long segundos = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(segundos).isEqualTo(3600);
    }

    @Test
    void contrasenaIncorrectaDa401CredencialesInvalidas() throws Exception {
        login(SOLICITANTE_EMAIL, "otra-cosa")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void emailInexistenteDa401CredencialesInvalidas() throws Exception {
        login("nadie@urgentia.local", SOLICITANTE_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private Claims decodificar(String token) {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Jwts.parser()
                .verifyWith(new SecretKeySpec(bytes, "HmacSHA256"))
                .build().parseSignedClaims(token).getPayload();
    }
}
