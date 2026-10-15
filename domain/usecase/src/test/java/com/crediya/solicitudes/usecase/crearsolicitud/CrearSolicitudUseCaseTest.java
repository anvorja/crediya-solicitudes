package com.crediya.solicitudes.usecase.crearsolicitud;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
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

    @Mock
    private ClienteGateway clienteGateway;
    @Mock
    private TipoPrestamoRepository tipoPrestamoRepository;
    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private TransaccionGateway transaccionGateway;

    private CrearSolicitudUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearSolicitudUseCase(clienteGateway, tipoPrestamoRepository, solicitudRepository, transaccionGateway);
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
        transaccionDejaPasar();
        var id = UUID.randomUUID();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenAnswer(inv -> Mono.just(((Solicitud) inv.getArgument(0)).conId(id)));

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
        transaccionDejaPasar();
        when(clienteGateway.buscarPorDocumento("1020304050")).thenReturn(Mono.just(CLIENTE));
        when(tipoPrestamoRepository.buscarPorNombre("PERSONAL")).thenReturn(Mono.just(PERSONAL));
        when(solicitudRepository.guardar(any())).thenReturn(Mono.error(new IllegalStateException("db caida")));

        StepVerifier.create(useCase.ejecutar(comando("1020304050", "5000000", 24, "PERSONAL")))
                .expectErrorMessage("db caida")
                .verify();
    }
}
