package com.urgentia.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTests {

    @Autowired
    private WebTestClient cliente;

    @Test
    void healthEsPublicoYRespondeConElFormatoComun() {
        cliente.get().uri("/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("api-gateway")
                .jsonPath("$.version").isEqualTo("0.1.0");
    }
}
