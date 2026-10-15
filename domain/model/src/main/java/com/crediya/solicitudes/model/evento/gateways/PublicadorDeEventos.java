package com.crediya.solicitudes.model.evento.gateways;

import com.crediya.solicitudes.model.evento.EventoSaliente;
import reactor.core.publisher.Mono;

public interface PublicadorDeEventos {

    /**
     * Publica el evento en su cola. Falla si no pudo hacerlo, para que se reintente mas tarde.
     */
    Mono<Void> publicar(EventoSaliente evento);
}
