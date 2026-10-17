package com.crediya.solicitudes.usecase.procesardecision;

import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.EventoDeDominio;
import com.crediya.solicitudes.model.evento.SolicitudAprobada;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.DecisionAutomaticaCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcesarDecisionAutomaticaUseCase (HU7)")
class ProcesarDecisionAutomaticaUseCaseTest {

    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);
    private static final Instant AHORA = Instant.parse("2026-10-15T10:00:00Z");
    private static final UUID ID = UUID.randomUUID();
    private static final List<CuotaPlanDePago> PLAN = List.of(new CuotaPlanDePago(1, new BigDecimal("258404.18"),
            new BigDecimal("168404.18"), new BigDecimal("90000.00"), new BigDecimal("4831595.82")));

    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private EventoSalienteRepository eventoSalienteRepository;
    @Mock
    private TransaccionGateway transaccionGateway;

    private ProcesarDecisionAutomaticaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcesarDecisionAutomaticaUseCase(solicitudRepository, eventoSalienteRepository, transaccionGateway,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Solicitud solicitud(EstadoSolicitud estado) {
        return new Solicitud(ID, new BigDecimal("5000000"), 24, "maria@example.com", estado, PERSONAL);
    }

    private static DecisionAutomaticaCommand decision(String estado) {
        return new DecisionAutomaticaCommand(ID.toString(), estado, "Maria Gomez", PLAN);
    }

    private void transaccionDejaPasar() {
        when(transaccionGateway.ejecutar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest(name = "decision = {0}")
    @ValueSource(strings = {"APROBADA", "RECHAZADA"})
    @DisplayName("una decision final cambia el estado y registra el aviso con el nombre y el plan de pago")
    void decisionFinal_guardaYNotifica(String estado) {
        transaccionDejaPasar();
        var nueva = solicitud(EstadoSolicitud.valueOf(estado));
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.PENDIENTE_REVISION)));
        when(solicitudRepository.actualizarEstado(nueva, EstadoSolicitud.PENDIENTE_REVISION)).thenReturn(Mono.just(nueva));
        when(eventoSalienteRepository.registrar(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(decision(estado))).expectNext(nueva).verifyComplete();

        boolean aprobada = estado.equals("APROBADA");
        var eventos = ArgumentCaptor.forClass(EventoDeDominio.class);
        verify(eventoSalienteRepository, times(aprobada ? 2 : 1)).registrar(eventos.capture());
        var decidida = (SolicitudDecidida) eventos.getAllValues().get(0);
        assertThat(decidida.solicitud()).isEqualTo(nueva);
        assertThat(decidida.nombreCliente()).isEqualTo("Maria Gomez");
        assertThat(decidida.planDePago()).isEqualTo(PLAN);
        assertThat(decidida.ocurridoEn()).isEqualTo(AHORA);
        if (aprobada) {
            var avisoReportes = (SolicitudAprobada) eventos.getAllValues().get(1);
            assertThat(avisoReportes.solicitud()).isEqualTo(nueva);
            assertThat(avisoReportes.ocurridoEn()).isEqualTo(AHORA);
        }
    }

    @Test
    @DisplayName("revision manual solo cambia el estado: la decision final es de un asesor y no se avisa al solicitante")
    void revisionManual_noNotifica() {
        transaccionDejaPasar();
        var nueva = solicitud(EstadoSolicitud.REVISION_MANUAL);
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.PENDIENTE_REVISION)));
        when(solicitudRepository.actualizarEstado(nueva, EstadoSolicitud.PENDIENTE_REVISION)).thenReturn(Mono.just(nueva));

        StepVerifier.create(useCase.ejecutar(decision("REVISION_MANUAL"))).expectNext(nueva).verifyComplete();

        verifyNoInteractions(eventoSalienteRepository);
    }

    @ParameterizedTest(name = "estado actual = {0}")
    @EnumSource(value = EstadoSolicitud.class, names = {"REVISION_MANUAL", "APROBADA", "RECHAZADA"})
    @DisplayName("es idempotente: si la solicitud ya no esta pendiente, ignora la decision sin error ni aviso duplicado")
    void yaNoPendiente_seIgnora(EstadoSolicitud actual) {
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(actual)));

        StepVerifier.create(useCase.ejecutar(decision("APROBADA"))).verifyComplete();

        verify(solicitudRepository, never()).actualizarEstado(any(), any());
        verifyNoInteractions(eventoSalienteRepository, transaccionGateway);
    }

    @Test
    @DisplayName("si otro proceso la decidio entre la lectura y la escritura, no registra aviso")
    void carreraPerdida_noNotifica() {
        transaccionDejaPasar();
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.just(solicitud(EstadoSolicitud.PENDIENTE_REVISION)));
        when(solicitudRepository.actualizarEstado(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(decision("APROBADA"))).verifyComplete();

        verifyNoInteractions(eventoSalienteRepository);
    }

    @Test
    @DisplayName("si la solicitud no existe, falla para que el mensaje termine en la DLQ y se investigue")
    void noExiste_falla() {
        when(solicitudRepository.buscarPorId(ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(decision("APROBADA"))).expectError(SolicitudNoEncontradaException.class).verify();
    }

    @ParameterizedTest(name = "decision = [{0}]")
    @ValueSource(strings = {"PENDIENTE_REVISION", "INEXISTENTE", "", "  "})
    @DisplayName("una decision que no es APROBADA, RECHAZADA ni REVISION_MANUAL es invalida y no toca nada")
    void decisionInvalida(String estado) {
        StepVerifier.create(useCase.ejecutar(decision(estado))).expectError(DatosSolicitudInvalidosException.class).verify();

        verifyNoInteractions(solicitudRepository, eventoSalienteRepository);
    }

    @ParameterizedTest(name = "id = [{0}]")
    @ValueSource(strings = {"", "  ", "no-es-uuid"})
    @DisplayName("un id de solicitud que no es un UUID es invalido")
    void idInvalido(String id) {
        StepVerifier.create(useCase.ejecutar(new DecisionAutomaticaCommand(id, "APROBADA", null, null)))
                .expectError(DatosSolicitudInvalidosException.class).verify();
    }

    @Test
    @DisplayName("un id nulo es invalido")
    void idNulo() {
        StepVerifier.create(useCase.ejecutar(new DecisionAutomaticaCommand(null, "APROBADA", null, null)))
                .expectError(DatosSolicitudInvalidosException.class).verify();
    }

    @Test
    @DisplayName("el comando sin plan expone una lista vacia")
    void comandoSinPlan() {
        assertThat(new DecisionAutomaticaCommand(ID.toString(), "APROBADA", null, null).planDePago()).isEmpty();
    }
}
