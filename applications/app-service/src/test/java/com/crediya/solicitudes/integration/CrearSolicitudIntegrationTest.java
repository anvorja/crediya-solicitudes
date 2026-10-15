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
@DisplayName("HU2 + HU3 + HU4 - Solicitudes de prestamo con autenticacion (aplicacion completa)")
class CrearSolicitudIntegrationTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String DOCUMENTO_MARIA = "1020304050";
    private static final String DOCUMENTO_INEXISTENTE = "99999999";
    private static final String DOCUMENTO_CON_FALLA = "5000000000";
    private static final String JSON_MARIA = """
            {"id":"6f1c1c52-1d5e-4a45-9b52-8b1f3f1a0a11","nombres":"Maria","apellidos":"Gomez",
             "documento_identidad":"1020304050","correo_electronico":"maria@example.com",
             "salario_base":4500000,"rol":"CLIENTE"}""";
    private static final String JSON_PEDRO = """
            {"id":"7a2d2d63-2e6f-4b56-8c63-9c2a4a2b1b22","nombres":"Pedro","apellidos":"Ruiz",
             "documento_identidad":"2020202020","correo_electronico":"pedro@example.com",
             "salario_base":3000000,"rol":"CLIENTE"}""";
    private static final Map<String, String> CLIENTES = Map.of(DOCUMENTO_MARIA, JSON_MARIA);
    private static final Map<String, String> CLIENTES_POR_EMAIL = Map.of("maria@example.com", JSON_MARIA, "pedro@example.com", JSON_PEDRO);

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
                String autorizacion = peticion.getHeader(HttpHeaders.AUTHORIZATION);
                if (ruta.startsWith("/api/v1/usuarios?")) {
                    return consultaPorLotes(ruta, autorizacion);
                }
                String documento = ruta.substring(ruta.lastIndexOf('/') + 1);
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

    /** Simula GET /api/v1/usuarios?correos=a,b: devuelve solo los conocidos y exige el token reenviado. */
    private static MockResponse consultaPorLotes(String ruta, String autorizacion) {
        CONSULTAS_A_AUTENTICACION.add("lote|" + autorizacion);
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            return json(401, "{}");
        }
        String correos = java.net.URLDecoder.decode(ruta.substring(ruta.indexOf("correos=") + 8), java.nio.charset.StandardCharsets.UTF_8);
        String lista = java.util.Arrays.stream(correos.split(","))
                .map(CLIENTES_POR_EMAIL::get).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return json(200, lista);
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
        // Estas pruebas no usan colas: se apaga el relevo para que no consulte una base que ya se detuvo
        registro.add("OUTBOX_ENABLED", () -> "false");
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

    // ---------- HU4: listado para revision manual ----------

    private void sembrar(String email, String monto, int plazo, int idEstado, int idTipo) {
        databaseClient.sql("""
                        INSERT INTO solicitud (monto, plazo, email, id_estado, id_tipo_prestamo)
                        VALUES (:monto, :plazo, :email, :estado, :tipo)""")
                .bind("monto", new java.math.BigDecimal(monto)).bind("plazo", plazo).bind("email", email)
                .bind("estado", (short) idEstado).bind("tipo", (short) idTipo)
                .then().block();
    }

    /** Estados: 1 pendiente, 2 revision manual, 3 aprobada, 4 rechazada. Tipos: 1 PERSONAL, 3 VEHICULO. */
    private void sembrarEscenario() {
        sembrar("maria@example.com", "5000000", 24, 3, 1);    // aprobada: cuenta para la deuda de Maria
        sembrar("maria@example.com", "2000000", 12, 1, 1);    // pendiente
        sembrar("pedro@example.com", "20000000", 36, 4, 3);   // rechazada (VEHICULO)
        sembrar("ana@example.com", "3000000", 12, 2, 1);      // revision manual; AUTENTICACION no la conoce
        sembrar("carlos@example.com", "4000000", 12, 3, 1);   // aprobada: no aparece en el listado
    }

    private WebTestClient.ResponseSpec listar(String token, String consulta) {
        var peticion = client.get().uri(RUTA + consulta);
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return peticion.exchange();
    }

    private static String tokenAsesor() {
        return token(CLAVE_DE_AUTENTICACION, "ASESOR", "3030303030", "crediya", 60_000);
    }

    @Test
    @DisplayName("HU4: un ASESOR lista las solicitudes de revision con nombre, salario y deuda mensual de las aprobadas")
    void asesor_listaSolicitudesParaRevision() {
        sembrarEscenario();
        String token = tokenAsesor();

        listar(token, "")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total_elementos").isEqualTo(3)
                .jsonPath("$.total_paginas").isEqualTo(1)
                .jsonPath("$.contenido.length()").isEqualTo(3)
                // De la mas antigua a la mas reciente: maria (pendiente), pedro (rechazada), ana (revision manual)
                .jsonPath("$.contenido[0].email").isEqualTo("maria@example.com")
                .jsonPath("$.contenido[0].nombre").isEqualTo("Maria Gomez")
                .jsonPath("$.contenido[0].salario_base").isEqualTo(4500000)
                .jsonPath("$.contenido[0].estado_solicitud").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.contenido[0].tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.contenido[0].tasa_interes").isEqualTo(0.018)
                .jsonPath("$.contenido[0].deuda_total_mensual_solicitudes_aprobadas").isEqualTo(258404.18)
                .jsonPath("$.contenido[1].nombre").isEqualTo("Pedro Ruiz")
                .jsonPath("$.contenido[1].estado_solicitud").isEqualTo("RECHAZADA")
                .jsonPath("$.contenido[1].tipo_prestamo").isEqualTo("VEHICULO")
                .jsonPath("$.contenido[1].deuda_total_mensual_solicitudes_aprobadas").isEqualTo(0)
                .jsonPath("$.contenido[2].email").isEqualTo("ana@example.com")
                .jsonPath("$.contenido[2].nombre").isEmpty()
                .jsonPath("$.contenido[2].salario_base").isEmpty();

        // Una sola consulta por lotes a AUTENTICACION, con el token del asesor reenviado
        assertThat(CONSULTAS_A_AUTENTICACION).containsExactly("lote|Bearer " + token);
    }

    @Test
    @DisplayName("HU4: el listado se puede filtrar por estado y por tipo de prestamo")
    void listado_sePuedeFiltrar() {
        sembrarEscenario();

        listar(tokenAsesor(), "?estado=RECHAZADA")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.total_elementos").isEqualTo(1).jsonPath("$.contenido[0].email").isEqualTo("pedro@example.com");
        listar(tokenAsesor(), "?tipo_prestamo=vehiculo")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.total_elementos").isEqualTo(1).jsonPath("$.contenido[0].email").isEqualTo("pedro@example.com");
        listar(tokenAsesor(), "?estado=REVISION_MANUAL,PENDIENTE_REVISION&tipo_prestamo=PERSONAL")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.total_elementos").isEqualTo(2);
    }

    @Test
    @DisplayName("HU4: el listado se pagina y siempre informa el total")
    void listado_sePagina() {
        sembrarEscenario();

        listar(tokenAsesor(), "?tamano=2&pagina=1")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contenido.length()").isEqualTo(1)
                .jsonPath("$.contenido[0].email").isEqualTo("ana@example.com")
                .jsonPath("$.pagina").isEqualTo(1)
                .jsonPath("$.tamano").isEqualTo(2)
                .jsonPath("$.total_elementos").isEqualTo(3)
                .jsonPath("$.total_paginas").isEqualTo(2);
    }

    @Test
    @DisplayName("HU4: sin solicitudes de revision el listado es una pagina vacia y no consulta a AUTENTICACION")
    void listadoVacio_noConsultaAutenticacion() {
        listar(tokenAsesor(), "")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.contenido.length()").isEqualTo(0).jsonPath("$.total_elementos").isEqualTo(0);

        assertThat(CONSULTAS_A_AUTENTICACION).isEmpty();
    }

    @Test
    @DisplayName("HU4: solo un ASESOR puede listar: CLIENTE y ADMIN reciben 403 y sin token 401")
    void listado_soloAsesor() {
        sembrarEscenario();

        for (String rol : List.of("CLIENTE", "ADMIN")) {
            listar(token(CLAVE_DE_AUTENTICACION, rol, "3030303030", "crediya", 60_000), "")
                    .expectStatus().isForbidden();
        }
        listar(null, "").expectStatus().isUnauthorized();
        assertThat(CONSULTAS_A_AUTENTICACION).isEmpty();
    }

    @Test
    @DisplayName("HU4: los parametros invalidos dan 400 con el detalle")
    void listado_validaParametros() {
        String token = tokenAsesor();

        listar(token, "?estado=APROBADA").expectStatus().isBadRequest();
        listar(token, "?tamano=0").expectStatus().isBadRequest();
        listar(token, "?tamano=51").expectStatus().isBadRequest();
        listar(token, "?pagina=-1").expectStatus().isBadRequest();
        listar(token, "?pagina=abc").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detalles.pagina").isNotEmpty();
    }
}
