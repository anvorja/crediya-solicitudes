package com.crediya.solicitudes.sqs.listener;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.DecisionAutomaticaCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.usecase.procesardecision.ProcesarDecisionAutomaticaUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SQSProcessor")
class SQSProcessorTest {

    private static final String CUERPO = "{\"id_solicitud\":\"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11\",\"decision\":\"RECHAZADA\"}";

    @Mock
    private ProcesarDecisionAutomaticaUseCase useCase;

    private Message mensaje(String cuerpo) {
        return Message.builder().messageId("m1").body(cuerpo).build();
    }

    @Test
    @DisplayName("convierte el mensaje en comando, lo entrega al caso de uso y termina bien")
    void procesaMensaje() {
        var solicitud = new Solicitud(UUID.randomUUID(), BigDecimal.TEN, 12, "a@b.co", EstadoSolicitud.RECHAZADA,
                new TipoPrestamo(1L, "PERSONAL", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, true));
        when(useCase.ejecutar(any())).thenReturn(Mono.just(solicitud));

        StepVerifier.create(new SQSProcessor(useCase).apply(mensaje(CUERPO))).verifyComplete();

        var comando = ArgumentCaptor.forClass(DecisionAutomaticaCommand.class);
        verify(useCase).ejecutar(comando.capture());
        assertThat(comando.getValue().decision()).isEqualTo("RECHAZADA");
    }

    @Test
    @DisplayName("cuando el caso de uso ignora la decision (vacio), termina bien para que el mensaje se borre")
    void decisionIgnorada() {
        when(useCase.ejecutar(any())).thenReturn(Mono.empty());

        StepVerifier.create(new SQSProcessor(useCase).apply(mensaje(CUERPO))).verifyComplete();
    }

    @Test
    @DisplayName("un mensaje invalido falla sin llegar al caso de uso, para que vaya a reintento y luego a la DLQ")
    void mensajeInvalido() {
        StepVerifier.create(new SQSProcessor(useCase).apply(mensaje("basura"))).expectError(IllegalArgumentException.class).verify();

        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("un error del caso de uso se propaga")
    void errorDelCasoDeUso() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new SolicitudNoEncontradaException()));

        StepVerifier.create(new SQSProcessor(useCase).apply(mensaje(CUERPO))).expectError(SolicitudNoEncontradaException.class).verify();
    }
}
