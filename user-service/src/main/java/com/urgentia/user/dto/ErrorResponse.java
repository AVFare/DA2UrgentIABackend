package com.urgentia.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Formato comun de error del proyecto (seccion 5.3). detalles solo va en errores de validacion. */
@Schema(name = "Error")
public record ErrorResponse(
        @Schema(example = "EMAIL_DUPLICADO") String codigo,
        @Schema(example = "Ya existe un usuario con ese email") String mensaje,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Detalle> detalles,
        @Schema(example = "2026-10-05T14:03:11Z") String timestamp,
        @Schema(example = "/api/usuarios") String path,
        @Schema(example = "a8e1b2c3-0000-4000-8000-000000000000") String correlationId) {

    public record Detalle(String campo, String mensaje) {
    }
}
