package com.crediya.solicitudes.usecase.crearsolicitud;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.evento.ValidacionAutomaticaSolicitada;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.seguridad.Rol;
import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.PrestamoAprobado;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.CrearSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.MontoFueraDeRangoException;
import com.crediya.solicitudes.model.solicitud.exceptions.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.model.tipoprestamo.gateways.TipoPrestamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrearSolicitudUseCase (HU2)")
class CrearSolicitudUseCaseTest {

    private static final Cliente CLIENTE = new Cliente("1020304050", "Maria", "Gomez", "maria@example.com",
            new BigDecimal("4500000"));
    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);

    private static final Instant AHORA = Instant.parse("2026-10-15T10:00:00Z");
    private static final TipoPrestamo VEHICULO = new TipoPrestamo(3L, "VEHICULO", new BigDecimal("10000000"),
            new BigDecimal("200000000"), new BigDecimal("0.012"), false);

    @Mock
    private ClienteGateway clienteGateway;
    @Mock
    private TipoPrestamoRepository tipoPrestamoRepository;
    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private TransaccionGateway transaccionGateway;
    @Mock
    private ContextoSeguridadGateway contextoSeguridad;
    @Mock
    private EventoSalienteRepository eventoSalienteRepository;

    private CrearSolicitudUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearSolicitudUseCase(clienteGateway, tipoPrestamoRepository, solicitudRepository, transaccionGateway,
                contextoSeguridad, eventoSalienteRepository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void actuaComo(Rol rol, String documento) {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.just(
                new UsuarioAutenticado(UUID.randomUUID(), documento, "actor@crediya.co", rol)));
    }

    private void actuaComoElCliente() {
        actuaComo(Rol.CLIENTE, "1020304050");
    }

    private void transaccionDejaPasar() {
        when(transaccionGateway.ejecutar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static CrearSolicitudCommand comando(String documento, String monto, Integer plazo, String tipo) {
        return new CrearSolicitudCommand(documento, monto == null ? null : new BigDecimal(monto), plazo, tipo);
    }

    @Test
    @DisplayName("cuando el cliente y el tipo existen, debe guardar una solicitud Pendiente de revision con el email del cliente")
    void cuandoTodoValido_debeGuardarPendienteDeRevision() {
        actuaComoElCliente();
        transaccionDejaPasar();
        var id = UUID.randomUUID();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenAnswer(inv -> Mono.just(((Solicitud) inv.getArgument(0)).conId(id)));
        when(solicitudRepository.buscarAprobadasPorEmails(List.of("maria@example.com"))).thenReturn(Flux.empty());
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando(" 1020304050 ", "5000000", 24, " PERSONAL ")))
                .assertNext(solicitud -> {
                    assertThat(solicitud.id()).isEqualTo(id);
                    assertThat(solicitud.email()).isEqualTo("maria@example.com");
                    assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.PENDIENTE_REVISION);
                    assertThat(solicitud.tipoPrestamo()).isEqualTo(PERSONAL);
                })
                .verifyComplete();

        var capturada = ArgumentCaptor.forClass(Solicitud.class);
        verify(solicitudRepository).guardar(capturada.capture());
        assertThat(capturada.getValue().id()).isNull();
        verify(transaccionGateway).ejecutar(any());
    }

    @Test
    @DisplayName("cuando el cliente no existe, debe fallar con ClienteNoEncontradoException y no guardar")
    void cuandoClienteNoExiste_debeFallarSinGuardar() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.empty());
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectError(ClienteNoEncontradoException.class)
                .verify();

        verify(solicitudRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("cuando el tipo de prestamo no existe, debe fallar con TipoPrestamoNoValidoException y no guardar")
    void cuandoTipoNoExiste_debeFallarSinGuardar() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("INEXISTENTE")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "INEXISTENTE")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(TipoPrestamoNoValidoException.class)
                        .hasMessageContaining("INEXISTENTE"))
                .verify();

        verify(solicitudRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("cuando el monto esta fuera del rango del tipo, debe fallar con MontoFueraDeRangoException y no guardar")
    void cuandoMontoFueraDeRango_debeFallarSinGuardar() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "100", 24, "PERSONAL")))
                .expectError(MontoFueraDeRangoException.class)
                .verify();

        verify(solicitudRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("cuando el documento viene vacio, debe fallar sin consultar ningun puerto")
    void cuandoDocumentoVacio_debeFallarSinConsultar() {
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando("  ", "5000000", 24, "PERSONAL")))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(DatosSolicitudInvalidosException.class)
                        .hasMessageContaining("documento_identidad"))
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @ParameterizedTest(name = "tipo = [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("cuando el tipo de prestamo viene nulo o vacio, debe fallar sin consultar ningun puerto")
    void cuandoTipoVacio_debeFallarSinConsultar(String tipo) {
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, tipo)))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(DatosSolicitudInvalidosException.class)
                        .hasMessageContaining("tipo_prestamo"))
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @Test
    @DisplayName("cuando el documento es nulo, debe fallar sin consultar ningun puerto")
    void cuandoDocumentoNulo_debeFallarSinConsultar() {
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando(null, "5000000", 24, "PERSONAL")))
                .expectError(DatosSolicitudInvalidosException.class)
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @Test
    @DisplayName("cuando el repositorio falla al guardar, debe propagar el error")
    void cuandoGuardarFalla_debePropagarError() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenReturn(Mono.error(new IllegalStateException("db caida")));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectErrorMessage("db caida")
                .verify();
    }

    @Test
    @DisplayName("cuando el cliente crea la solicitud a nombre de otra persona, debe denegar sin consultar ningun puerto")
    void cuandoSolicitudAjena_debeDenegar() {
        actuaComo(Rol.CLIENTE, "1111111111");
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectError(AccesoDenegadoException.class)
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @ParameterizedTest(name = "actor {0}")
    @EnumSource(value = Rol.class, names = {"ADMIN", "ASESOR"})
    @DisplayName("cuando quien llama no es un CLIENTE, debe denegar aunque el documento coincida")
    void cuandoActorNoEsCliente_debeDenegar(Rol rol) {
        actuaComo(rol, "1020304050");
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectError(AccesoDenegadoException.class)
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @Test
    @DisplayName("cuando no hay usuario autenticado, debe denegar")
    void cuandoSinUsuarioAutenticado_debeDenegar() {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.empty());
        transaccionDejaPasar();

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectError(AccesoDenegadoException.class)
                .verify();

        verifyNoInteractions(clienteGateway, tipoPrestamoRepository, solicitudRepository);
    }

    @Test
    @DisplayName("HU7: un tipo con validacion automatica registra, en la misma transaccion, el pedido de evaluacion con salario, nombre y prestamos aprobados")
    void tipoAutomatico_registraPedidoDeValidacion() {
        actuaComoElCliente();
        transaccionDejaPasar();
        var id = UUID.randomUUID();
        var aprobada = new Solicitud(UUID.randomUUID(), new BigDecimal("2000000"), 12, "maria@example.com", EstadoSolicitud.APROBADA, PERSONAL);
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenAnswer(inv -> Mono.just(((Solicitud) inv.getArgument(0)).conId(id)));
        when(solicitudRepository.buscarAprobadasPorEmails(List.of("maria@example.com"))).thenReturn(Flux.just(aprobada));
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectNextCount(1).verifyComplete();

        var evento = ArgumentCaptor.forClass(ValidacionAutomaticaSolicitada.class);
        verify(eventoSalienteRepository).registrar(evento.capture());
        assertThat(evento.getValue().solicitud().id()).isEqualTo(id);
        assertThat(evento.getValue().nombreCliente()).isEqualTo("Maria Gomez");
        assertThat(evento.getValue().salarioBase()).isEqualByComparingTo("4500000");
        assertThat(evento.getValue().ocurridoEn()).isEqualTo(AHORA);
        assertThat(evento.getValue().prestamosAprobados()).containsExactly(PrestamoAprobado.de(aprobada));
        verify(transaccionGateway).ejecutar(any());
    }

    @Test
    @DisplayName("HU7: un tipo sin validacion automatica queda Pendiente sin evento ni consulta de prestamos")
    void tipoManual_noRegistraEvento() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("VEHICULO")).thenReturn(Mono.just(VEHICULO));
        when(solicitudRepository.guardar(any())).thenAnswer(inv -> Mono.just(((Solicitud) inv.getArgument(0)).conId(UUID.randomUUID())));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "20000000", 48, "VEHICULO")))
                .assertNext(s -> assertThat(s.estado()).isEqualTo(EstadoSolicitud.PENDIENTE_REVISION)).verifyComplete();

        verifyNoInteractions(eventoSalienteRepository);
        verify(solicitudRepository, never()).buscarAprobadasPorEmails(any());
    }

    @Test
    @DisplayName("HU7: si no se puede registrar el pedido de evaluacion, el error se propaga para que la transaccion revierta la solicitud")
    void falloAlRegistrarEvento_propagaError() {
        actuaComoElCliente();
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenAnswer(inv -> Mono.just(((Solicitud) inv.getArgument(0)).conId(UUID.randomUUID())));
        when(solicitudRepository.buscarAprobadasPorEmails(any())).thenReturn(Flux.empty());
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.error(new IllegalStateException("bd caida")));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectError(IllegalStateException.class).verify();
    }
}
