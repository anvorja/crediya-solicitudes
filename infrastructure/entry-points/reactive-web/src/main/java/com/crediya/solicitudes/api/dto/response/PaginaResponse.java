package com.crediya.solicitudes.api.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Pagina de resultados. {@code pagina} empieza en 0.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
}
