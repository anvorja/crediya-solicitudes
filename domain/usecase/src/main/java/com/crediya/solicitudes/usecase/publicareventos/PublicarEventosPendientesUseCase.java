package com.crediya.solicitudes.usecase.publicareventos;

import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.EventoSaliente;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.evento.gateways.PublicadorDeEventos;
import reactor.core.publisher.Mono;

/**
 * Publica los eventos pendientes del outbox. Entrega "al menos una vez": si el proceso cae despues de publicar y antes de
 * marcar el evento como enviado, se publicara otra vez; los consumidores deben tolerar duplicados.
 * Un fallo al publicar un evento no impide publicar los demas: se registra y se reintenta en la siguiente ronda.
 */
public class PublicarEventosPendientesUseCase {

    private static final int LARGO_MAXIMO_MOTIVO = 300;

    private final EventoSalienteRepository eventoSalienteRepository;
    private final PublicadorDeEventos publicadorDeEventos;
    private final TransaccionGateway transaccionGateway;

    public PublicarEventosPendientesUseCase(EventoSalienteRepository eventoSalienteRepository,
                                            PublicadorDeEventos publicadorDeEventos,
                                            TransaccionGateway transaccionGateway) {
        this.eventoSalienteRepository = eventoSalienteRepository;
        this.publicadorDeEventos = publicadorDeEventos;
        this.transaccionGateway = transaccionGateway;
    }

    /**
     * @return cuantos eventos se publicaron en esta ronda
     */
    public Mono<Long> ejecutar(int limite) {
        return transaccionGateway.ejecutar(eventoSalienteRepository.tomarPendientes(limite)
                .concatMap(this::publicar)
                .reduce(0L, Long::sum));
    }

    private Mono<Long> publicar(EventoSaliente evento) {
        return publicadorDeEventos.publicar(evento)
                .then(Mono.defer(() -> eventoSalienteRepository.marcarEnviado(evento.id())))
                .thenReturn(1L)
                .onErrorResume(error -> Mono.defer(() -> eventoSalienteRepository.registrarFallo(evento.id(), motivo(error))).thenReturn(0L));
    }

    private static String motivo(Throwable error) {
        String texto = error.getClass().getSimpleName() + ": " + error.getMessage();
        return texto.length() > LARGO_MAXIMO_MOTIVO ? texto.substring(0, LARGO_MAXIMO_MOTIVO) : texto;
    }
}
