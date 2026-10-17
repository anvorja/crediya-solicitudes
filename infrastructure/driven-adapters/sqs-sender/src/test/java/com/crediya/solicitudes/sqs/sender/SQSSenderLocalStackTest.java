package com.crediya.solicitudes.sqs.sender;

import com.crediya.solicitudes.model.evento.Destino;
import com.crediya.solicitudes.model.evento.EventoSaliente;
import com.crediya.solicitudes.sqs.sender.config.SQSSenderProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publica contra SQS real (LocalStack). Se fija la 4.9.2 porque las versiones posteriores de la imagen exigen un token de cuenta.
 */
@Tag("integration")
@Testcontainers
@DisplayName("SQSSender contra SQS real (LocalStack)")
class SQSSenderLocalStackTest {

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.9.2"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsAsyncClient sqs;
    private static String colaNotificaciones;

    @BeforeAll
    static void preparar() {
        sqs = SqsAsyncClient.builder().endpointOverride(LOCALSTACK.getEndpoint()).region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
        colaNotificaciones = sqs.createQueue(r -> r.queueName("crediya-notificaciones")).join().queueUrl();
    }

    @AfterAll
    static void cerrar() {
        sqs.close();
    }

    private static SQSSender emisor(Map<String, String> colas) {
        return new SQSSender(new SQSSenderProperties(LOCALSTACK.getRegion(), LOCALSTACK.getEndpoint().toString(), colas), sqs);
    }

    @Test
    @DisplayName("publicar entrega el payload tal cual en la cola configurada para el destino")
    void publicar_entregaElPayload() {
        var evento = new EventoSaliente(UUID.randomUUID(), Destino.NOTIFICACIONES, "{\"tipo\":\"SOLICITUD_DECIDIDA\"}", 0);

        StepVerifier.create(emisor(Map.of("notificaciones", colaNotificaciones)).publicar(evento)).verifyComplete();

        var mensajes = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(colaNotificaciones).waitTimeSeconds(10).build())
                .join().messages();
        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.get(0).body()).isEqualTo("{\"tipo\":\"SOLICITUD_DECIDIDA\"}");
    }

    @Test
    @DisplayName("publicar falla con un mensaje claro cuando el destino no tiene cola configurada")
    void sinColaConfigurada_falla() {
        var evento = new EventoSaliente(UUID.randomUUID(), Destino.NOTIFICACIONES, "{}", 0);

        StepVerifier.create(emisor(Map.of()).publicar(evento))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(IllegalStateException.class).hasMessageContaining("NOTIFICACIONES"))
                .verify();
    }

    @Test
    @DisplayName("publicar falla cuando la cola no existe, para que el evento se reintente")
    void colaInexistente_falla() {
        var evento = new EventoSaliente(UUID.randomUUID(), Destino.NOTIFICACIONES, "{}", 0);
        String inexistente = colaNotificaciones.replace("crediya-notificaciones", "no-existe");

        StepVerifier.create(emisor(Map.of("notificaciones", inexistente)).publicar(evento)).expectError().verify();
    }
}
