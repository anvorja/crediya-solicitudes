package com.crediya.solicitudes.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * HU7 de extremo a extremo desde SOLICITUDES: la aplicacion completa + PostgreSQL + SQS real (LocalStack). La Lambda de capacidad
 * de endeudamiento se reemplaza por el propio test, que lee la cola de validacion y responde en la de decisiones.
 */
@Tag("integration")
@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("HU7 - Validacion automatica de solicitudes (aplicacion completa)")
class ValidacionAutomaticaIntegrationTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String JSON_MARIA = """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","correo_electronico":"maria@example.com",
             "salario_base":4500000,"rol":"CLIENTE"}""";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.9.2"))
            .withServices(LocalStackContainer.Service.SQS);
    static final MockWebServer AUTENTICACION = new MockWebServer();
    static final RSAKey CLAVE = generarClave();

    static {
        AUTENTICACION.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest peticion) {
                String ruta = peticion.getPath();
                if (ruta.equals("/.well-known/jwks.json")) {
                    return json(new JWKSet(CLAVE.toPublicJWK()).toString());
                }
                if (ruta.endsWith("/1020304050")) {
                    return json(JSON_MARIA);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        try {
            AUTENTICACION.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockResponse json(String cuerpo) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(cuerpo);
    }

    private static RSAKey generarClave() {
        try {
            return new RSAKeyGenerator(2048).keyID("k1").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static SqsAsyncClient sqs;
    private static String colaValidacion;
    private static String colaDecision;
    private static String colaNotificaciones;
    private static String colaReportes;
    private static String dlqDecision;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
        sqs = SqsAsyncClient.builder().endpointOverride(LOCALSTACK.getEndpoint()).region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
        colaValidacion = sqs.createQueue(r -> r.queueName("crediya-validacion-automatica")).join().queueUrl();
        colaNotificaciones = sqs.createQueue(r -> r.queueName("crediya-notificaciones")).join().queueUrl();
        colaReportes = sqs.createQueue(r -> r.queueName("crediya-solicitud-aprobada")).join().queueUrl();
        dlqDecision = sqs.createQueue(r -> r.queueName("crediya-decision-automatica-dlq")).join().queueUrl();
        String arnDlq = sqs.getQueueAttributes(r -> r.queueUrl(dlqDecision).attributeNamesWithStrings("QueueArn")).join()
                .attributesAsStrings().get("QueueArn");
        colaDecision = sqs.createQueue(r -> r.queueName("crediya-decision-automatica").attributesWithStrings(java.util.Map.of(
                "VisibilityTimeout", "2",
                "RedrivePolicy", "{\"deadLetterTargetArn\":\"" + arnDlq + "\",\"maxReceiveCount\":\"2\"}"))).join().queueUrl();

        registro.add("DB_HOST", POSTGRES::getHost);
        registro.add("DB_PORT", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registro.add("DB_NAME", POSTGRES::getDatabaseName);
        registro.add("DB_USERNAME", POSTGRES::getUsername);
        registro.add("DB_PASSWORD", POSTGRES::getPassword);
        registro.add("AUTH_SERVICE_URL", () -> AUTENTICACION.url("/").toString());
        registro.add("AUTH_JWKS_URI", () -> AUTENTICACION.url("/.well-known/jwks.json").toString());
        registro.add("AWS_REGION", LOCALSTACK::getRegion);
        registro.add("AWS_ENDPOINT_URL", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("SQS_NOTIFICACIONES_URL", () -> colaNotificaciones);
        registro.add("SQS_REPORTES_URL", () -> colaReportes);
        registro.add("SQS_VALIDACION_URL", () -> colaValidacion);
        registro.add("SQS_DECISION_URL", () -> colaDecision);
        registro.add("OUTBOX_INTERVALO", () -> "300ms");
        // El listener pide su propio tiempo de visibilidad: se acorta para que los reintentos y la DLQ ocurran dentro de la prueba
        registro.add("entrypoint.sqs.visibilityTimeoutSeconds", () -> "2");
    }

    @AfterAll
    static void cerrar() throws IOException {
        AUTENTICACION.shutdown();
        sqs.close();
    }

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient db;

    @BeforeEach
    void limpiar() {
        db.sql("TRUNCATE TABLE solicitud").then().block();
        db.sql("TRUNCATE TABLE evento_saliente").then().block();
        for (String cola : List.of(colaValidacion, colaNotificaciones, colaReportes, dlqDecision)) {
            sqs.purgeQueue(r -> r.queueUrl(cola)).join();
        }
    }

    // ---------- utilidades ----------

    private static String tokenCliente() {
        try {
            var claims = new JWTClaimsSet.Builder().issuer("crediya-auth").audience("crediya").subject(UUID.randomUUID().toString())
                    .claim("documento_identidad", "1020304050").claim("email", "maria@example.com").claim("rol", "CLIENTE")
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60_000));
            var firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CLAVE.getKeyID()).build(), claims.build());
            firmado.sign(new RSASSASigner(CLAVE));
            return firmado.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String crear(String tipo, int monto) {
        return client.post().uri(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"documento_identidad\":\"1020304050\",\"monto\":%d,\"plazo\":24,\"tipo_prestamo\":\"%s\"}".formatted(monto, tipo))
                .exchange().expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody()
                .get("id_solicitud").asText();
    }

    private String estadoEnBd(String id) {
        return db.sql("SELECT e.nombre FROM solicitud s JOIN estados e USING (id_estado) WHERE s.id_solicitud = :id")
                .bind("id", UUID.fromString(id)).map((fila, m) -> fila.get("nombre", String.class)).one().block();
    }

    private List<Message> recibir(String cola, int segundos) {
        return sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(cola).waitTimeSeconds(segundos).maxNumberOfMessages(10).build())
                .join().messages();
    }

    private void responderDecision(String cuerpo) {
        sqs.sendMessage(r -> r.queueUrl(colaDecision).messageBody(cuerpo)).join();
    }

    private static String decision(String id, String decision) {
        return """
                {"version":1,"id_evento":"%s","tipo":"DECISION_AUTOMATICA","id_solicitud":"%s","decision":"%s",
                 "destinatario":{"email":"maria@example.com","nombre":"Maria Gomez"},
                 "plan_de_pago":[{"numero":1,"cuota":258404.18,"capital":168404.18,"interes":90000.00,"saldo":4831595.82}]}"""
                .formatted(UUID.randomUUID(), id, decision);
    }

    // ---------- pruebas ----------

    @Test
    @DisplayName("un tipo con validacion automatica publica el pedido de evaluacion con el salario y el nombre del solicitante")
    void tipoAutomatico_publicaPedido() throws Exception {
        String id = crear("PERSONAL", 5_000_000);

        var mensajes = recibir(colaValidacion, 15);

        assertThat(mensajes).hasSize(1);
        JsonNode pedido = JSON.readTree(mensajes.get(0).body());
        assertThat(pedido.get("tipo").asText()).isEqualTo("VALIDACION_AUTOMATICA_SOLICITADA");
        assertThat(pedido.at("/solicitud/id_solicitud").asText()).isEqualTo(id);
        assertThat(pedido.at("/solicitud/tasa_interes").decimalValue()).isEqualByComparingTo("0.018");
        assertThat(pedido.at("/solicitante/email").asText()).isEqualTo("maria@example.com");
        assertThat(pedido.at("/solicitante/nombre").asText()).isEqualTo("Maria Gomez");
        assertThat(pedido.at("/solicitante/salario_base").decimalValue()).isEqualByComparingTo("4500000");
        assertThat(pedido.at("/prestamos_aprobados")).isEmpty();
        assertThat(estadoEnBd(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("un tipo sin validacion automatica queda pendiente y no publica nada")
    void tipoManual_noPublica() {
        String id = crear("VEHICULO", 20_000_000);

        assertThat(recibir(colaValidacion, 3)).isEmpty();
        assertThat(estadoEnBd(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("la decision APROBADA cambia el estado y notifica al solicitante con su plan de pago")
    void decisionAprobada_notificaConPlan() throws Exception {
        String id = crear("PERSONAL", 5_000_000);

        responderDecision(decision(id, "APROBADA"));

        await().atMost(Duration.ofSeconds(20)).until(() -> estadoEnBd(id).equals("APROBADA"));
        var mensajes = recibir(colaNotificaciones, 15);
        assertThat(mensajes).hasSize(1);
        JsonNode evento = JSON.readTree(mensajes.get(0).body());
        assertThat(evento.get("tipo").asText()).isEqualTo("SOLICITUD_DECIDIDA");
        assertThat(evento.at("/solicitud/estado").asText()).isEqualTo("APROBADA");
        assertThat(evento.at("/destinatario/nombre").asText()).isEqualTo("Maria Gomez");
        assertThat(evento.at("/plan_de_pago/0/capital").decimalValue()).isEqualByComparingTo("168404.18");
    }

    @Test
    @DisplayName("la decision RECHAZADA notifica y REVISION_MANUAL solo cambia el estado")
    void rechazadaNotifica_revisionManualNo() throws Exception {
        String rechazada = crear("PERSONAL", 5_000_000);
        String manual = crear("PERSONAL", 6_000_000);

        responderDecision(decision(rechazada, "RECHAZADA"));
        responderDecision(decision(manual, "REVISION_MANUAL"));

        await().atMost(Duration.ofSeconds(20)).until(() -> estadoEnBd(rechazada).equals("RECHAZADA") && estadoEnBd(manual).equals("REVISION_MANUAL"));
        var mensajes = recibir(colaNotificaciones, 10);
        assertThat(mensajes).hasSize(1);
        assertThat(JSON.readTree(mensajes.get(0).body()).at("/solicitud/id_solicitud").asText()).isEqualTo(rechazada);
    }

    @Test
    @DisplayName("es idempotente: la misma decision entregada dos veces no duplica el aviso ni cambia nada")
    void decisionDuplicada_seIgnora() {
        String id = crear("PERSONAL", 5_000_000);

        responderDecision(decision(id, "APROBADA"));
        responderDecision(decision(id, "RECHAZADA"));

        await().atMost(Duration.ofSeconds(20)).until(() -> estadoEnBd(id).equals("APROBADA"));
        assertThat(recibir(colaNotificaciones, 8)).hasSize(1);
        assertThat(estadoEnBd(id)).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("un mensaje invalido no se pierde ni bloquea la cola: tras los reintentos va a la DLQ y los demas siguen")
    void mensajeInvalido_vaALaDlq() {
        String id = crear("PERSONAL", 5_000_000);

        responderDecision("esto no es json");
        responderDecision(decision(id, "APROBADA"));

        await().atMost(Duration.ofSeconds(20)).until(() -> estadoEnBd(id).equals("APROBADA"));
        var enDlq = await().atMost(Duration.ofSeconds(30)).until(() -> recibir(dlqDecision, 2), mensajes -> !mensajes.isEmpty());
        assertThat(enDlq.get(0).body()).isEqualTo("esto no es json");
    }
}
