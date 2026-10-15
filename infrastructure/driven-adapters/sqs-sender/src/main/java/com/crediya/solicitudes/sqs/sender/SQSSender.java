package com.crediya.solicitudes.sqs.sender;

import com.crediya.solicitudes.model.evento.EventoSaliente;
import com.crediya.solicitudes.model.evento.gateways.PublicadorDeEventos;
import com.crediya.solicitudes.sqs.sender.config.SQSSenderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@Log4j2
@RequiredArgsConstructor
public class SQSSender implements PublicadorDeEventos {
    private final SQSSenderProperties properties;
    private final SqsAsyncClient client;

    @Override
    public Mono<Void> publicar(EventoSaliente evento) {
        return Mono.fromCallable(() -> construirSolicitud(evento))
                .flatMap(solicitud -> Mono.fromFuture(client.sendMessage(solicitud)))
                .doOnNext(respuesta -> log.info("Evento {} publicado en {} messageId={}", evento.id(), evento.destino(), respuesta.messageId()))
                .then();
    }

    private SendMessageRequest construirSolicitud(EventoSaliente evento) {
        String cola = properties.colas().get(evento.destino().name().toLowerCase());
        if (cola == null || cola.isBlank()) {
            throw new IllegalStateException("No hay cola configurada para el destino " + evento.destino());
        }
        // El cuerpo no se registra: trae datos personales
        return SendMessageRequest.builder().queueUrl(cola).messageBody(evento.payload()).build();
    }
}
