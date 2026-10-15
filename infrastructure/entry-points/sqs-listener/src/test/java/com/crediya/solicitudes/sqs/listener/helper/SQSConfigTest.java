package com.crediya.solicitudes.sqs.listener.helper;

import com.crediya.solicitudes.sqs.listener.config.SQSConfig;
import com.crediya.solicitudes.sqs.listener.config.SQSProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("SQSConfig")
class SQSConfigTest {

    @Test
    @DisplayName("crea el listener con el cliente compartido y se puede detener")
    void creaListener() {
        var cliente = Mockito.mock(SqsAsyncClient.class);
        when(cliente.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build()));
        var propiedades = new SQSProperties(true, "http://localhost:4566/000000000000/cola", 0, 30, 10, 1);

        var listener = new SQSConfig().sqsListener(cliente, propiedades, mensaje -> Mono.empty());

        assertThat(listener).isNotNull();
        listener.stop();
    }
}
