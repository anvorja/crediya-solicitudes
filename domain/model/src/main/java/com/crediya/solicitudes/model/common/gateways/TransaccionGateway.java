package com.crediya.solicitudes.model.common.gateways;

import reactor.core.publisher.Mono;

/**
 * Puerto de salida que ejecuta una operacion reactiva de forma atomica.
 */
public interface TransaccionGateway {

    <T> Mono<T> ejecutar(Mono<T> operacion);
}
