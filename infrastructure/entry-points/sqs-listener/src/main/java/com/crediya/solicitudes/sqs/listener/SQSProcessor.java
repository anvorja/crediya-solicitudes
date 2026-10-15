package com.crediya.solicitudes.sqs.listener;

import com.crediya.solicitudes.usecase.procesardecision.ProcesarDecisionAutomaticaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

/**
 * Procesa los mensajes de la cola de decisiones automaticas (HU7). Un mensaje que no cumple el contrato falla y, tras los
 * reintentos de la cola, termina en su DLQ. El contenido no se registra en logs: trae datos personales.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SQSProcessor implements Function<Message, Mono<Void>> {

    private final ProcesarDecisionAutomaticaUseCase procesarDecision;

    @Override
    public Mono<Void> apply(Message message) {
        return Mono.fromCallable(() -> DecisionAutomaticaMensaje.aComando(message.body()))
                .flatMap(procesarDecision::ejecutar)
                .doOnNext(solicitud -> log.info("Decision automatica aplicada solicitud={} estado={}", solicitud.id(), solicitud.estado()))
                .switchIfEmpty(Mono.fromRunnable(() -> log.info("Decision automatica ignorada: la solicitud ya no esta pendiente messageId={}", message.messageId())))
                .then();
    }
}
