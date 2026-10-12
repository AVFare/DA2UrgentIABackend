package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class LogsEnJsonTests {

    @Autowired
    private WebTestClient cliente;

    @Test
    void cadaPedidoDejaUnaLineaJsonConElCorrelationId(CapturedOutput salida) {
        cliente.get().uri("/health").header("X-Correlation-Id", "abc-123").exchange()
                .expectStatus().isOk();

        assertThat(salida.getOut())
                .contains("\"service\":\"api-gateway\"")
                .contains("\"correlationId\":\"abc-123\"");
    }
}
