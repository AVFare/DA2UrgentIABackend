package com.urgentia.ticket.infrastructure.soap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El contrato SOAP vive en contracts/soap (la fuente de verdad), pero la imagen Docker se arma solo
 * con la carpeta de ticket-service, que no ve contracts/. Por eso hay una copia en
 * src/main/resources/soap. Este test falla si las dos dejan de ser iguales.
 *
 * <p>Si falla: copiar contracts/soap/tickets.wsdl y tickets-schema.xsd a ticket-service/src/main/resources/soap/
 * (o al reves, si el cambio se hizo en la copia: el contrato se cambia en contracts/, con aviso al grupo).
 */
class ContratoSoapTest {

    private static final Path CONTRATO = Path.of("..", "contracts", "soap");
    private static final Path COPIA = Path.of("src", "main", "resources", "soap");

    @ParameterizedTest
    @ValueSource(strings = {"tickets.wsdl", "tickets-schema.xsd"})
    void laCopiaDelServicioEsIgualAlContratoDeContracts(String archivo) throws IOException {
        Path original = CONTRATO.resolve(archivo);
        assertThat(original).as("contracts/soap/" + archivo + " (los tests corren desde ticket-service/)").exists();

        assertThat(leer(COPIA.resolve(archivo)))
                .as("src/main/resources/soap/%s debe ser igual a contracts/soap/%s", archivo, archivo)
                .isEqualTo(leer(original));
    }

    @Test
    void elContratoTieneLosDosArchivos() throws IOException {
        try (var archivos = Files.list(CONTRATO)) {
            assertThat(archivos.map(p -> p.getFileName().toString())).contains("tickets.wsdl", "tickets-schema.xsd");
        }
    }

    /** Se comparan sin saltos de linea de Windows: Git puede convertirlos y no cambia el contrato. */
    private static String leer(Path archivo) throws IOException {
        return Files.readString(archivo, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
