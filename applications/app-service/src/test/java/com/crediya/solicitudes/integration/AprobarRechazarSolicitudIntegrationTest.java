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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU6 de extremo a extremo: aplicacion completa + PostgreSQL real + SQS real (LocalStack) + AUTENTICACION simulado por HTTP.
 * Verifica el cambio de estado, el outbox y la entrega del evento a la cola de notificaciones.
 */
@Tag("integration")
@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("HU6 - Aprobar o rechazar solicitudes y notificar (aplicacion completa)")
class AprobarRechazarSolicitudIntegrationTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String COLA = "crediya-notificaciones";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String JSON_MARIA = """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","correo_electronico":"maria@example.com",
             "salario_base":4500000,"rol":"CLIENTE"}""";
    private static final Map<String, String> CLIENTES_POR_EMAIL = Map.of("maria@example.com", JSON_MARIA);

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
                if (ruta.startsWith("/api/v1/usuarios?")) {
                    String correos = java.net.URLDecoder.decode(ruta.substring(ruta.indexOf("correos=") + 8), StandardCharsets.UTF_8);
                    return json(Arrays.stream(correos.split(",")).map(CLIENTES_POR_EMAIL::get).filter(Objects::nonNull)
                            .collect(Collectors.joining(",", "[", "]")));
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
    private static String urlCola;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        // El SDK de la aplicacion toma las credenciales de las propiedades del sistema (no hay nada real en el codigo)
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
        sqs = SqsAsyncClient.builder().endpointOverride(LOCALSTACK.getEndpoint()).region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
        urlCola = sqs.createQueue(r -> r.queueName(COLA)).join().queueUrl();

        registro.add("DB_HOST", POSTGRES::getHost);
        registro.add("DB_PORT", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registro.add("DB_NAME", POSTGRES::getDatabaseName);
        registro.add("DB_USERNAME", POSTGRES::getUsername);
        registro.add("DB_PASSWORD", POSTGRES::getPassword);
        registro.add("AUTH_SERVICE_URL", () -> AUTENTICACION.url("/").toString());
        registro.add("AUTH_JWKS_URI", () -> AUTENTICACION.url("/.well-known/jwks.json").toString());
        registro.add("AWS_REGION", LOCALSTACK::getRegion);
        registro.add("AWS_ENDPOINT_URL", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("SQS_NOTIFICACIONES_URL", () -> urlCola);
        registro.add("OUTBOX_INTERVALO", () -> "300ms");
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
        vaciarCola();
    }

    // ---------- utilidades ----------

    private static void vaciarCola() {
        sqs.purgeQueue(r -> r.queueUrl(urlCola)).join();
    }

    private static String token(String rol) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer("crediya-auth").audience("crediya").subject(UUID.randomUUID().toString())
                    .claim("documento_identidad", "9999999").claim("email", "asesor@crediya.test").claim("rol", rol)
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60_000));
            var firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CLAVE.getKeyID()).build(), claims.build());
            firmado.sign(new RSASSASigner(CLAVE));
            return firmado.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID solicitud(String email, int idEstado) {
        return db.sql("INSERT INTO solicitud (monto, plazo, email, id_estado, id_tipo_prestamo) VALUES (5000000, 24, :e, :s, 1) RETURNING id_solicitud")
                .bind("e", email).bind("s", (short) idEstado)
                .map((fila, m) -> fila.get("id_solicitud", UUID.class)).one().block();
    }

    private WebTestClient.ResponseSpec decidir(String token, Object id, String estado) {
        var peticion = client.put().uri(RUTA).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return peticion.bodyValue("{\"id_solicitud\":\"%s\",\"estado\":\"%s\"}".formatted(id, estado)).exchange();
    }

    private String estadoEnBd(UUID id) {
        return db.sql("SELECT e.nombre FROM solicitud s JOIN estados e USING (id_estado) WHERE s.id_solicitud = :id")
                .bind("id", id).map((fila, m) -> fila.get("nombre", String.class)).one().block();
    }

    private long eventosPendientes() {
        return db.sql("SELECT count(*) AS n FROM evento_saliente WHERE enviado_en IS NULL")
                .map((fila, m) -> fila.get("n", Long.class)).one().block();
    }

    private List<Message> recibirMensajes(int segundos) {
        return sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(urlCola).waitTimeSeconds(segundos).maxNumberOfMessages(10).build())
                .join().messages();
    }

    // ---------- pruebas ----------

    @Test
    @DisplayName("un ASESOR aprueba: 200, estado guardado y el evento SOLICITUD_DECIDIDA llega a la cola con el contrato v1")
    void asesorAprueba_guardaYPublica() throws Exception {
        UUID id = solicitud("maria@example.com", 1);

        decidir(token("ASESOR"), id, "aprobada")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id_solicitud").isEqualTo(id.toString())
                .jsonPath("$.estado").isEqualTo("APROBADA");

        assertThat(estadoEnBd(id)).isEqualTo("APROBADA");
        var mensajes = recibirMensajes(15);
        assertThat(mensajes).hasSize(1);
        JsonNode evento = JSON.readTree(mensajes.get(0).body());
        assertThat(evento.get("version").asInt()).isEqualTo(1);
        assertThat(evento.get("tipo").asText()).isEqualTo("SOLICITUD_DECIDIDA");
        assertThat(evento.get("id_evento").asText()).isNotBlank();
        assertThat(evento.at("/destinatario/email").asText()).isEqualTo("maria@example.com");
        assertThat(evento.at("/destinatario/nombre").asText()).isEqualTo("Maria Gomez");
        assertThat(evento.at("/solicitud/id_solicitud").asText()).isEqualTo(id.toString());
        assertThat(evento.at("/solicitud/estado").asText()).isEqualTo("APROBADA");
        assertThat(evento.at("/solicitud/tipo_prestamo").asText()).isEqualTo("PERSONAL");
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> eventosPendientes() == 0);
    }

    @Test
    @DisplayName("un ASESOR rechaza: 200 y el evento lleva estado RECHAZADA")
    void asesorRechaza() throws Exception {
        UUID id = solicitud("maria@example.com", 2);

        decidir(token("ASESOR"), id, "RECHAZADA").expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("RECHAZADA");

        var mensajes = recibirMensajes(15);
        assertThat(mensajes).hasSize(1);
        assertThat(JSON.readTree(mensajes.get(0).body()).at("/solicitud/estado").asText()).isEqualTo("RECHAZADA");
    }

    @Test
    @DisplayName("una solicitud rechazada se puede reconsiderar y aprobar, pero una aprobada ya no cambia (409)")
    void transiciones() {
        UUID rechazada = solicitud("maria@example.com", 4);
        UUID aprobada = solicitud("maria@example.com", 3);

        decidir(token("ASESOR"), rechazada, "APROBADA").expectStatus().isOk();
        decidir(token("ASESOR"), aprobada, "RECHAZADA").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("TRANSICION_ESTADO_INVALIDA");

        assertThat(estadoEnBd(aprobada)).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("una transicion rechazada no deja ningun evento")
    void transicionInvalida_noDejaEvento() {
        UUID aprobada = solicitud("maria@example.com", 3);

        decidir(token("ASESOR"), aprobada, "RECHAZADA").expectStatus().isEqualTo(409);

        Long total = db.sql("SELECT count(*) AS n FROM evento_saliente").map((f, m) -> f.get("n", Long.class)).one().block();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("solo un ASESOR decide: CLIENTE y ADMIN reciben 403 y sin token 401; nada cambia")
    void soloAsesor() {
        UUID id = solicitud("maria@example.com", 1);

        decidir(token("CLIENTE"), id, "APROBADA").expectStatus().isForbidden();
        decidir(token("ADMIN"), id, "APROBADA").expectStatus().isForbidden();
        decidir(null, id, "APROBADA").expectStatus().isUnauthorized();

        assertThat(estadoEnBd(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("valida la entrada: solicitud inexistente 404, estado o id invalidos 400")
    void validaEntrada() {
        UUID id = solicitud("maria@example.com", 1);

        decidir(token("ASESOR"), UUID.randomUUID(), "APROBADA").expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("SOLICITUD_NO_ENCONTRADA");
        decidir(token("ASESOR"), id, "REVISION_MANUAL").expectStatus().isBadRequest();
        decidir(token("ASESOR"), id, "NOPE").expectStatus().isBadRequest();
        decidir(token("ASESOR"), "no-es-uuid", "APROBADA").expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("si la cola no existe, la decision igual se guarda y el evento queda pendiente; al volver la cola se entrega")
    void colaCaida_eventoQuedaPendienteYSeEntregaDespues() throws Exception {
        UUID id = solicitud("maria@example.com", 1);
        sqs.deleteQueue(r -> r.queueUrl(urlCola)).join();

        decidir(token("ASESOR"), id, "APROBADA").expectStatus().isOk();

        assertThat(estadoEnBd(id)).isEqualTo("APROBADA");
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                db.sql("SELECT intentos FROM evento_saliente WHERE enviado_en IS NULL").map((f, m) -> f.get("intentos", Integer.class)).one().block() >= 1);
        assertThat(db.sql("SELECT ultimo_error FROM evento_saliente").map((f, m) -> f.get("ultimo_error", String.class)).one().block()).isNotBlank();

        sqs.createQueue(r -> r.queueName(COLA)).join();

        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> eventosPendientes() == 0);
        var mensajes = recibirMensajes(10);
        assertThat(mensajes).hasSize(1);
        assertThat(JSON.readTree(mensajes.get(0).body()).at("/solicitud/id_solicitud").asText()).isEqualTo(id.toString());
    }
}
