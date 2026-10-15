package com.crediya.solicitudes.usecase.listarsolicitudes;

import com.crediya.solicitudes.model.cliente.Cliente;
import com.crediya.solicitudes.model.cliente.gateways.ClienteGateway;
import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.crediya.solicitudes.model.seguridad.Rol;
import com.crediya.solicitudes.model.seguridad.UsuarioAutenticado;
import com.crediya.solicitudes.model.seguridad.gateways.ContextoSeguridadGateway;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListarSolicitudesUseCase (HU4)")
class ListarSolicitudesUseCaseTest {

    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);
    private static final FiltroSolicitudes FILTRO = FiltroSolicitudes.de(null, null, 0, 10);

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private ClienteGateway clienteGateway;
    @Mock
    private ContextoSeguridadGateway contextoSeguridad;

    private ListarSolicitudesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListarSolicitudesUseCase(solicitudRepository, clienteGateway, contextoSeguridad);
    }

    private void actuaComo(Rol rol) {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.just(
                new UsuarioAutenticado(UUID.randomUUID(), "9999999", "actor@crediya.co", rol)));
    }

    private static Solicitud solicitud(String email, String monto, int plazo, EstadoSolicitud estado) {
        return new Solicitud(UUID.randomUUID(), new BigDecimal(monto), plazo, email, estado, PERSONAL);
    }

    private static Cliente cliente(String email, String nombres, String salario) {
        return new Cliente("1020304050", nombres, "Gomez", email, new BigDecimal(salario));
    }

    @Test
    @DisplayName("cuando un ASESOR lista, debe enriquecer cada solicitud con nombre, salario y deuda mensual de sus aprobadas")
    void cuandoAsesor_debeEnriquecerLasSolicitudes() {
        actuaComo(Rol.ASESOR);
        var pendiente = solicitud("maria@example.com", "5000000", 24, EstadoSolicitud.PENDIENTE_REVISION);
        var otra = solicitud("pedro@example.com", "1000000", 12, EstadoSolicitud.RECHAZADA);
        when(solicitudRepository.buscarParaRevision(FILTRO)).thenReturn(Mono.just(new Pagina<>(List.of(pendiente, otra), 0, 10, 2)));
        when(clienteGateway.buscarPorEmails(anyCollection())).thenReturn(Flux.just(
                cliente("maria@example.com", "Maria", "4500000"), cliente("pedro@example.com", "Pedro", "3000000")));
        // Maria ya tiene DOS prestamos aprobados: su deuda mensual es la suma de ambas cuotas
        when(solicitudRepository.buscarAprobadasPorEmails(anyCollection())).thenReturn(Flux.just(
                solicitud("maria@example.com", "5000000", 24, EstadoSolicitud.APROBADA),
                solicitud("maria@example.com", "1000000", 12, EstadoSolicitud.APROBADA)));

        StepVerifier.create(useCase.ejecutar(FILTRO))
                .assertNext(pagina -> {
                    assertThat(pagina.totalElementos()).isEqualTo(2);
                    var maria = pagina.elementos().get(0);
                    assertThat(maria.solicitud()).isEqualTo(pendiente);
                    assertThat(maria.nombreCliente()).isEqualTo("Maria Gomez");
                    assertThat(maria.salarioBase()).isEqualByComparingTo("4500000");
                    assertThat(maria.deudaTotalMensualAprobadas()).isEqualByComparingTo("351806.16"); // 258404.18 + 93401.98 (PERSONAL, 1.8% mensual)
                    var pedro = pagina.elementos().get(1);
                    assertThat(pedro.nombreCliente()).isEqualTo("Pedro Gomez");
                    assertThat(pedro.deudaTotalMensualAprobadas()).isEqualByComparingTo("0");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("debe consultar los correos una sola vez cada uno (sin duplicados) en una consulta por lotes")
    void debeConsultarCorreosSinDuplicados() {
        actuaComo(Rol.ASESOR);
        var uno = solicitud("maria@example.com", "5000000", 24, EstadoSolicitud.PENDIENTE_REVISION);
        var dos = solicitud("maria@example.com", "2000000", 12, EstadoSolicitud.REVISION_MANUAL);
        when(solicitudRepository.buscarParaRevision(any())).thenReturn(Mono.just(new Pagina<>(List.of(uno, dos), 0, 10, 2)));
        when(clienteGateway.buscarPorEmails(anyCollection())).thenReturn(Flux.empty());
        when(solicitudRepository.buscarAprobadasPorEmails(anyCollection())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.ejecutar(FILTRO)).expectNextCount(1).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> correos = ArgumentCaptor.forClass(Collection.class);
        verify(clienteGateway).buscarPorEmails(correos.capture());
        assertThat(correos.getValue()).containsExactly("maria@example.com");
    }

    @Test
    @DisplayName("cuando AUTENTICACION ya no conoce al solicitante, debe listar la solicitud con nombre y salario nulos")
    void cuandoClienteDesconocido_debeListarConDatosNulos() {
        actuaComo(Rol.ASESOR);
        var pendiente = solicitud("fantasma@example.com", "5000000", 24, EstadoSolicitud.PENDIENTE_REVISION);
        when(solicitudRepository.buscarParaRevision(any())).thenReturn(Mono.just(new Pagina<>(List.of(pendiente), 0, 10, 1)));
        when(clienteGateway.buscarPorEmails(anyCollection())).thenReturn(Flux.empty());
        when(solicitudRepository.buscarAprobadasPorEmails(anyCollection())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.ejecutar(FILTRO))
                .assertNext(pagina -> {
                    var resumen = pagina.elementos().get(0);
                    assertThat(resumen.nombreCliente()).isNull();
                    assertThat(resumen.salarioBase()).isNull();
                    assertThat(resumen.deudaTotalMensualAprobadas()).isEqualByComparingTo("0");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("el correo devuelto por AUTENTICACION se compara sin distinguir mayusculas")
    void correoDeAutenticacion_seComparaSinMayusculas() {
        actuaComo(Rol.ASESOR);
        var pendiente = solicitud("maria@example.com", "5000000", 24, EstadoSolicitud.PENDIENTE_REVISION);
        when(solicitudRepository.buscarParaRevision(any())).thenReturn(Mono.just(new Pagina<>(List.of(pendiente), 0, 10, 1)));
        when(clienteGateway.buscarPorEmails(anyCollection())).thenReturn(Flux.just(cliente("MARIA@Example.com", "Maria", "4500000")));
        when(solicitudRepository.buscarAprobadasPorEmails(anyCollection())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.ejecutar(FILTRO))
                .assertNext(pagina -> assertThat(pagina.elementos().get(0).nombreCliente()).isEqualTo("Maria Gomez"))
                .verifyComplete();
    }

    @Test
    @DisplayName("cuando no hay solicitudes, debe devolver una pagina vacia sin consultar a AUTENTICACION ni las aprobadas")
    void cuandoNoHaySolicitudes_debeDevolverPaginaVacia() {
        actuaComo(Rol.ASESOR);
        when(solicitudRepository.buscarParaRevision(any())).thenReturn(Mono.just(new Pagina<>(List.of(), 2, 10, 0)));

        StepVerifier.create(useCase.ejecutar(FILTRO))
                .assertNext(pagina -> {
                    assertThat(pagina.elementos()).isEmpty();
                    assertThat(pagina.pagina()).isEqualTo(2);
                    assertThat(pagina.totalElementos()).isZero();
                })
                .verifyComplete();

        verifyNoInteractions(clienteGateway);
    }

    @ParameterizedTest(name = "actor {0}")
    @EnumSource(value = Rol.class, names = {"CLIENTE", "ADMIN"})
    @DisplayName("cuando quien llama no es ASESOR, debe denegar sin consultar nada")
    void cuandoNoEsAsesor_debeDenegar(Rol rol) {
        actuaComo(rol);

        StepVerifier.create(useCase.ejecutar(FILTRO)).expectError(AccesoDenegadoException.class).verify();

        verifyNoInteractions(solicitudRepository, clienteGateway);
    }

    @Test
    @DisplayName("cuando no hay usuario autenticado, debe denegar")
    void cuandoSinUsuario_debeDenegar() {
        when(contextoSeguridad.usuarioActual()).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(FILTRO)).expectError(AccesoDenegadoException.class).verify();

        verifyNoInteractions(solicitudRepository, clienteGateway);
    }

    @Test
    @DisplayName("cuando el repositorio falla, debe propagar el error")
    void cuandoRepositorioFalla_debePropagarError() {
        actuaComo(Rol.ASESOR);
        when(solicitudRepository.buscarParaRevision(any())).thenReturn(Mono.error(new IllegalStateException("db caida")));

        StepVerifier.create(useCase.ejecutar(FILTRO)).expectErrorMessage("db caida").verify();
    }
}
