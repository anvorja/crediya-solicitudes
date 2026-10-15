package com.crediya.solicitudes.integration;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU2 + HU3 de extremo a extremo: aplicacion completa + PostgreSQL real + AUTENTICACION simulado por HTTP real
 * (sirve el JWKS y los usuarios) + tokens firmados de verdad.
 */
@Tag("integration")
@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("HU2 + HU3 - Registrar una solicitud de prestamo con autenticacion (aplicacion completa)")
class CrearSolicitudIntegrationTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String DOCUMENTO_MARIA = "1020304050";
    private static final String DOCUMENTO_INEXISTENTE = "99999999";
    private static final String DOCUMENTO_CON_FALLA = "5000000000";
    private static final Map<String, String> CLIENTES = Map.of(DOCUMENTO_MARIA, """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","correo_electronico":"maria@example.com",
             "salario_base":4500000,"rol":"CLIENTE"}""");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final MockWebServer AUTENTICACION = new MockWebServer();
    static final RSAKey CLAVE_DE_AUTENTICACION = generarClave("k1");
    /** Peticiones de usuario recibidas por AUTENTICACION: "documento|cabecera Authorization". */
    static final List<String> CONSULTAS_A_AUTENTICACION = new CopyOnWriteArrayList<>();

    static {
        AUTENTICACION.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest peticion) {
                String ruta = peticion.getPath();
                if (ruta.equals("/.well-known/jwks.json")) {
                    return json(200, new JWKSet(CLAVE_DE_AUTENTICACION.toPublicJWK()).toString());
                }
                String documento = ruta.substring(ruta.lastIndexOf('/') + 1);
                String autorizacion = peticion.getHeader(HttpHeaders.AUTHORIZATION);
                CONSULTAS_A_AUTENTICACION.add(documento + "|" + autorizacion);
                if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
                    return json(401, "{}");
                }
                if (DOCUMENTO_CON_FALLA.equals(documento)) {
                    return new MockResponse().setResponseCode(500);
                }
                String cuerpo = CLIENTES.get(documento);
                return cuerpo == null ? json(404, "{}") : json(200, cuerpo);
            }
        });
        try {
            AUTENTICACION.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar el servidor simulado de autenticacion", e);
        }
    }

    private static MockResponse json(int estado, String cuerpo) {
        return new MockResponse().setResponseCode(estado).setHeader("Content-Type", "application/json").setBody(cuerpo);
    }

    private static RSAKey generarClave(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
        registro.add("AUTH_JWKS_URI", () -> AUTENTICACION.url("/.well-known/jwks.json").toString());
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
        CONSULTAS_A_AUTENTICACION.clear();
    }

    // ---------- utilidades ----------

    private static String token(RSAKey firma, String rol, String documento, String audiencia, long vigenciaMs) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer("crediya-auth").audience(audiencia)
                    .subject(UUID.randomUUID().toString()).claim("documento_identidad", documento)
                    .claim("email", "actor@crediya.test").issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + vigenciaMs));
            if (rol != null) {
                claims.claim("rol", rol);
            }
            var firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(firma.getKeyID()).build(), claims.build());
            firmado.sign(new RSASSASigner(firma));
            return firmado.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenCliente(String documento) {
        return token(CLAVE_DE_AUTENTICACION, "CLIENTE", documento, "crediya", 60_000);
    }

    private WebTestClient.ResponseSpec crear(String token, String documento, String monto, int plazo, String tipo) {
        String cuerpo = """
                {"documento_identidad":"%s","monto":%s,"plazo":%d,"tipo_prestamo":"%s"}""".formatted(documento, monto, plazo, tipo);
        var peticion = client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return peticion.bodyValue(cuerpo).exchange();
    }

    private Long contarSolicitudes() {
        return databaseClient.sql("SELECT count(*) AS total FROM solicitud")
                .map((fila, metadatos) -> fila.get("total", Long.class)).one().block();
    }

    // ---------- HU2 con un cliente autenticado ----------

    @Test
    @DisplayName("un CLIENTE autentico crea su solicitud: 201, Pendiente de revision, email del cliente y token reenviado a AUTENTICACION")
    void clienteAutenticado_debeCrearSolicitudYReenviarElToken() {
        String token = tokenCliente(DOCUMENTO_MARIA);

        crear(token, DOCUMENTO_MARIA, "5000000", 24, "personal")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_solicitud").isNotEmpty()
                .jsonPath("$.estado").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.email").isEqualTo("maria@example.com")
                .jsonPath("$.tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.tasa_interes").isEqualTo(0.018);

        var fila = databaseClient.sql("SELECT e.nombre AS estado, s.email FROM solicitud s JOIN estados e USING (id_estado)")
                .map((r, m) -> r.get("estado", String.class) + "|" + r.get("email", String.class)).one().block();
        assertThat(fila).isEqualTo("PENDIENTE_REVISION|maria@example.com");
        assertThat(CONSULTAS_A_AUTENTICACION).containsExactly(DOCUMENTO_MARIA + "|Bearer " + token);
    }

    // ---------- HU3: autenticacion ----------

    @Test
    @DisplayName("sin token debe responder 401 en formato JSON y no guardar nada")
    void sinToken_debeResponder401() {
        crear(null, DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.codigo").isEqualTo("NO_AUTENTICADO");

        assertThat(contarSolicitudes()).isZero();
        assertThat(CONSULTAS_A_AUTENTICACION).isEmpty();
    }

    @Test
    @DisplayName("un token basura debe responder 401")
    void tokenBasura_debeResponder401() {
        crear("esto.no.es-un-jwt", DOCUMENTO_MARIA, "5000000", 24, "PERSONAL").expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("un token firmado con una clave que no esta en el JWKS debe responder 401 aunque diga ser CLIENTE")
    void tokenFirmadoConClaveAjena_debeResponder401() {
        var ajena = generarClave("k1");

        crear(token(ajena, "CLIENTE", DOCUMENTO_MARIA, "crediya", 60_000), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isUnauthorized();
        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("un token vencido debe responder 401")
    void tokenVencido_debeResponder401() {
        crear(token(CLAVE_DE_AUTENTICACION, "CLIENTE", DOCUMENTO_MARIA, "crediya", -120_000), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("un token dirigido a otra audiencia debe responder 401")
    void tokenDeOtraAudiencia_debeResponder401() {
        crear(token(CLAVE_DE_AUTENTICACION, "CLIENTE", DOCUMENTO_MARIA, "otro-servicio", 60_000), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isUnauthorized();
    }

    // ---------- HU3: autorizacion ----------

    @Test
    @DisplayName("un ASESOR o un ADMIN no pueden crear solicitudes: 403")
    void personalInterno_noPuedeCrearSolicitudes() {
        for (String rol : List.of("ASESOR", "ADMIN")) {
            crear(token(CLAVE_DE_AUTENTICACION, rol, DOCUMENTO_MARIA, "crediya", 60_000), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
        }
        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("un token valido pero sin rol no concede ningun permiso: 403")
    void tokenSinRol_debeResponder403() {
        crear(token(CLAVE_DE_AUTENTICACION, null, DOCUMENTO_MARIA, "crediya", 60_000), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("un CLIENTE no puede crear una solicitud a nombre de otra persona: 403 y no se consulta a AUTENTICACION")
    void clienteNoPuedeCrearANombreDeOtro_debeResponder403() {
        crear(tokenCliente("1111111111"), DOCUMENTO_MARIA, "5000000", 24, "PERSONAL")
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("ACCESO_DENEGADO");

        assertThat(contarSolicitudes()).isZero();
        assertThat(CONSULTAS_A_AUTENTICACION).isEmpty();
    }

    // ---------- HU2: reglas de negocio ----------

    @Test
    @DisplayName("cuando el cliente no esta registrado en AUTENTICACION, debe responder 404 y no guardar nada")
    void cuandoClienteNoExiste_debeResponder404() {
        crear(tokenCliente(DOCUMENTO_INEXISTENTE), DOCUMENTO_INEXISTENTE, "5000000", 24, "PERSONAL")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CLIENTE_NO_ENCONTRADO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando el tipo de prestamo no existe, debe responder 400 y no guardar nada")
    void cuandoTipoNoExiste_debeResponder400() {
        crear(tokenCliente(DOCUMENTO_MARIA), DOCUMENTO_MARIA, "5000000", 24, "INEXISTENTE")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("TIPO_PRESTAMO_NO_VALIDO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando el monto esta fuera del rango del tipo, debe responder 400 y no guardar nada")
    void cuandoMontoFueraDeRango_debeResponder400() {
        crear(tokenCliente(DOCUMENTO_MARIA), DOCUMENTO_MARIA, "100", 24, "PERSONAL")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("MONTO_FUERA_DE_RANGO");

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("cuando los datos de entrada son invalidos, debe responder 400 con el detalle por campo")
    void cuandoDatosInvalidos_debeResponder400() {
        client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente(DOCUMENTO_MARIA))
                .bodyValue("{\"monto\":-1}").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                .jsonPath("$.detalles.documento_identidad").isNotEmpty()
                .jsonPath("$.detalles.tipo_prestamo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando AUTENTICACION falla, debe responder 503 con mensaje generico y no guardar nada")
    void cuandoAutenticacionFalla_debeResponder503() {
        crear(tokenCliente(DOCUMENTO_CON_FALLA), DOCUMENTO_CON_FALLA, "5000000", 24, "PERSONAL")
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");

        assertThat(contarSolicitudes()).isZero();
    }

    // ---------- operacion ----------

    @Test
    @DisplayName("la documentacion y el health deben ser publicos y la documentacion debe declarar el esquema Bearer")
    void documentacionYHealth_debenSerPublicos() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/solicitud'].post.operationId").isEqualTo("crearSolicitud")
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
