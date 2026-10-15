package com.crediya.solicitudes.model.tipoprestamo.gateways;

import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import reactor.core.publisher.Mono;

public interface TipoPrestamoRepository {

    /**
     * Busca por nombre sin distinguir mayusculas; Mono vacio si no existe.
     */
    Mono<TipoPrestamo> buscarPorNombre(String nombre);
}
