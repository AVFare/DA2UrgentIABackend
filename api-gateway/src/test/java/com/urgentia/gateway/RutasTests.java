package com.urgentia.gateway;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasTests {

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/usuarios",
            "/api/usuarios/123",
            "/api/tickets",
            "/api/tickets/123/estado",
            "/api/clasificaciones",
            "/api/notificaciones/123",
            "/api/reportes/resumen"
    })
    void ruteaLasRutasDeLaTablaDeContratos(String ruta) {
        cliente.get().uri(ruta).header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(ruta);
    }

    @Test
    void ruteaElLogin() {
        cliente.post().uri("/api/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("/api/auth/login");
    }

    @Test
    void noRuteaEventos() {
        cliente.post().uri("/api/eventos").header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                .expectStatus().isNotFound();
    }

    private String bearer() {
        return "Bearer " + TokensDePrueba.valido(secreto, "usuario-1", "ADMIN");
    }
}
