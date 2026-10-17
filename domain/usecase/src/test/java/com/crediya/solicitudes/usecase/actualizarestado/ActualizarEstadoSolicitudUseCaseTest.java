package com.crediya.solicitudes.usecase.actualizarestado;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.EventoDeDominio;
import com.crediya.solicitudes.model.evento.SolicitudAprobada;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.seguridad.Rol;
import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.ActualizarEstadoSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudModificadaException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.solicitud.exceptions.TransicionEstadoInvalidaException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActualizarEstadoSolicitudUseCase (HU6)")
class ActualizarEstadoSolicitudUseCaseTest {

    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);
    private static final Instant AHORA = Instant.parse("2026-10-15T10:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private ClienteGateway clienteGateway;
    @Mock
    private EventoSalienteRepository eventoSalienteRepository;
    @Mock
    private TransaccionGateway transaccionGateway;
    @Mock
    private ContextoSeguridadGateway contextoSeguridad;

    private ActualizarEstadoSolicitudUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActualizarEstadoSolicitudUseCase(solicitudRepository, clienteGateway, eventoSalienteRepository,
                transaccionGateway, contextoSeguridad, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void actuaComo(Rol rol) {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.just(
                new UsuarioAutenticado(UUID.randomUUID(), "9999999", "actor@crediya.co", rol)));
    }

    private void transaccionPasante() {
        when(transaccionGateway.ejecutar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Solicitud solicitud(EstadoSolicitud estado) {
        return new Solicitud(ID, new BigDecimal("5000000"), 24, "maria@example.com", estado, PERSONAL);
    }

    private static ActualizarEstadoSolicitudCommand comando(String estado) {
        return new ActualizarEstadoSolicitudCommand(ID.toString(), estado);
    }

    @Test
    @DisplayName("cuando un ASESOR aprueba, debe guardar el estado y registrar el evento con el nombre del solicitante en la misma transaccion")
    void asesorAprueba_guardaYRegistraEvento() {
        actuaComo(Rol.ASESOR);
        transaccionPasante();
        var pendiente = solicitud(EstadoSolicitud.PENDIENTE_REVISION);
        var aprobada = solicitud(EstadoSolicitud.APROBADA);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(pendiente));
        when(clienteGateway.buscarPorEmails(anyList())).thenReturn(Flux.just(
                new Cliente("1020304050", "Maria", "Gomez", "maria@example.com", new BigDecimal("4000000"))));
        when(solicitudRepository.actualizarEstado(aprobada, EstadoSolicitud.PENDIENTE_REVISION)).thenReturn(Mono.just(aprobada));
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("aprobada")))
                .expectNext(aprobada)
                .verifyComplete();

        var eventos = ArgumentCaptor.forClass(EventoDeDominio.class);
        verify(eventoSalienteRepository, times(2)).registrar(eventos.capture());
        var decidida = (SolicitudDecidida) eventos.getAllValues().get(0);
        assertThat(decidida.solicitud()).isEqualTo(aprobada);
        assertThat(decidida.nombreCliente()).isEqualTo("Maria Gomez");
        assertThat(decidida.ocurridoEn()).isEqualTo(AHORA);
        assertThat(decidida.planDePago()).isEmpty();
        var avisoReportes = (SolicitudAprobada) eventos.getAllValues().get(1);
        assertThat(avisoReportes.solicitud()).isEqualTo(aprobada);
        assertThat(avisoReportes.ocurridoEn()).isEqualTo(AHORA);
        verify(transaccionGateway).ejecutar(any());
    }

    @Test
    @DisplayName("cuando AUTENTICACION no conoce al solicitante, igual decide y el evento va sin nombre")
    void clienteDesconocido_decideSinNombre() {
        actuaComo(Rol.ASESOR);
        transaccionPasante();
        var rechazada = solicitud(EstadoSolicitud.RECHAZADA);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.REVISION_MANUAL)));
        when(clienteGateway.buscarPorEmails(anyList())).thenReturn(Flux.empty());
        when(solicitudRepository.actualizarEstado(rechazada, EstadoSolicitud.REVISION_MANUAL)).thenReturn(Mono.just(rechazada));
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("RECHAZADA"))).expectNext(rechazada).verifyComplete();

        var evento = ArgumentCaptor.forClass(SolicitudDecidida.class);
        verify(eventoSalienteRepository).registrar(evento.capture());
        assertThat(evento.getValue().nombreCliente()).isNull();
    }

    @Test
    @DisplayName("cuando AUTENTICACION no responde, no cambia nada y propaga el error")
    void autenticacionCaida_noCambiaNada() {
        actuaComo(Rol.ASESOR);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.PENDIENTE_REVISION)));
        when(clienteGateway.buscarPorEmails(anyList())).thenReturn(Flux.error(new ServicioNoDisponibleException("autenticacion")));

        StepVerifier.create(useCase.ejecutar(comando("APROBADA"))).expectError(ServicioNoDisponibleException.class).verify();

        verify(solicitudRepository, never()).actualizarEstado(any(), any());
        verifyNoInteractions(eventoSalienteRepository, transaccionGateway);
    }

    @ParameterizedTest(name = "rol = {0}")
    @ValueSource(strings = {"CLIENTE", "ADMIN"})
    @DisplayName("cuando el usuario no es ASESOR, debe denegar el acceso sin consultar nada")
    void noAsesor_denegado(String rol) {
        actuaComo(Rol.valueOf(rol));

        StepVerifier.create(useCase.ejecutar(comando("APROBADA"))).expectError(AccesoDenegadoException.class).verify();

        verifyNoInteractions(solicitudRepository, clienteGateway, eventoSalienteRepository, transaccionGateway);
    }

    @Test
    @DisplayName("cuando no hay usuario autenticado, debe denegar el acceso")
    void sinUsuario_denegado() {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("APROBADA"))).expectError(AccesoDenegadoException.class).verify();
    }

    @ParameterizedTest(name = "estado = {0}")
    @ValueSource(strings = {"PENDIENTE_REVISION", "REVISION_MANUAL", "INEXISTENTE", "", "  "})
    @DisplayName("cuando el estado destino no es APROBADA ni RECHAZADA, debe fallar con datos invalidos antes de tocar nada")
    void estadoDestinoInvalido(String estado) {
        StepVerifier.create(useCase.ejecutar(comando(estado))).expectError(DatosSolicitudInvalidosException.class).verify();

        verifyNoInteractions(contextoSeguridad, solicitudRepository, eventoSalienteRepository);
    }

    @ParameterizedTest(name = "id = [{0}]")
    @ValueSource(strings = {"", "  ", "no-es-uuid"})
    @DisplayName("cuando el id no es un UUID valido, debe fallar con datos invalidos")
    void idInvalido(String id) {
        StepVerifier.create(useCase.ejecutar(new ActualizarEstadoSolicitudCommand(id, "APROBADA")))
                .expectError(DatosSolicitudInvalidosException.class).verify();
    }

    @Test
    @DisplayName("cuando el id es nulo, debe fallar con datos invalidos")
    void idNulo() {
        StepVerifier.create(useCase.ejecutar(new ActualizarEstadoSolicitudCommand(null, "APROBADA")))
                .expectError(DatosSolicitudInvalidosException.class).verify();
    }

    @Test
    @DisplayName("cuando la solicitud no existe, debe fallar con SolicitudNoEncontrada")
    void noExiste() {
        actuaComo(Rol.ASESOR);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("APROBADA"))).expectError(SolicitudNoEncontradaException.class).verify();

        verifyNoInteractions(clienteGateway, eventoSalienteRepository);
    }

    @Test
    @DisplayName("cuando la solicitud ya esta aprobada, la transicion no se permite")
    void yaAprobada_transicionInvalida() {
        actuaComo(Rol.ASESOR);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.APROBADA)));

        StepVerifier.create(useCase.ejecutar(comando("RECHAZADA"))).expectError(TransicionEstadoInvalidaException.class).verify();

        verifyNoInteractions(clienteGateway, eventoSalienteRepository, transaccionGateway);
    }

    @Test
    @DisplayName("cuando otro proceso la modifico entre la lectura y la escritura, falla con SolicitudModificada y no registra evento")
    void modificadaPorOtro() {
        actuaComo(Rol.ASESOR);
        transaccionPasante();
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.PENDIENTE_REVISION)));
        when(clienteGateway.buscarPorEmails(anyList())).thenReturn(Flux.empty());
        when(solicitudRepository.actualizarEstado(any(), eq(EstadoSolicitud.PENDIENTE_REVISION))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("APROBADA"))).expectError(SolicitudModificadaException.class).verify();

        verify(eventoSalienteRepository, never()).registrar(any());
    }
}
