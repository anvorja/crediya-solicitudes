package com.crediya.solicitudes.usecase.procesardecision;

import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.DecisionAutomaticaCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

/**
 * HU7: aplica el resultado de la validacion automatica. Aprobada o rechazada se notifica al solicitante; "revision manual" solo
 * cambia el estado, porque la decision final la toma un asesor.
 * <p>
 * Es idempotente: la cola entrega al menos una vez, asi que una decision para una solicitud que ya no esta pendiente (ya
 * procesada, o decidida a mano por un asesor) se ignora sin error y sin duplicar el aviso.
 */
public class ProcesarDecisionAutomaticaUseCase {

    private final SolicitudRepository solicitudRepository;
    private final EventoSalienteRepository eventoSalienteRepository;
    private final TransaccionGateway transaccionGateway;
    private final Clock reloj;

    public ProcesarDecisionAutomaticaUseCase(SolicitudRepository solicitudRepository,
                                             EventoSalienteRepository eventoSalienteRepository,
                                             TransaccionGateway transaccionGateway,
                                             Clock reloj) {
        this.solicitudRepository = solicitudRepository;
        this.eventoSalienteRepository = eventoSalienteRepository;
        this.transaccionGateway = transaccionGateway;
        this.reloj = reloj;
    }

    /**
     * @return la solicitud actualizada, o vacio si la decision se ignoro porque la solicitud ya no estaba pendiente
     */
    public Mono<Solicitud> ejecutar(DecisionAutomaticaCommand comando) {
        return Mono.fromCallable(() -> new Decision(identificador(comando.idSolicitud()), estado(comando.decision())))
                .flatMap(decision -> solicitudRepository.buscarPorId(decision.id())
                        .switchIfEmpty(Mono.error(SolicitudNoEncontradaException::new))
                        .filter(actual -> actual.estado() == EstadoSolicitud.PENDIENTE_REVISION)
                        .flatMap(actual -> aplicar(actual, decision.estado(), comando)));
    }

    private Mono<Solicitud> aplicar(Solicitud actual, EstadoSolicitud nuevoEstado, DecisionAutomaticaCommand comando) {
        Solicitud nueva = actual.cambiarEstado(nuevoEstado);
        // Si otro proceso la cambio entre la lectura y la escritura, no hay nada que hacer: ya la decidieron
        return transaccionGateway.ejecutar(solicitudRepository.actualizarEstado(nueva, actual.estado())
                .flatMap(guardada -> notificarSiEsFinal(guardada, comando)));
    }

    private Mono<Solicitud> notificarSiEsFinal(Solicitud guardada, DecisionAutomaticaCommand comando) {
        if (!guardada.estado().esDecisionFinal()) {
            return Mono.just(guardada);
        }
        return eventoSalienteRepository
                .registrar(new SolicitudDecidida(reloj.instant(), guardada, comando.nombreCliente(), comando.planDePago()))
                .thenReturn(guardada);
    }

    private static UUID identificador(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new DatosSolicitudInvalidosException("id_solicitud", "es obligatorio");
        }
        try {
            return UUID.fromString(texto.trim());
        } catch (IllegalArgumentException e) {
            throw new DatosSolicitudInvalidosException("id_solicitud", "debe ser un UUID valido");
        }
    }

    private static EstadoSolicitud estado(String texto) {
        EstadoSolicitud estado = EstadoSolicitud.desde(texto);
        if (estado == EstadoSolicitud.PENDIENTE_REVISION) {
            throw new DatosSolicitudInvalidosException("decision", "debe ser APROBADA, RECHAZADA o REVISION_MANUAL");
        }
        return estado;
    }

    private record Decision(UUID id, EstadoSolicitud estado) {
    }
}
