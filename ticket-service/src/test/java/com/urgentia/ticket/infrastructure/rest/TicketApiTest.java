package com.urgentia.ticket.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso.Pedido;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Prueba la API completa (controller + casos de uso + dominio + JPA) contra una IA de mentira. */
class TicketApiTest extends IntegracionBase {

    private static final String TITULO = "No puede ingresar nadie";
    private static final String DESCRIPCION = "Producción caída, todos los usuarios bloqueados en el login desde las 9";

    @Nested
    class CrearTicket {

        @Test
        void elCasoDeLaDemoVuelveP1EscaladoConSlaDeUnaHora() throws Exception {
            IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));

            ResultActions resultado = crearComo(SOLICITANTE, TITULO, DESCRIPCION, "cid-demo")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("/api/tickets/")))
                    .andExpect(header().string("X-Correlation-Id", "cid-demo"))
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.titulo").value(TITULO))
                    .andExpect(jsonPath("$.solicitanteId").value(SOLICITANTE))
                    .andExpect(jsonPath("$.agenteAsignadoId").value(nullValue()))
                    .andExpect(jsonPath("$.estado").value("ESCALADO"))
                    .andExpect(jsonPath("$.prioridad").value("P1"))
                    .andExpect(jsonPath("$.fechaLimiteSla").value("2026-10-05T15:03:11Z"))
                    .andExpect(jsonPath("$.requiereRevisionManual").value(false))
                    .andExpect(jsonPath("$.motivoEscalamiento").value("Prioridad P1"))
                    .andExpect(jsonPath("$.clasificacion.categoria").value("INCIDENTE"))
                    .andExpect(jsonPath("$.clasificacion.urgencia").value("ALTA"))
                    .andExpect(jsonPath("$.clasificacion.impacto").value("ALTO"))
                    .andExpect(jsonPath("$.clasificacion.moduloAfectado").value("AUTENTICACION"))
                    .andExpect(jsonPath("$.clasificacion.requiereEscalamiento").value(true))
                    .andExpect(jsonPath("$.clasificacion.confianza").value(0.93))
                    .andExpect(jsonPath("$.clasificacion.proveedor").value("mock"))
                    .andExpect(jsonPath("$.clasificacion.fecha").value(RespuestasIa.FECHA))
                    .andExpect(jsonPath("$.fechaCreacion").value(notNullValue()))
                    .andExpect(jsonPath("$.fechaActualizacion").value(notNullValue()));

            JsonNode ticket = leer(resultado);
            assertThat(ticket.get("descripcion").asText()).isEqualTo(DESCRIPCION);
            String cuerpo = resultado.andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).contains("\"agenteAsignadoId\":null");
            assertThat(ticket.get("fechaCreacion").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

            // La IA recibio el ticket con el correlationId del pedido.
            List<Pedido> pedidosIa = IA.pedidos();
            assertThat(pedidosIa).hasSize(1);
            JsonNode enviado = json.readTree(pedidosIa.get(0).cuerpo());
            assertThat(enviado.get("ticketId").asText()).isEqualTo(ticket.get("id").asText());
            assertThat(enviado.get("titulo").asText()).isEqualTo(TITULO);
            assertThat(enviado.get("descripcion").asText()).isEqualTo(DESCRIPCION);
            assertThat(pedidosIa.get(0).header("X-Correlation-Id")).isEqualTo("cid-demo");
        }

        @Test
        void unCasoComunQuedaClasificadoConSuPrioridad() throws Exception {
            JsonNode ticket = crearConIa(RespuestasIa.comun());

            assertThat(ticket.get("estado").asText()).isEqualTo("CLASIFICADO");
            assertThat(ticket.get("prioridad").asText()).isEqualTo("P3");
            assertThat(ticket.get("fechaLimiteSla").asText()).isEqualTo("2026-10-05T22:03:11Z");
            assertThat(ticket.get("motivoEscalamiento").isNull()).isTrue();
        }

        @Test
        void siLaIaMarcaCriticoSeFuerzaP1ConMotivoDeLaIa() throws Exception {
            JsonNode ticket = crearConIa(
                    RespuestasIa.clasificacion("INCIDENTE", "MEDIA", "BAJO", "PAGOS", true, 0.8));

            assertThat(ticket.get("prioridad").asText()).isEqualTo("P1");
            assertThat(ticket.get("estado").asText()).isEqualTo("ESCALADO");
            assertThat(ticket.get("motivoEscalamiento").asText()).isEqualTo("Marcado como crítico por la IA");
        }

        @Test
        void conConfianzaBajaPideRevisionManual() throws Exception {
            JsonNode ticket = crearConIa(
                    RespuestasIa.clasificacion("CONSULTA", "BAJA", "BAJO", "OTRO", false, 0.4));

            assertThat(ticket.get("requiereRevisionManual").asBoolean()).isTrue();
            assertThat(ticket.get("estado").asText()).isEqualTo("CLASIFICADO");
        }
    }

    @Nested
    class IaCaida {

        @Test
        void siLaIaDevuelve500ElTicketSeCreaIgualYQuedaPendiente() throws Exception {
            IA.responder(RUTA_IA, Respuesta.json(500, "{\"codigo\":\"ERROR_INTERNO\"}"));

            ResultActions resultado = crear(TITULO, DESCRIPCION)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_CLASIFICACION"));

            String cuerpo = resultado.andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).contains("\"prioridad\":null", "\"fechaLimiteSla\":null", "\"clasificacion\":null",
                    "\"motivoEscalamiento\":null");
        }

        @Test
        void siLaIaTardaMasQueElTimeoutQuedaPendiente() throws Exception {
            IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()).conDemora(Duration.ofMillis(3000)));

            long inicio = System.nanoTime();
            crear(TITULO, DESCRIPCION)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_CLASIFICACION"));
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

            // En los tests el timeout es de 1 s (en produccion, 7 s).
            assertThat(milisegundos).isLessThan(2500);
        }

        @Test
        void siLaIaDevuelveUnValorFueraDeLaListaQuedaPendiente() throws Exception {
            IA.responder(RUTA_IA, Respuesta.json(200,
                    RespuestasIa.clasificacion("URGENTISIMO", "ALTA", "ALTO", "OTRO", false, 0.9)));

            crear(TITULO, DESCRIPCION)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_CLASIFICACION"));
        }

        @Test
        void siLaIaDevuelveAlgoQueNoEsJsonQuedaPendiente() throws Exception {
            IA.responder(RUTA_IA, Respuesta.json(200, "esto no es json"));

            crear(TITULO, DESCRIPCION)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_CLASIFICACION"));
        }

        @Test
        void unTicketPendienteSeReclasificaCuandoLaIaVuelve() throws Exception {
            IA.responder(RUTA_IA, Respuesta.vacia(503));
            String id = leer(crear(TITULO, DESCRIPCION)).get("id").asText();

            IA.responder(RUTA_IA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
            mvc.perform(post("/api/tickets/{id}/reclasificacion", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ESCALADO"))
                    .andExpect(jsonPath("$.prioridad").value("P1"))
                    .andExpect(jsonPath("$.motivoEscalamiento").value("Prioridad P1"));
        }

        @Test
        void reclasificarConLaIaCaidaDa503YElTicketNoCambia() throws Exception {
            IA.responder(RUTA_IA, Respuesta.vacia(500));
            String id = leer(crear(TITULO, DESCRIPCION)).get("id").asText();

            mvc.perform(post("/api/tickets/{id}/reclasificacion", id).header("X-Correlation-Id", "cid-503"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"))
                    .andExpect(jsonPath("$.path").value("/api/tickets/" + id + "/reclasificacion"))
                    .andExpect(jsonPath("$.correlationId").value("cid-503"))
                    .andExpect(jsonPath("$.timestamp").value(notNullValue()));

            mvc.perform(get("/api/tickets/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_CLASIFICACION"));
        }

        @Test
        void noSeReclasificaUnTicketAsignado() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();
            asignar(id, GUARDIA).andExpect(status().isOk());

            mvc.perform(post("/api/tickets/{id}/reclasificacion", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        }
    }

    @Nested
    class AsignacionYEstados {

        @Test
        void cicloCompletoHastaCerrarYDespuesNoSePuedeReabrir() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            asignar(id, GUARDIA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ASIGNADO"))
                    .andExpect(jsonPath("$.agenteAsignadoId").value(GUARDIA));
            cambiarEstado(id, "EN_CURSO", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EN_CURSO"));
            cambiarEstado(id, "RESUELTO", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("RESUELTO"));
            cambiarEstado(id, "CERRADO", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CERRADO"));

            cambiarEstado(id, "EN_CURSO", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                    .andExpect(jsonPath("$.mensaje").value("No se puede pasar de CERRADO a EN_CURSO"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("estado"))
                    .andExpect(jsonPath("$.path").value("/api/tickets/" + id + "/estado"))
                    .andExpect(jsonPath("$.correlationId").value(notNullValue()));
        }

        @Test
        void unResueltoSePuedeReabrir() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();
            asignar(id, GUARDIA);
            cambiarEstado(id, "EN_CURSO", null);
            cambiarEstado(id, "RESUELTO", null);

            cambiarEstado(id, "EN_CURSO", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EN_CURSO"));
        }

        @Test
        void laGuardiaNoPuedeTomarUnEscaladoSinAgenteYDespuesDeAsignarloSi() throws Exception {
            String id = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();

            cambiarEstado(id, "EN_CURSO", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("REGLA_DE_NEGOCIO"));

            asignar(id, GUARDIA)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ESCALADO"))
                    .andExpect(jsonPath("$.agenteAsignadoId").value(GUARDIA));
            cambiarEstado(id, "EN_CURSO", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                    .andExpect(jsonPath("$.motivoEscalamiento").value("Prioridad P1"));
        }

        @Test
        void escalarAManoExigeMotivo() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();
            asignar(id, GUARDIA);

            cambiarEstado(id, "ESCALADO", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("motivo"));

            JsonNode escalado = leer(cambiarEstado(id, "ESCALADO", "El cliente reporta pérdida de datos")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ESCALADO")));
            assertThat(escalado.get("motivoEscalamiento").asText()).isEqualTo("El cliente reporta pérdida de datos");
        }

        @Test
        void noSePuedeAsignarUnTicketPendiente() throws Exception {
            IA.responder(RUTA_IA, Respuesta.vacia(500));
            String id = leer(crear(TITULO, DESCRIPCION)).get("id").asText();

            asignar(id, GUARDIA)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        }

        @Test
        void noSePuedePasarAAsignadoConUnCambioDeEstado() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            cambiarEstado(id, "ASIGNADO", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        }
    }

    @Nested
    class Validaciones {

        @Test
        void tituloCortoDa400ConElCampo() throws Exception {
            crear("abc", DESCRIPCION)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles", hasSize(1)))
                    .andExpect(jsonPath("$.detalles[0].campo").value("titulo"))
                    .andExpect(jsonPath("$.path").value("/api/tickets"));
            assertThat(IA.pedidos()).isEmpty();
        }

        @Test
        void bodyVacioDa400ConLosDosCampos() throws Exception {
            mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", SOLICITANTE).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles", hasSize(2)));
        }

        @Test
        void jsonMalFormadoDa400() throws Exception {
            mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", SOLICITANTE).content("{\"titulo\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }

        @Test
        void sinXUserIdDa400() throws Exception {
            mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"titulo\":\"" + TITULO + "\",\"descripcion\":\"" + DESCRIPCION + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("X-User-Id"));
        }

        @Test
        void xUserIdQueNoEsUuidDa400() throws Exception {
            crearComo("no-es-uuid", TITULO, DESCRIPCION, "cid")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("X-User-Id"));
        }

        @Test
        void estadoDesconocidoDa400() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            mvc.perform(patch("/api/tickets/{id}/estado", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"VOLANDO\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("estado"));
        }

        @Test
        void estadoFaltanteDa400() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            mvc.perform(patch("/api/tickets/{id}/estado", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("estado"));
        }

        @Test
        void agenteQueNoEsUuidDa400() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            mvc.perform(patch("/api/tickets/{id}/asignacion", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agenteId\":\"pepe\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("agenteId"));
        }

        @Test
        void idQueNoEsUuidDa400() throws Exception {
            mvc.perform(get("/api/tickets/no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("id"));
        }
    }

    @Nested
    class NoEncontrado {

        @Test
        void unTicketQueNoExisteDa404() throws Exception {
            String id = UUID.randomUUID().toString();

            mvc.perform(get("/api/tickets/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                    .andExpect(jsonPath("$.mensaje").value("No existe el ticket " + id))
                    .andExpect(jsonPath("$.detalles").doesNotExist());
            asignar(id, GUARDIA).andExpect(status().isNotFound());
            cambiarEstado(id, "EN_CURSO", null).andExpect(status().isNotFound());
            mvc.perform(post("/api/tickets/{id}/reclasificacion", id)).andExpect(status().isNotFound());
        }

        @Test
        void unaRutaQueNoExisteDa404ConElFormatoComun() throws Exception {
            mvc.perform(get("/api/no-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
    }

    @Nested
    class Listado {

        @Test
        void ordenaPorPrioridadConLosPendientesAlFinal() throws Exception {
            String p4 = crearConIa(RespuestasIa.consulta()).get("id").asText();
            String p1 = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();
            IA.responder(RUTA_IA, Respuesta.vacia(500));
            String pendiente = leer(crear(TITULO, DESCRIPCION)).get("id").asText();

            mvc.perform(get("/api/tickets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.content[0].id").value(p1))
                    .andExpect(jsonPath("$.content[1].id").value(p4))
                    .andExpect(jsonPath("$.content[2].id").value(pendiente))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        void filtraPorEstadoPrioridadYCategoria() throws Exception {
            crearConIa(RespuestasIa.consulta());
            String p1 = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();

            mvc.perform(get("/api/tickets").param("estado", "ESCALADO"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(p1));
            mvc.perform(get("/api/tickets").param("prioridad", "P4"))
                    .andExpect(jsonPath("$.content", hasSize(1)));
            mvc.perform(get("/api/tickets").param("categoria", "CONSULTA"))
                    .andExpect(jsonPath("$.content", hasSize(1)));
            mvc.perform(get("/api/tickets").param("categoria", "BUG"))
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void pagina() throws Exception {
            crearConIa(RespuestasIa.consulta());
            crearConIa(RespuestasIa.comun());
            crearConIa(RespuestasIa.laDeLaDemo());

            mvc.perform(get("/api/tickets").param("page", "1").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        void parametrosInvalidosDan400() throws Exception {
            mvc.perform(get("/api/tickets").param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.detalles[0].campo").value("size"));
            mvc.perform(get("/api/tickets").param("size", "0")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/tickets").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("page"));
            mvc.perform(get("/api/tickets").param("estado", "VOLANDO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("estado"));
        }

        @Test
        void unSolicitanteSoloVeLosSuyos() throws Exception {
            String otro = UUID.randomUUID().toString();
            String propio = leer(crearComo(SOLICITANTE, TITULO, DESCRIPCION, "cid-1")).get("id").asText();
            String ajeno = leer(crearComo(otro, TITULO, DESCRIPCION, "cid-2")).get("id").asText();

            mvc.perform(get("/api/tickets").header("X-User-Id", SOLICITANTE).header("X-User-Rol", "SOLICITANTE"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(propio));
            mvc.perform(get("/api/tickets/{id}", ajeno)
                            .header("X-User-Id", SOLICITANTE).header("X-User-Rol", "SOLICITANTE"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/tickets/{id}", propio)
                            .header("X-User-Id", SOLICITANTE).header("X-User-Rol", "SOLICITANTE"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/tickets").header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE"))
                    .andExpect(jsonPath("$.content", hasSize(2)));
            mvc.perform(get("/api/tickets/{id}", ajeno).header("X-User-Id", GUARDIA).header("X-User-Rol", "AGENTE"))
                    .andExpect(status().isOk());
        }

        @Test
        void unRolDesconocidoDa400() throws Exception {
            mvc.perform(get("/api/tickets").header("X-User-Id", SOLICITANTE).header("X-User-Rol", "JEFE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalles[0].campo").value("X-User-Rol"));
        }
    }

    @Test
    void healthRespondeUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ticket-service"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    @Test
    void generaUnCorrelationIdSiNoViene() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(header().string("X-Correlation-Id", notNullValue()));
    }

    private ResultActions asignar(String id, String agente) throws Exception {
        return mvc.perform(patch("/api/tickets/{id}/asignacion", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agenteId\":\"" + agente + "\"}"));
    }

    private ResultActions cambiarEstado(String id, String estado, String motivo) throws Exception {
        String cuerpo = motivo == null
                ? "{\"estado\":\"" + estado + "\"}"
                : "{\"estado\":\"" + estado + "\",\"motivo\":\"" + motivo + "\"}";
        return mvc.perform(patch("/api/tickets/{id}/estado", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }
}
