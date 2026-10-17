package com.crediya.solicitudes.usecase.listarsolicitudes;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.seguridad.Rol;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.ResumenSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU4: listado paginado y filtrable de las solicitudes que necesitan la decision de un asesor, con el nombre y salario del
 * solicitante y la deuda mensual de sus prestamos ya aprobados. Solo para ASESOR.
 */
public class ListarSolicitudesUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteGateway clienteGateway;
    private final ContextoSeguridadGateway contextoSeguridad;

    public ListarSolicitudesUseCase(SolicitudRepository solicitudRepository,
                                    ClienteGateway clienteGateway,
                                    ContextoSeguridadGateway contextoSeguridad) {
        this.solicitudRepository = solicitudRepository;
        this.clienteGateway = clienteGateway;
        this.contextoSeguridad = contextoSeguridad;
    }

    public Mono<Pagina<ResumenSolicitud>> ejecutar(FiltroSolicitudes filtro) {
        return contextoSeguridad.usuarioActual()
                .filter(actor -> actor.rol() == Rol.ASESOR)
                .switchIfEmpty(Mono.error(AccesoDenegadoException::new))
                .then(Mono.defer(() -> solicitudRepository.buscarParaRevision(filtro)))
                .flatMap(this::enriquecer);
    }

    private Mono<Pagina<ResumenSolicitud>> enriquecer(Pagina<Solicitud> pagina) {
        if (pagina.elementos().isEmpty()) {
            return Mono.just(new Pagina<>(List.of(), pagina.pagina(), pagina.tamano(), pagina.totalElementos()));
        }
        List<String> emails = pagina.elementos().stream().map(Solicitud::email).distinct().toList();
        return Mono.zip(clientesPorEmail(emails), deudaMensualPorEmail(emails), Contexto::new)
                .map(contexto -> pagina.mapear(solicitud -> resumir(solicitud, contexto)));
    }

    private Mono<Map<String, Cliente>> clientesPorEmail(List<String> emails) {
        return clienteGateway.buscarPorEmails(emails)
                .collectMap(cliente -> cliente.email().toLowerCase(Locale.ROOT), Function.identity());
    }

    private Mono<Map<String, BigDecimal>> deudaMensualPorEmail(List<String> emails) {
        return solicitudRepository.buscarAprobadasPorEmails(emails)
                .collect(Collectors.groupingBy(Solicitud::email,
                        Collectors.reducing(BigDecimal.ZERO, Solicitud::cuotaMensual, BigDecimal::add)));
    }

    private static ResumenSolicitud resumir(Solicitud solicitud, Contexto contexto) {
        Cliente cliente = contexto.clientes().get(solicitud.email());
        return new ResumenSolicitud(
                solicitud,
                cliente == null ? null : cliente.nombreCompleto(),
                cliente == null ? null : cliente.salarioBase(),
                contexto.deudaMensual().getOrDefault(solicitud.email(), BigDecimal.ZERO));
    }

    private record Contexto(Map<String, Cliente> clientes, Map<String, BigDecimal> deudaMensual) {
    }
}
