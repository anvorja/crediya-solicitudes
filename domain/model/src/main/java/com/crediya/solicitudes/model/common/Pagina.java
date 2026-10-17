package com.crediya.solicitudes.model.common;

import java.util.List;
import java.util.function.Function;

/**
 * Una pagina de resultados. {@code pagina} empieza en 0.
 */
public record Pagina<T>(List<T> elementos, int pagina, int tamano, long totalElementos) {

    public int totalPaginas() {
        return tamano == 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }

    public <R> Pagina<R> mapear(Function<T, R> transformacion) {
        return new Pagina<>(elementos.stream().map(transformacion).toList(), pagina, tamano, totalElementos);
    }
}
