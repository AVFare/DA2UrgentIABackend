package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAuthFilterTests {

    private static final String OTRO_SECRETO = "otro-secreto-distinto-de-al-menos-32-caracteres";
    private static final ServicioFalso servicioFalso = new ServicioFalso();

    @Autowired
    private WebTestClient cliente;

    @Value("${jwt.secret}")
    private String secreto;

    @DynamicPropertySource
    static void apuntarAlServicioFalso(DynamicPropertyRegistry registry) {
        for (String servicio : List.of("user", "ticket", "classification", "notification", "reporting")) {
            registry.add("servicios." + servicio, servicioFalso::url);
        }
    }

    @AfterAll
    static void apagarServicioFalso() {
        servicioFalso.detener();
    }

    @Test
    void sinTokenDevuelve401ConElFormatoComun() {
        cliente.get().uri("/api/tickets").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO")
                .jsonPath("$.mensaje").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.path").isEqualTo("/api/tickets")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void tokenFirmadoConOtraClaveDevuelve401() {
        String token = TokensDePrueba.valido(OTRO_SECRETO, "usuario-1", "AGENTE");

        cliente.get().uri("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void tokenVencidoDevuelve401() {
        String token = TokensDePrueba.vencido(secreto, "usuario-1", "AGENTE");

        cliente.get().uri("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void tokenValidoPasaYPropagaUsuarioYRol() {
        String token = TokensDePrueba.valido(secreto, "usuario-1", "AGENTE");

        cliente.get().uri("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "intruso")
                .header("X-User-Rol", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(servicioFalso.headerRecibido("X-User-Id")).isEqualTo("usuario-1");
        assertThat(servicioFalso.headerRecibido("X-User-Rol")).isEqualTo("AGENTE");
    }

    @Test
    void elLoginEsPublicoYNoDejaPasarHeadersDeUsuarioFalsos() {
        cliente.post().uri("/api/auth/login")
                .header("X-User-Id", "intruso")
                .header("X-User-Rol", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(servicioFalso.headerRecibido("X-User-Id")).isNull();
        assertThat(servicioFalso.headerRecibido("X-User-Rol")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs/tickets", "/docs/algo"})
    void lasRutasPublicasNoExigenToken(String ruta) {
        cliente.get().uri(ruta).exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }
}
