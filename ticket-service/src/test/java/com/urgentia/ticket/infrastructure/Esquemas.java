package com.urgentia.ticket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Valida los mensajes que publica ticket-service contra los JSON Schema de contracts/events/. */
public final class Esquemas {

    private static final JsonSchema EVENTOS = cargar("../contracts/events/ticket-events.schema.json");
    private static final JsonSchema CLASIFICACION = cargar("../contracts/events/clasificacion-events.schema.json");

    private Esquemas() {
    }

    /** El sobre cumple contracts/events/ticket-events.schema.json (los 6 eventos de dominio). */
    public static void cumpleElDeEventos(JsonNode sobre) {
        cumple(EVENTOS, sobre);
    }

    /** El sobre cumple contracts/events/clasificacion-events.schema.json. */
    public static void cumpleElDeClasificacion(JsonNode sobre) {
        cumple(CLASIFICACION, sobre);
    }

    private static void cumple(JsonSchema esquema, JsonNode sobre) {
        Set<ValidationMessage> errores = esquema.validate(sobre);
        assertThat(errores).as("errores de esquema en " + sobre).isEmpty();
    }

    private static JsonSchema cargar(String ruta) {
        try (InputStream entrada = Files.newInputStream(Path.of(ruta))) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
