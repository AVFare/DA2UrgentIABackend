package com.urgentia.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.urgentia.user.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** El seed es idempotente: correrlo de nuevo no duplica usuarios (seccion 6.2 del contexto). */
@SpringBootTest
@Transactional
class DatosSemillaTest {

    @Autowired
    private DatosSemilla datosSemilla;

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void correrloDeNuevoNoDuplicaLosUsuarios() {
        assertThat(usuarios.count()).isEqualTo(5);

        datosSemilla.run(new DefaultApplicationArguments());

        assertThat(usuarios.count()).isEqualTo(5);
    }
}
