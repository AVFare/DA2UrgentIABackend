package com.urgentia.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasTests {

    // Servicio falso: responde 200 y devuelve la ruta que le llego.
    private static final HttpServer servicioFalso = iniciarServicioFalso();

    @Autowired
    private WebTestClient cliente;

    private static HttpServer iniciarServicioFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", exchange -> {
                byte[] cuerpo = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = exchange.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void apuntarAlServicioFalso(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + servicioFalso.getAddress().getPort();
        for (String servicio : List.of("user", "ticket", "classification", "notification", "reporting")) {
            registry.add("servicios." + servicio, () -> url);
        }
    }

    @AfterAll
    static void apagarServicioFalso() {
        servicioFalso.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/login",
            "/api/usuarios",
            "/api/usuarios/123",
            "/api/tickets",
            "/api/tickets/123/estado",
            "/api/clasificaciones",
            "/api/notificaciones/123",
            "/api/reportes/resumen"
    })
    void ruteaLasRutasDeLaTablaDeContratos(String ruta) {
        cliente.get().uri(ruta).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(ruta);
    }

    @Test
    void noRuteaEventos() {
        cliente.post().uri("/api/eventos").exchange()
                .expectStatus().isNotFound();
    }
}