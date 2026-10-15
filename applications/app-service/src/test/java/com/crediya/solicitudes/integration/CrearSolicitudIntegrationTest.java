package com.crediya.solicitudes.integration;

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
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU2 de extremo a extremo: aplicacion completa + PostgreSQL real + AUTENTICACION simulado por HTTP real.
 */
@Tag("integration")
@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("HU2 - Registrar una solicitud de prestamo (aplicacion completa)")
class CrearSolicitudIntegrationTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String DOCUMENTO_CON_FALLA = "5000000000";
    private static final Map<String, String> CLIENTES = Map.of("1020304050", """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","correo_electronico":"maria@example.com",
             "salario_base":4500000,"rol":"CLIENTE"}""");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final MockWebServer AUTENTICACION = new MockWebServer();

    static {
        AUTENTICACION.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest peticion) {
                String documento = peticion.getPath().substring(peticion.getPath().lastIndexOf('/') + 1);
                if (DOCUMENTO_CON_FALLA.equals(documento)) {
                    return new MockResponse().setResponseCode(500);
                }
                String cuerpo = CLIENTES.get(documento);
                if (cuerpo == null) {
                    return new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("{}");
                }
                return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(cuerpo);
            }
        });
        try {
            AUTENTICACION.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar el servidor simulado de autenticacion", e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("DB_HOST", POSTGRES::getHost);
        registro.add("DB_PORT", () -> POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        registro.add("DB_NAME", POSTGRES::getDatabaseName);
        registro.add("DB_USERNAME", POSTGRES::getUsername);
        registro.add("DB_PASSWORD", POSTGRES::getPassword);
        registro.add("AUTH_SERVICE_URL", () -> AUTENTICACION.url("/").toString());
    }

    @AfterAll
    static void detenerServidor() throws IOException {
        AUTENTICACION.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void limpiar() {
        databaseClient.sql("TRUNCATE TABLE solicitud").then().block();
    }

    private WebTestClient.ResponseSpec crear(String documento, String monto, int plazo, String tipo) {
        String cuerpo = """
                {"documento_identidad":"%s","monto":%s,"plazo":%d,"tipo_prestamo":"%s"}""".formatted(documento, monto, plazo, tipo);
        return client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo).exchange();
    }

    private Long contarSolicitudes() {
        return databaseClient.sql("SELECT count(*) AS total FROM solicitud")
                .map((fila, metadatos) -> fila.get("total", Long.class)).one().block();
    }

    @Test
    @DisplayName("cuando el cliente y el tipo existen, debe responder 201, quedar Pendiente de revision y guardar el email del cliente")
    void cuandoTodoValido_debeResponder201YPersistir() {
        crear("1020304050", "5000000", 24, "personal")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_solicitud").isNotEmpty()
                .jsonPath("$.estado").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.email").isEqualTo("maria@example.com")
                .jsonPath("$.tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.tasa_interes").isEqualTo(0.018);

        var fila = databaseClient.sql("""
                        SELECT e.nombre AS estado, s.email, s.plazo FROM solicitud s JOIN estados e USING (id_estado)""")
                .map((r, m) -> r.get("estado", String.class) + "|" + r.get("email", String.class) + "|" + r.get("plazo", Integer.class))
                .one().block();
        assertThat(fila).isEqualTo("PENDIENTE_REVISION|maria@example.com|24");
    }

    @Test
    @DisplayName("cuando el cliente no esta registrado, debe responder 404 y no guardar nada")
    void cuandoClienteNoExiste_debeResponder404() {
        crear("99999999", "5000000", 24, "PERSONAL")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CLIENTE_NO_ENCONTRADO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando el tipo de prestamo no existe, debe responder 400 y no guardar nada")
    void cuandoTipoNoExiste_debeResponder400() {
        crear("1020304050", "5000000", 24, "INEXISTENTE")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("TIPO_PRESTAMO_NO_VALIDO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando el monto esta fuera del rango del tipo, debe responder 400 y no guardar nada")
    void cuandoMontoFueraDeRango_debeResponder400() {
        crear("1020304050", "100", 24, "PERSONAL")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("MONTO_FUERA_DE_RANGO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando los datos de entrada son invalidos, debe responder 400 con el detalle por campo")
    void cuandoDatosInvalidos_debeResponder400() {
        client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).bodyValue("{\"monto\":-1}").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                .jsonPath("$.detalles.documento_identidad").isNotEmpty()
                .jsonPath("$.detalles.tipo_prestamo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando AUTENTICACION falla, debe responder 503 con mensaje generico y no guardar nada")
    void cuandoAutenticacionFalla_debeResponder503() {
        crear(DOCUMENTO_CON_FALLA, "5000000", 24, "PERSONAL")
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("la documentacion OpenAPI debe exponer el endpoint de solicitudes")
    void documentacionOpenApi_debeExponerEndpoint() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.paths['/api/v1/solicitud'].post.operationId").isEqualTo("crearSolicitud");
    }

    @Test
    @DisplayName("el health check debe reportar UP")
    void healthCheck_debeReportarUp() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
