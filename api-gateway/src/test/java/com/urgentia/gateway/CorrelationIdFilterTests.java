package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdFilterTests {

    private static final String HEADER = "X-Correlation-Id";
    private static final ServicioFalso servicioFalso = new ServicioFalso();

    @Autowired
    private WebTestClient cliente;

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
        String devuelto = cliente.get().uri("/api/tickets").exchange()
                .expectStatus().isOk()
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(HEADER);

        assertThat(devuelto).isNotBlank();
        assertThat(servicioFalso.headerRecibido(HEADER)).isEqualTo(devuelto);
    }

    @Test
    void respetaElCorrelationIdQueViene() {
        cliente.get().uri("/api/tickets").header(HEADER, "abc-123").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, "abc-123");

        assertThat(servicioFalso.headerRecibido(HEADER)).isEqualTo("abc-123");
    }

    @Test
    void tambienLoAgregaCuandoNoHayRuta() {
        cliente.get().uri("/api/eventos").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(HEADER);
    }
}