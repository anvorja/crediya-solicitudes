package com.crediya.solicitudes.model.evento.gateways;

import com.crediya.solicitudes.model.evento.EventoDeDominio;
import com.crediya.solicitudes.model.evento.EventoSaliente;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbox: los eventos se registran en la misma transaccion que el cambio de estado y un proceso aparte los publica.
 * Asi el cambio y su aviso nunca quedan inconsistentes: o ocurren los dos, o ninguno.
 */
public interface EventoSalienteRepository {

    Mono<Void> registrar(EventoDeDominio evento);

    /**
     * Toma (y bloquea hasta que termine la transaccion actual) los eventos pendientes mas antiguos. Varias instancias pueden
     * ejecutarlo a la vez sin tomar los mismos eventos.
     */
    Flux<EventoSaliente> tomarPendientes(int limite);

    Mono<Void> marcarEnviado(UUID id);

    Mono<Void> registrarFallo(UUID id, String motivo);
}
