package com.crediya.solicitudes.usecase.publicareventos;

import com.crediya.solicitudes.model.common.gateways.TransaccionGateway;
import com.crediya.solicitudes.model.evento.Destino;
import com.crediya.solicitudes.model.evento.EventoSaliente;
import com.crediya.solicitudes.model.evento.gateways.EventoSalienteRepository;
import com.crediya.solicitudes.model.evento.gateways.PublicadorDeEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicarEventosPendientesUseCase")
class PublicarEventosPendientesUseCaseTest {

    @Mock
    private EventoSalienteRepository repository;
    @Mock
    private PublicadorDeEventos publicador;
    @Mock
    private TransaccionGateway transaccionGateway;

    private PublicarEventosPendientesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublicarEventosPendientesUseCase(repository, publicador, transaccionGateway);
        when(transaccionGateway.ejecutar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static EventoSaliente evento() {
        return new EventoSaliente(UUID.randomUUID(), Destino.NOTIFICACIONES, "{}", 0);
    }

    @Test
    @DisplayName("publica cada evento pendiente y lo marca como enviado")
    void publicaYMarca() {
        var a = evento();
        var b = evento();
        when(repository.tomarPendientes(10)).thenReturn(Flux.just(a, b));
        when(publicador.publicar(any())).thenReturn(Mono.empty());
        when(repository.marcarEnviado(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(10)).expectNext(2L).verifyComplete();

        verify(repository).marcarEnviado(a.id());
        verify(repository).marcarEnviado(b.id());
    }

    @Test
    @DisplayName("cuando no hay pendientes, responde 0")
    void sinPendientes() {
        when(repository.tomarPendientes(5)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.ejecutar(5)).expectNext(0L).verifyComplete();
    }

    @Test
    @DisplayName("cuando un evento falla, lo registra como fallo, no lo marca enviado y sigue con los demas")
    void fallaUnoSigueConLosDemas() {
        var malo = evento();
        var bueno = evento();
        when(repository.tomarPendientes(10)).thenReturn(Flux.just(malo, bueno));
        when(publicador.publicar(malo)).thenReturn(Mono.error(new IllegalStateException("cola caida")));
        when(publicador.publicar(bueno)).thenReturn(Mono.empty());
        when(repository.registrarFallo(any(), any())).thenReturn(Mono.empty());
        when(repository.marcarEnviado(bueno.id())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(10)).expectNext(1L).verifyComplete();

        var motivo = ArgumentCaptor.forClass(String.class);
        verify(repository).registrarFallo(eq(malo.id()), motivo.capture());
        assertThat(motivo.getValue()).contains("IllegalStateException").contains("cola caida");
        verify(repository, never()).marcarEnviado(malo.id());
    }

    @Test
    @DisplayName("el motivo del fallo se recorta a 300 caracteres")
    void motivoLargoSeRecorta() {
        var malo = evento();
        when(repository.tomarPendientes(1)).thenReturn(Flux.just(malo));
        when(publicador.publicar(malo)).thenReturn(Mono.error(new IllegalStateException("x".repeat(1000))));
        when(repository.registrarFallo(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.ejecutar(1)).expectNext(0L).verifyComplete();

        var motivo = ArgumentCaptor.forClass(String.class);
        verify(repository).registrarFallo(eq(malo.id()), motivo.capture());
        assertThat(motivo.getValue()).hasSize(300);
    }
}
