package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdFilterTests {

    private static final String HEADER = "X-Correlation-Id";
    private static final ServicioFalso servicioFalso = new ServicioFalso(true);

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
    void generaUnCorrelationIdSiNoViene() {
        String devuelto = cliente.get().uri("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                .expectStatus().isOk()
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(HEADER);

        assertThat(devuelto).isNotBlank();
        assertThat(servicioFalso.headerRecibido(HEADER)).isEqualTo(devuelto);
    }

    @Test
    void respetaElCorrelationIdQueViene() {
        cliente.get().uri("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HEADER, "abc-123").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, "abc-123");

        assertThat(servicioFalso.headerRecibido(HEADER)).isEqualTo("abc-123");
    }

    @Test
    void elLoginDevuelveUnaSolaCorrelacionAunqueElServicioTambienLaDevuelva() {
        cliente.post().uri("/api/auth/login")
                .header(HEADER, "cid-login")
                .bodyValue(java.util.Map.of("email", "usuario@urgentia.local", "password", "clave"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, "cid-login");

        assertThat(servicioFalso.headerRecibido(HEADER)).isEqualTo("cid-login");
    }

    @Test
    void tambienLoAgregaCuandoNoHayRuta() {
        cliente.get().uri("/docs/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(HEADER);
    }

    @Test
    void tambienLoAgregaEnLasRespuestasDeError() {
        cliente.get().uri("/api/tickets").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(HEADER);
    }

    private String bearer() {
        return "Bearer " + TokensDePrueba.valido(secreto, "usuario-1", "AGENTE");
    }
}
