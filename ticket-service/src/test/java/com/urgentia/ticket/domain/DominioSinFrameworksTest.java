package com.urgentia.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Regla de arquitectura (CONTEXTO, seccion 0, punto 4): el paquete domain es Java puro.
 * Ademas, la capa de aplicacion tampoco depende de Spring: la transaccion y el resto
 * de los detalles tecnicos los pone la infraestructura.
 */
class DominioSinFrameworksTest {

    private static final Path FUENTES = Path.of("src/main/java/com/urgentia/ticket");
    private static final List<String> PROHIBIDOS_EN_DOMINIO =
            List.of("import org.springframework", "import jakarta.", "import javax.persistence", "import com.fasterxml",
                    "import org.hibernate", "import com.urgentia.ticket.application", "import com.urgentia.ticket.infrastructure");
    private static final List<String> PROHIBIDOS_EN_APLICACION =
            List.of("import org.springframework", "import jakarta.", "import com.fasterxml", "import org.hibernate",
                    "import com.urgentia.ticket.infrastructure");

    @Test
    void elDominioNoImportaFrameworksNiOtrasCapas() {
        assertThat(importsProhibidos(FUENTES.resolve("domain"), PROHIBIDOS_EN_DOMINIO)).isEmpty();
    }

    @Test
    void laAplicacionNoImportaFrameworksNiInfraestructura() {
        assertThat(importsProhibidos(FUENTES.resolve("application"), PROHIBIDOS_EN_APLICACION)).isEmpty();
    }

    private static List<String> importsProhibidos(Path carpeta, List<String> prohibidos) {
        Assumptions.assumeTrue(Files.isDirectory(carpeta), "Todavia no existe " + carpeta);
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(archivo -> archivo.toString().endsWith(".java"))
                    .flatMap(archivo -> lineas(archivo).stream()
                            .filter(linea -> prohibidos.stream().anyMatch(linea.strip()::startsWith))
                            .map(linea -> archivo.getFileName() + ": " + linea.strip()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lineas(Path archivo) {
        try {
            return Files.readAllLines(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
