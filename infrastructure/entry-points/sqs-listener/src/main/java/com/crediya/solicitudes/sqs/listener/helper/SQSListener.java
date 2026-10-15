package com.crediya.solicitudes.sqs.listener.helper;

import com.crediya.solicitudes.sqs.listener.config.SQSProperties;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.util.function.Function;

/**
 * Escucha una cola con long polling. Un mensaje se borra solo si se proceso bien: si falla vuelve a la cola cuando vence su
 * tiempo de visibilidad y, tras agotar los intentos, la propia cola lo manda a su DLQ. Si SQS no responde (red caida, cola
 * inexistente) el bucle reintenta con espera creciente en vez de morir.
 */
@Log4j2
@Builder
public class SQSListener {
    private final SqsAsyncClient client;
    private final SQSProperties properties;
    private final Function<Message, Mono<Void>> processor;
    private final Disposable.Composite suscripciones = Disposables.composite();
    private Scheduler scheduler;

    public SQSListener start() {
        scheduler = Schedulers.newBoundedElastic(Math.max(properties.numberOfThreads(), 1) * 2, 100, "sqs-listener");
        for (var i = 0; i < properties.numberOfThreads(); i++) {
            suscripciones.add(bucle().subscribeOn(scheduler).subscribe());
        }
        log.info("Escuchando {} con {} hilo(s)", properties.queueUrl(), properties.numberOfThreads());
        return this;
    }

    public void stop() {
        suscripciones.dispose();
        if (scheduler != null) {
            scheduler.dispose();
        }
    }

    private Flux<Void> bucle() {
        return listen()
                .repeat()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(senal -> log.error("Error escuchando la cola, se reintenta: {}", senal.failure().toString())));
    }

    private Flux<Void> listen() {
        return getMessages()
                .flatMap(message -> processor.apply(message)
                        .name("async_operation")
                        .tag("operation", properties.queueUrl())
                        .metrics()
                        .then(confirm(message))
                        // El mensaje fallido no se borra: vuelve a la cola y la politica de reintentos decide
                        .onErrorResume(e -> {
                            log.error("Error procesando el mensaje {} de la cola, quedara para reintento: {}", message.messageId(), e.toString());
                            return Mono.empty();
                        }));
    }

    private Mono<Void> confirm(Message message) {
        return Mono.fromCallable(() -> getDeleteMessageRequest(message.receiptHandle()))
                .flatMap(request -> Mono.fromFuture(client.deleteMessage(request)))
                .then();
    }

    private Flux<Message> getMessages() {
        return Mono.fromCallable(this::getReceiveMessageRequest)
                .flatMap(request -> Mono.fromFuture(client.receiveMessage(request)))
                .doOnNext(response -> log.debug("{} mensajes recibidos de la cola", response.messages().size()))
                .flatMapMany(response -> Flux.fromIterable(response.messages()));
    }

    private ReceiveMessageRequest getReceiveMessageRequest() {
        return ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxNumberOfMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .visibilityTimeout(properties.visibilityTimeoutSeconds())
                .build();
    }

    private DeleteMessageRequest getDeleteMessageRequest(String receiptHandle) {
        return DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(receiptHandle)
                .build();
    }
}
