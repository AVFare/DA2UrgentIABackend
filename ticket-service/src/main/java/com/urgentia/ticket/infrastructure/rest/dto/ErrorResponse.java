package com.urgentia.ticket.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Formato comun de error del proyecto (seccion 5.3). detalles solo va en errores de validacion. */
@Schema(name = "Error")
public record ErrorResponse(
        @Schema(example = "TRANSICION_INVALIDA") String codigo,
        @Schema(example = "No se puede pasar de CERRADO a EN_CURSO") String mensaje,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Detalle> detalles,
        @Schema(example = "2026-10-05T14:03:11Z") String timestamp,
        @Schema(example = "/api/tickets/7c9e6679-7425-40de-944b-e07fc1f90ae7/estado") String path,
        @Schema(example = "a8e1b2c3-0000-4000-8000-000000000000") String correlationId) {

    public record Detalle(String campo, String mensaje) {
    }
}
