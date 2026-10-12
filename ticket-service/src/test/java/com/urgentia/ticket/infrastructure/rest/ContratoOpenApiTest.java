package com.urgentia.ticket.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract-first: lo que expone el servicio (springdoc) tiene que coincidir con
 * contracts/ticket-service.yaml (mismas rutas, metodos y codigos de respuesta).
 */
class ContratoOpenApiTest extends IntegracionBase {

    private static final Path CONTRATO = Path.of("../contracts/ticket-service.yaml");
    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    void lasRutasYLosCodigosCoincidenConElContrato() throws Exception {
        JsonNode generado = leer(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));

        Set<String> delServicio = new TreeSet<>();
        generado.get("paths").properties().forEach(ruta -> ruta.getValue().properties().forEach(metodo -> {
            if (METODOS.contains(metodo.getKey())) {
                metodo.getValue().get("responses").fieldNames()
                        .forEachRemaining(codigo -> delServicio.add(metodo.getKey().toUpperCase() + " "
                                + ruta.getKey() + " " + codigo));
            }
        }));

        assertThat(delServicio).containsExactlyInAnyOrderElementsOf(delContrato());
    }

    @Test
    void declaraElEsquemaBearerParaElBotonAuthorize() throws Exception {
        JsonNode generado = leer(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));

        assertThat(generado.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
        assertThat(generado.at("/info/title").asText()).isEqualTo("UrgentIA - ticket-service");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> delContrato() throws Exception {
        Map<String, Object> contrato;
        try (InputStream entrada = Files.newInputStream(CONTRATO)) {
            contrato = new Yaml().load(entrada);
        }
        Set<String> resultado = new TreeSet<>();
        Map<String, Object> rutas = (Map<String, Object>) contrato.get("paths");
        rutas.forEach((ruta, operaciones) -> ((Map<String, Object>) operaciones).forEach((metodo, operacion) -> {
            if (METODOS.contains(metodo)) {
                Map<String, Object> respuestas = (Map<String, Object>) ((Map<String, Object>) operacion).get("responses");
                respuestas.keySet().forEach(codigo -> resultado.add(metodo.toUpperCase() + " " + ruta + " " + codigo));
            }
        }));
        return resultado;
    }
}
