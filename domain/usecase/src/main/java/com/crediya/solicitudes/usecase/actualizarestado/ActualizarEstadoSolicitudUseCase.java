package com.crediya.solicitudes.usecase.actualizarestado;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.seguridad.Rol;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.ActualizarEstadoSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudModificadaException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * HU6: un asesor aprueba o rechaza una solicitud y el solicitante recibe la notificacion. El cambio de estado y el registro del
 * evento de notificacion ocurren en una sola transaccion (outbox): no puede quedar una decision sin aviso ni un aviso sin
 * decision. La consulta a AUTENTICACION (nombre del solicitante) se hace antes de abrir la transaccion para no mantener una
 * conexion de base de datos ocupada durante una llamada de red.
 */
public class ActualizarEstadoSolicitudUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteGateway clienteGateway;
    private final EventoSalienteRepository eventoSalienteRepository;
    private final TransaccionGateway transaccionGateway;
    private final ContextoSeguridadGateway contextoSeguridad;
    private final Clock reloj;

    public ActualizarEstadoSolicitudUseCase(SolicitudRepository solicitudRepository,
                                            ClienteGateway clienteGateway,
                                            EventoSalienteRepository eventoSalienteRepository,
                                            TransaccionGateway transaccionGateway,
                                            ContextoSeguridadGateway contextoSeguridad,
                                            Clock reloj) {
        this.solicitudRepository = solicitudRepository;
        this.clienteGateway = clienteGateway;
        this.eventoSalienteRepository = eventoSalienteRepository;
        this.transaccionGateway = transaccionGateway;
        this.contextoSeguridad = contextoSeguridad;
        this.reloj = reloj;
    }

    public Mono<Solicitud> ejecutar(ActualizarEstadoSolicitudCommand comando) {
        return Mono.fromCallable(() -> Decision.de(comando))
                .flatMap(this::autorizar)
                .flatMap(this::buscarSolicitud)
                .map(contexto -> contexto.conNuevaSolicitud(contexto.actual().cambiarEstado(contexto.decision().estado())))
                .flatMap(this::cargarNombreDelSolicitante)
                .flatMap(this::guardarYRegistrarEvento);
    }

    private Mono<Decision> autorizar(Decision decision) {
        return contextoSeguridad.usuarioActual()
                .filter(actor -> actor.rol() == Rol.ASESOR)
                .switchIfEmpty(Mono.error(AccesoDenegadoException::new))
                .thenReturn(decision);
    }

    private Mono<Contexto> buscarSolicitud(Decision decision) {
        return solicitudRepository.buscarPorId(decision.id())
                .switchIfEmpty(Mono.error(SolicitudNoEncontradaException::new))
                .map(actual -> new Contexto(decision, actual, null, null));
    }

    private Mono<Contexto> cargarNombreDelSolicitante(Contexto contexto) {
        return clienteGateway.buscarPorEmails(List.of(contexto.nueva().email()))
                .next()
                .map(Cliente::nombreCompleto)
                .map(contexto::conNombre)
                .defaultIfEmpty(contexto);
    }

    private Mono<Solicitud> guardarYRegistrarEvento(Contexto contexto) {
        Solicitud nueva = contexto.nueva();
        return transaccionGateway.ejecutar(solicitudRepository.actualizarEstado(nueva, contexto.actual().estado())
                .switchIfEmpty(Mono.error(SolicitudModificadaException::new))
                .flatMap(guardada -> eventoSalienteRepository
                        .registrar(new SolicitudDecidida(reloj.instant(), guardada, contexto.nombre(), List.of()))
                        .thenReturn(guardada)));
    }

    private record Decision(UUID id, EstadoSolicitud estado) {

        static Decision de(ActualizarEstadoSolicitudCommand comando) {
            return new Decision(uuid(comando.idSolicitud()), estadoFinal(comando.estado()));
        }

        private static UUID uuid(String texto) {
            if (texto == null || texto.isBlank()) {
                throw new DatosSolicitudInvalidosException("id_solicitud", "es obligatorio");
            }
            try {
                return UUID.fromString(texto.trim());
            } catch (IllegalArgumentException e) {
                throw new DatosSolicitudInvalidosException("id_solicitud", "debe ser un UUID valido");
            }
        }

        private static EstadoSolicitud estadoFinal(String texto) {
            EstadoSolicitud estado = EstadoSolicitud.desde(texto);
            if (!estado.esDecisionFinal()) {
                throw new DatosSolicitudInvalidosException("estado", "debe ser APROBADA o RECHAZADA");
            }
            return estado;
        }
    }

    private record Contexto(Decision decision, Solicitud actual, Solicitud nueva, String nombre) {

        Contexto conNuevaSolicitud(Solicitud solicitud) {
            return new Contexto(decision, actual, solicitud, nombre);
        }

        Contexto conNombre(String nombreCompleto) {
            return new Contexto(decision, actual, nueva, nombreCompleto);
        }
    }
}
