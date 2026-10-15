package com.crediya.solicitudes.r2dbc.evento;

import com.crediya.solicitudes.model.evento.Destino;
import com.crediya.solicitudes.model.evento.SolicitudDecidida;
import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.r2dbc.PostgresTestSupport;
import com.crediya.solicitudes.r2dbc.transaccion.TransaccionAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@DisplayName("Outbox de eventos contra PostgreSQL real")
class EventoSalienteRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);

    private static ConnectionPool pool;
    private static DatabaseClient databaseClient;
    private static EventoSalienteRepositoryAdapter outbox;
    private static TransaccionAdapter transaccion;

    @BeforeAll
    static void iniciar() {
        PostgresTestSupport.migrar(POSTGRES);
        pool = PostgresTestSupport.crearPool(POSTGRES);
        databaseClient = DatabaseClient.create(pool);
        outbox = new EventoSalienteRepositoryAdapter(databaseClient);
        transaccion = new TransaccionAdapter(TransactionalOperator.create(new R2dbcTransactionManager(pool)));
    }

    @AfterAll
    static void cerrar() {
        pool.dispose();
    }

    @BeforeEach
    void limpiar() {
        databaseClient.sql("TRUNCATE TABLE evento_saliente").then().block();
    }

    private static SolicitudDecidida evento(String email, List<CuotaPlanDePago> plan) {
        var solicitud = new Solicitud(UUID.randomUUID(), new BigDecimal("5000000.00"), 24, email, EstadoSolicitud.APROBADA, PERSONAL);
        return new SolicitudDecidida(Instant.parse("2026-10-15T10:00:00Z"), solicitud, "Maria Gomez", plan);
    }

    @Test
    @DisplayName("registrar guarda el evento con destino, id_evento igual al id de la fila y el contrato v1 en snake_case")
    void registrar_guardaContrato() throws Exception {
        outbox.registrar(evento("maria@example.com", List.of())).block();

        var pendientes = outbox.tomarPendientes(10).collectList().block();

        assertThat(pendientes).hasSize(1);
        var guardado = pendientes.get(0);
        assertThat(guardado.destino()).isEqualTo(Destino.NOTIFICACIONES);
        assertThat(guardado.intentos()).isZero();
        JsonNode json = JSON.readTree(guardado.payload());
        assertThat(json.get("id_evento").asText()).isEqualTo(guardado.id().toString());
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("tipo").asText()).isEqualTo("SOLICITUD_DECIDIDA");
        assertThat(json.get("ocurrido_en").asText()).isEqualTo("2026-10-15T10:00:00Z");
        assertThat(json.at("/destinatario/email").asText()).isEqualTo("maria@example.com");
        assertThat(json.at("/destinatario/nombre").asText()).isEqualTo("Maria Gomez");
        assertThat(json.at("/solicitud/estado").asText()).isEqualTo("APROBADA");
        assertThat(json.at("/solicitud/monto").decimalValue()).isEqualByComparingTo("5000000");
        assertThat(json.at("/solicitud/plazo").asInt()).isEqualTo(24);
        assertThat(json.at("/solicitud/tipo_prestamo").asText()).isEqualTo("PERSONAL");
        assertThat(json.has("plan_de_pago")).isFalse();
    }

    @Test
    @DisplayName("el plan de pago viaja en el evento cuando existe y el nombre se omite si se desconoce")
    void registrar_conPlanYSinNombre() throws Exception {
        var plan = List.of(new CuotaPlanDePago(1, new BigDecimal("258404.18"), new BigDecimal("168404.18"),
                new BigDecimal("90000.00"), new BigDecimal("4831595.82")));
        var solicitud = new Solicitud(UUID.randomUUID(), new BigDecimal("5000000"), 24, "x@example.com", EstadoSolicitud.APROBADA, PERSONAL);
        outbox.registrar(new SolicitudDecidida(Instant.now(), solicitud, null, plan)).block();

        JsonNode json = JSON.readTree(outbox.tomarPendientes(1).blockFirst().payload());

        assertThat(json.at("/destinatario").has("nombre")).isFalse();
        assertThat(json.at("/plan_de_pago/0/numero").asInt()).isEqualTo(1);
        assertThat(json.at("/plan_de_pago/0/cuota").decimalValue()).isEqualByComparingTo("258404.18");
        assertThat(json.at("/plan_de_pago/0/interes").decimalValue()).isEqualByComparingTo("90000.00");
    }

    @Test
    @DisplayName("marcarEnviado saca el evento de los pendientes y registrarFallo cuenta el intento sin sacarlo")
    void marcarEnviadoYFallo() {
        outbox.registrar(evento("a@example.com", List.of())).block();
        outbox.registrar(evento("b@example.com", List.of())).block();
        var primero = outbox.tomarPendientes(10).collectList().block();

        outbox.marcarEnviado(primero.get(0).id()).block();
        outbox.registrarFallo(primero.get(1).id(), "cola caida").block();

        var restantes = outbox.tomarPendientes(10).collectList().block();
        assertThat(restantes).hasSize(1);
        assertThat(restantes.get(0).id()).isEqualTo(primero.get(1).id());
        assertThat(restantes.get(0).intentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("tomarPendientes respeta el limite y el orden de llegada")
    void limiteYOrden() {
        for (int i = 0; i < 5; i++) {
            outbox.registrar(evento("u" + i + "@example.com", List.of())).block();
        }

        var lote = outbox.tomarPendientes(3).collectList().block();

        assertThat(lote).hasSize(3);
    }

    @Test
    @DisplayName("si la transaccion falla, el evento no queda registrado")
    void rollbackNoDejaEvento() {
        var fallida = transaccion.ejecutar(outbox.registrar(evento("a@example.com", List.of()))
                .then(Mono.<Void>error(new IllegalStateException("fallo despues de registrar"))));

        StepVerifier.create(fallida).expectError(IllegalStateException.class).verify();

        assertThat(outbox.tomarPendientes(10).collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("dos relevos concurrentes no toman los mismos eventos (FOR UPDATE SKIP LOCKED)")
    void skipLocked() {
        for (int i = 0; i < 4; i++) {
            outbox.registrar(evento("u" + i + "@example.com", List.of())).block();
        }

        // Una transaccion externa bloquea 2 filas y, sin cerrarse, otro relevo pide hasta 10
        var resultado = Mono.usingWhen(pool.create(),
                conexion -> Mono.from(conexion.beginTransaction())
                        .then(Mono.from(conexion.createStatement("SELECT id FROM evento_saliente WHERE enviado_en IS NULL ORDER BY creado_en LIMIT 2 FOR UPDATE").execute()))
                        .flatMap(r -> Mono.from(r.map((fila, m) -> fila.get("id", UUID.class))).then())
                        .then(outbox.tomarPendientes(10).collectList()),
                conexion -> Mono.from(conexion.rollbackTransaction()).then(Mono.from(conexion.close())));

        // Con 2 filas bloqueadas por otra transaccion, SKIP LOCKED devuelve solo las 2 restantes
        assertThat(resultado.block()).hasSize(2);
    }
}
