package com.urgentia.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Alta, listado y consulta de usuarios (seccion 9.1 del contexto). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void altaOkDevuelveElUsuarioSinLaContrasena() throws Exception {
        String cuerpo = crear("Nueva Agente", "Nueva.Agente@Urgentia.Local", "Segura123!", "AGENTE")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/usuarios/")))
                .andExpect(jsonPath("$.email").value("nueva.agente@urgentia.local"))
                .andExpect(jsonPath("$.rol").value("AGENTE"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.fechaAlta").value(notNullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo).doesNotContain("password").doesNotContain("passwordHash");
    }

    @Test
    void altaConEmailYaUsadoDa409EmailDuplicado() throws Exception {
        crear("Otro Admin", "ADMIN@urgentia.local", "Segura123!", "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_DUPLICADO"));
    }

    @Test
    void altaConDatosInvalidosDa400ConDetalles() throws Exception {
        crear("", "no-es-un-email", "corta", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles").isArray());
    }

    @Test
    void listarPorRolDevuelveSoloLosAgentes() throws Exception {
        mvc.perform(get("/api/usuarios").param("rol", "AGENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void consultarPorIdInexistenteDa404NoEncontrado() throws Exception {
        mvc.perform(get("/api/usuarios/b1c2d3e4-0000-4000-8000-000000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    private ResultActions crear(String nombre, String email, String password, String rol) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("email", email);
        cuerpo.put("password", password);
        cuerpo.put("rol", rol);
        return mvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }
}
