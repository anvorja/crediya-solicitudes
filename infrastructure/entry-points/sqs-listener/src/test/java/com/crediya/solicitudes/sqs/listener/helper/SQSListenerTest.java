package com.crediya.solicitudes.sqs.listener.helper;

import com.crediya.solicitudes.sqs.listener.config.SQSProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SQSListener")
class SQSListenerTest {

    private static final SQSProperties PROPIEDADES = new SQSProperties(true, "http://localhost:4566/000000000000/cola", 0, 30, 10, 1);

    @Mock
    private SqsAsyncClient client;

    private SQSListener listener;

    private void borradoExitoso() {
        when(client.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
    }

    @AfterEach
    void detener() {
        if (listener != null) {
            listener.stop();
        }
    }

    private static CompletableFuture<ReceiveMessageResponse> con(Message... mensajes) {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(mensajes).build());
    }

    @Test
    @DisplayName("un mensaje procesado bien se borra de la cola")
    void procesadoBien_seBorra() {
        borradoExitoso();
        var recibido = Message.builder().messageId("m1").receiptHandle("h1").body("ok").build();
        var entregado = new AtomicInteger();
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(inv -> entregado.getAndIncrement() == 0 ? con(recibido) : con());
        var cuerpos = new CopyOnWriteArrayList<String>();

        listener = SQSListener.builder().client(client).properties(PROPIEDADES)
                .processor(m -> Mono.fromRunnable(() -> cuerpos.add(m.body()))).build().start();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(client, atLeastOnce())
                .deleteMessage(DeleteMessageRequest.builder().queueUrl(PROPIEDADES.queueUrl()).receiptHandle("h1").build()));
        assertThat(cuerpos).containsExactly("ok");
    }

    @Test
    @DisplayName("un mensaje cuyo procesamiento falla NO se borra: vuelve a la cola para reintento y el bucle sigue vivo")
    void procesadoMal_noSeBorra() {
        borradoExitoso();
        var malo = Message.builder().messageId("m1").receiptHandle("malo").body("x").build();
        var bueno = Message.builder().messageId("m2").receiptHandle("bueno").body("y").build();
        var entregado = new AtomicInteger();
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(inv -> switch (entregado.getAndIncrement()) {
            case 0 -> con(malo);
            case 1 -> con(bueno);
            default -> con();
        });

        listener = SQSListener.builder().client(client).properties(PROPIEDADES)
                .processor(m -> m.body().equals("x") ? Mono.error(new IllegalStateException("fallo")) : Mono.empty()).build().start();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(client).deleteMessage(
                DeleteMessageRequest.builder().queueUrl(PROPIEDADES.queueUrl()).receiptHandle("bueno").build()));
        verify(client, never()).deleteMessage(DeleteMessageRequest.builder().queueUrl(PROPIEDADES.queueUrl()).receiptHandle("malo").build());
    }

    @Test
    @DisplayName("si SQS falla al recibir, el bucle reintenta en vez de morir y luego procesa con normalidad")
    void errorAlRecibir_reintenta() {
        borradoExitoso();
        var recibido = Message.builder().messageId("m1").receiptHandle("h1").body("ok").build();
        var llamadas = new AtomicInteger();
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(inv -> switch (llamadas.getAndIncrement()) {
            case 0 -> CompletableFuture.<ReceiveMessageResponse>failedFuture(new IllegalStateException("cola no disponible"));
            case 1 -> con(recibido);
            default -> con();
        });
        List<String> procesados = new CopyOnWriteArrayList<>();

        listener = SQSListener.builder().client(client).properties(PROPIEDADES)
                .processor(m -> Mono.fromRunnable(() -> procesados.add(m.body()))).build().start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(procesados).containsExactly("ok"));
    }

    @Test
    @DisplayName("stop detiene el bucle: despues de detenerlo no se vuelve a consultar la cola")
    void stop_detieneElBucle() throws Exception {
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(inv -> {
            Thread.sleep(20);
            return con();
        });
        listener = SQSListener.builder().client(client).properties(PROPIEDADES).processor(m -> Mono.empty()).build().start();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(client, atLeastOnce()).receiveMessage(any(ReceiveMessageRequest.class)));

        listener.stop();
        Thread.sleep(100);
        int despues = org.mockito.Mockito.mockingDetails(client).getInvocations().size();
        Thread.sleep(300);

        assertThat(org.mockito.Mockito.mockingDetails(client).getInvocations()).hasSize(despues);
    }
}
