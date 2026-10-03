package com.urgentia.gateway;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleAuthorizationFilterTests {

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

    @ParameterizedTest(name = "{0} {1} como {2} -> {3}")
    @CsvSource({
            "POST,   /api/usuarios,                    ADMIN,       200",
            "POST,   /api/usuarios,                    AGENTE,      403",
            "GET,    /api/usuarios,                    AGENTE,      200",
            "GET,    /api/usuarios/123,                SOLICITANTE, 403",
            "POST,   /api/tickets,                     SOLICITANTE, 200",
            "GET,    /api/tickets/123,                 SOLICITANTE, 200",
            "PATCH,  /api/tickets/123/asignacion,      SOLICITANTE, 403",
            "PATCH,  /api/tickets/123/estado,          AGENTE,      200",
            "POST,   /api/tickets/123/reclasificacion, ADMIN,       200",
            "POST,   /api/clasificaciones,             AGENTE,      403",
            "GET,    /api/clasificaciones,             ADMIN,       200",
            "GET,    /api/notificaciones/123,          SOLICITANTE, 403",
            "GET,    /api/reportes/resumen,            AGENTE,      200",
            "DELETE, /api/tickets/123,                 ADMIN,       403",
            "POST,   /api/eventos,                     ADMIN,       403"
    })
    void aplicaLaTablaDeRoles(String metodo, String ruta, String rol, int esperado) {
        cliente.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(rol))
                .exchange()
                .expectStatus().isEqualTo(esperado);
    }

    @Test
    void sinPermisoDevuelve403ConElFormatoComun() {
        cliente.post().uri("/api/usuarios")
                .header(HttpHeaders.AUTHORIZATION, bearer("AGENTE"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("SIN_PERMISO")
                .jsonPath("$.mensaje").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.path").isEqualTo("/api/usuarios")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    private String bearer(String rol) {
        return "Bearer " + TokensDePrueba.valido(secreto, "usuario-1", rol);
    }
}
