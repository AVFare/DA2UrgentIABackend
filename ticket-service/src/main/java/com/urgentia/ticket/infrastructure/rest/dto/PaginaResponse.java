package com.urgentia.ticket.infrastructure.rest.dto;

import java.util.List;

/** Formato comun de paginacion (seccion 5): page empieza en 0. */
public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
