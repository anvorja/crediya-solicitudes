package com.crediya.solicitudes.model.solicitud.gateways;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface SolicitudRepository {

    /**
     * Inserta la solicitud si no tiene id y devuelve la solicitud persistida con su id.
     */
    Mono<Solicitud> guardar(Solicitud solicitud);

    /**
     * Pagina de solicitudes que cumplen el filtro, de la mas antigua a la mas reciente (orden de atencion).
     */
    Mono<Pagina<Solicitud>> buscarParaRevision(FiltroSolicitudes filtro);

    /**
     * Solicitudes en estado Aprobada de los solicitantes indicados (correos en minusculas).
     */
    Flux<Solicitud> buscarAprobadasPorEmails(Collection<String> emails);
}
