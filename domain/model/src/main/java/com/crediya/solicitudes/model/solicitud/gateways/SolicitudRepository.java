package com.crediya.solicitudes.model.solicitud.gateways;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import reactor.core.publisher.Mono;

public interface SolicitudRepository {

    /**
     * Inserta la solicitud si no tiene id y devuelve la solicitud persistida con su id.
     */
    Mono<Solicitud> guardar(Solicitud solicitud);
}
