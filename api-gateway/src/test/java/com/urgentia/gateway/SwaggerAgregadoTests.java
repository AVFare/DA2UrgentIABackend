package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerAgregadoTests {

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

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/v3/api-docs/user-service,           /v3/api-docs",
            "/v3/api-docs/ticket-service,         /v3/api-docs",
            "/v3/api-docs/classification-service, /openapi.json",
            "/v3/api-docs/notification-service,   /api-docs-json",
            "/v3/api-docs/reporting-service,      /api-docs-json"
    })
    void pideLaDocumentacionDeCadaServicioEnSuRutaYSinToken(String rutaEnElGateway, String rutaEnElServicio) {
        cliente.get().uri(rutaEnElGateway).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(rutaEnElServicio);
    }

    @Test
    void publicaLaDocumentacionDelPropioGateway() {
        cliente.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("UrgentIA - API Gateway")
                .jsonPath("$.info.version").isEqualTo("0.1.0")
                .jsonPath("$.paths['/health']").exists();
    }

    @Test
    void elSelectorListaLosSeisServicios() {
        cliente.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.urls.length()").isEqualTo(6);
    }

    @Test
    void laPaginaDeSwaggerEsPublica() {
        cliente.get().uri("/swagger-ui.html").exchange()
                .expectStatus().value(status -> assertThat(status).isBetween(200, 399));
    }
}
