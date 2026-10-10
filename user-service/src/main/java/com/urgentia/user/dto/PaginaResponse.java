package com.urgentia.user.dto;

import java.util.List;

/** Formato comun de paginacion: page empieza en 0. */
public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
