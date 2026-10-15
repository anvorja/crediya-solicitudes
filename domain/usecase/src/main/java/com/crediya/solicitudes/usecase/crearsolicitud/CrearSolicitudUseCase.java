package com.crediya.solicitudes.usecase.crearsolicitud;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.CrearSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.model.tipoprestamo.gateways.TipoPrestamoRepository;
import reactor.core.publisher.Mono;

/**
 * HU2 + HU3: registra una solicitud de prestamo verificando al cliente y el tipo de prestamo. Solo un CLIENTE
 * puede crearla y unicamente a su propio nombre.
 */
public class CrearSolicitudUseCase {

    private final ClienteGateway clienteGateway;
    private final TipoPrestamoRepository tipoPrestamoRepository;
    private final SolicitudRepository solicitudRepository;
    private final TransaccionGateway transaccionGateway;
    private final ContextoSeguridadGateway contextoSeguridad;

    public CrearSolicitudUseCase(ClienteGateway clienteGateway,
                                 TipoPrestamoRepository tipoPrestamoRepository,
                                 SolicitudRepository solicitudRepository,
                                 TransaccionGateway transaccionGateway,
                                 ContextoSeguridadGateway contextoSeguridad) {
        this.clienteGateway = clienteGateway;
        this.tipoPrestamoRepository = tipoPrestamoRepository;
        this.solicitudRepository = solicitudRepository;
        this.transaccionGateway = transaccionGateway;
        this.contextoSeguridad = contextoSeguridad;
    }

    public Mono<Solicitud> ejecutar(CrearSolicitudCommand comando) {
        return Mono.fromCallable(() -> exigirDatosBasicos(comando))
                .flatMap(this::autorizar)
                .flatMap(this::cargarContexto)
                .map(contexto -> Solicitud.crear(contexto.cliente().email(), comando.monto(), comando.plazo(),
                        contexto.tipoPrestamo()))
                .flatMap(solicitudRepository::guardar)
                .as(transaccionGateway::ejecutar);
    }

    /**
     * Solo un CLIENTE autenticado puede crear la solicitud y unicamente a nombre de su propio documento.
     */
    private Mono<CrearSolicitudCommand> autorizar(CrearSolicitudCommand comando) {
        return contextoSeguridad.usuarioActual()
                .filter(actor -> actuaEnNombrePropio(actor, comando))
                .switchIfEmpty(Mono.error(AccesoDenegadoException::new))
                .thenReturn(comando);
    }

    private static boolean actuaEnNombrePropio(UsuarioAutenticado actor, CrearSolicitudCommand comando) {
        return actor.esCliente() && actor.documentoIdentidad().equals(comando.documentoIdentidad().trim());
    }

    private Mono<Contexto> cargarContexto(CrearSolicitudCommand comando) {
        return Mono.zip(buscarCliente(comando), buscarTipoPrestamo(comando), Contexto::new);
    }

    private Mono<Cliente> buscarCliente(CrearSolicitudCommand comando) {
        return clienteGateway.buscarPorDocumento(comando.documentoIdentidad().trim())
                .switchIfEmpty(Mono.error(ClienteNoEncontradoException::new));
    }

    private Mono<TipoPrestamo> buscarTipoPrestamo(CrearSolicitudCommand comando) {
        return tipoPrestamoRepository.buscarPorNombre(comando.tipoPrestamo().trim())
                .switchIfEmpty(Mono.error(() -> new TipoPrestamoNoValidoException(comando.tipoPrestamo().trim())));
    }

    private static CrearSolicitudCommand exigirDatosBasicos(CrearSolicitudCommand comando) {
        if (comando.documentoIdentidad() == null || comando.documentoIdentidad().isBlank()) {
            throw new DatosSolicitudInvalidosException("documento_identidad", "es obligatorio");
        }
        if (comando.tipoPrestamo() == null || comando.tipoPrestamo().isBlank()) {
            throw new DatosSolicitudInvalidosException("tipo_prestamo", "es obligatorio");
        }
        return comando;
    }

    private record Contexto(Cliente cliente, TipoPrestamo tipoPrestamo) {
    }
}
