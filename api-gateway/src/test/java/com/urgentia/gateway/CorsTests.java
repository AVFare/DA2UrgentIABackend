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
class CorsTests {

    // Valor por defecto de CORS_ALLOWED_ORIGINS: el front corriendo con Vite.
    private static final String FRONT = "http://localhost:5173";
    private static final String OTRO_ORIGEN = "http://otro-sitio.example";

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
    void elPreflightDelFrontPasaSinToken() {
        cliente.options().uri("/api/tickets")
                .header(HttpHeaders.ORIGIN, FRONT)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        metodos -> assertThat(metodos).contains("POST"));
    }

    @Test
    void elPreflightDeOtroOrigenSeRechaza() {
        cliente.options().uri("/api/tickets")
                .header(HttpHeaders.ORIGIN, OTRO_ORIGEN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void un401LlevaLosHeadersDeCorsParaQueElFrontLoPuedaLeer() {
        cliente.get().uri("/api/tickets")
                .header(HttpHeaders.ORIGIN, FRONT)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        expuestos -> assertThat(expuestos).containsIgnoringCase("X-Correlation-Id"));
    }

    @Test
    void conTokenElPedidoDelFrontLlegaAlServicio() {
        String token = TokensDePrueba.valido(secreto, "usuario-1", "SOLICITANTE");

        cliente.get().uri("/api/tickets")
                .header(HttpHeaders.ORIGIN, FRONT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT)
                .expectBody(String.class).isEqualTo("/api/tickets");
    }

    @Test
    void sinOrigenNoAgregaHeadersDeCors() {
        cliente.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
