package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.r2dbc.PostgresTestSupport;
import com.crediya.solicitudes.r2dbc.tipoprestamo.TipoPrestamoRepositoryAdapter;
import com.crediya.solicitudes.r2dbc.transaccion.TransaccionAdapter;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@DisplayName("Adaptadores de solicitudes contra PostgreSQL real")
class SolicitudRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static ConnectionPool pool;
    private static DatabaseClient databaseClient;
    private static SolicitudRepositoryAdapter solicitudes;
    private static TipoPrestamoRepositoryAdapter tipos;
    private static TransaccionAdapter transaccion;

    @BeforeAll
    static void iniciar() {
        PostgresTestSupport.migrar(POSTGRES);
        pool = PostgresTestSupport.crearPool(POSTGRES);
        databaseClient = DatabaseClient.create(pool);
        solicitudes = new SolicitudRepositoryAdapter(databaseClient);
        tipos = new TipoPrestamoRepositoryAdapter(databaseClient);
        transaccion = new TransaccionAdapter(TransactionalOperator.create(new R2dbcTransactionManager(pool)));
    }

    @AfterAll
    static void cerrar() {
        pool.dispose();
    }

    @BeforeEach
    void limpiar() {
        databaseClient.sql("TRUNCATE TABLE solicitud").then().block();
    }

    private static TipoPrestamo personal() {
        return tipos.buscarPorNombre("PERSONAL").block();
    }

    private static Solicitud nueva(String email) {
        return Solicitud.crear(email, new BigDecimal("5000000"), 24, personal());
    }

    private static Long contarSolicitudes() {
        return databaseClient.sql("SELECT count(*) AS total FROM solicitud")
                .map((fila, metadatos) -> fila.get("total", Long.class)).one().block();
    }

    @Test
    @DisplayName("el esquema debe sembrar los 4 estados y los tipos de prestamo del enunciado")
    void esquema_debeSembrarCatalogos() {
        var estados = databaseClient.sql("SELECT count(*) AS total FROM estados")
                .map((fila, metadatos) -> fila.get("total", Long.class)).one().block();
        var nombresTipos = databaseClient.sql("SELECT nombre FROM tipo_prestamo ORDER BY id_tipo_prestamo")
                .map((fila, metadatos) -> fila.get("nombre", String.class)).all().collectList().block();

        assertThat(estados).isEqualTo(4L);
        assertThat(nombresTipos).containsExactly("PERSONAL", "LIBRE_INVERSION", "VEHICULO", "HIPOTECARIO");
    }

    @Test
    @DisplayName("cuando se guarda una solicitud, debe asignar un id y persistir estado, tipo, monto, plazo y email")
    void guardar_debeAsignarIdYPersistir() {
        var original = nueva("maria@example.com");

        StepVerifier.create(solicitudes.guardar(original))
                .assertNext(guardada -> {
                    assertThat(guardada.id()).isNotNull();
                    assertThat(guardada).usingRecursiveComparison().ignoringFields("id").isEqualTo(original);
                })
                .verifyComplete();

        var fila = databaseClient.sql("""
                        SELECT s.monto, s.plazo, s.email, e.nombre AS estado, t.nombre AS tipo
                        FROM solicitud s JOIN estados e USING (id_estado) JOIN tipo_prestamo t USING (id_tipo_prestamo)""")
                .map((r, m) -> r.get("estado", String.class) + "|" + r.get("tipo", String.class) + "|"
                        + r.get("plazo", Integer.class) + "|" + r.get("email", String.class) + "|"
                        + r.get("monto", BigDecimal.class).toPlainString())
                .one().block();
        assertThat(fila).isEqualTo("PENDIENTE_REVISION|PERSONAL|24|maria@example.com|5000000.00");
    }

    @Test
    @DisplayName("cada estado del dominio debe poder persistirse")
    void guardar_debePersistirCadaEstado() {
        for (EstadoSolicitud estado : EstadoSolicitud.values()) {
            var solicitud = new Solicitud(null, new BigDecimal("5000000"), 12, "a@b.co", estado, personal());

            StepVerifier.create(solicitudes.guardar(solicitud))
                    .assertNext(guardada -> assertThat(guardada.estado()).isEqualTo(estado))
                    .verifyComplete();
        }
        assertThat(contarSolicitudes()).isEqualTo(4L);
    }

    // ---------- HU4: listado para revision ----------

    private static Solicitud guardarConEstado(String email, String monto, EstadoSolicitud estado, String tipo) {
        var tipoPrestamo = tipos.buscarPorNombre(tipo).block();
        return solicitudes.guardar(new Solicitud(null, new BigDecimal(monto), 12, email, estado, tipoPrestamo)).block();
    }

    @Test
    @DisplayName("buscarParaRevision debe listar por defecto pendientes, revision manual y rechazadas, de la mas antigua a la mas reciente")
    void buscarParaRevision_debeListarEnOrdenDeAtencion() {
        var primera = guardarConEstado("a@example.com", "1000000", EstadoSolicitud.PENDIENTE_REVISION, "PERSONAL");
        var segunda = guardarConEstado("b@example.com", "2000000", EstadoSolicitud.RECHAZADA, "PERSONAL");
        var tercera = guardarConEstado("c@example.com", "3000000", EstadoSolicitud.REVISION_MANUAL, "VEHICULO");
        guardarConEstado("d@example.com", "4000000", EstadoSolicitud.APROBADA, "PERSONAL");

        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(null, null, 0, 10)))
                .assertNext(pagina -> {
                    assertThat(pagina.totalElementos()).isEqualTo(3);
                    assertThat(pagina.elementos()).extracting(Solicitud::id)
                            .containsExactly(primera.id(), segunda.id(), tercera.id());
                    assertThat(pagina.elementos().get(2).tipoPrestamo().nombre()).isEqualTo("VEHICULO");
                    assertThat(pagina.elementos().get(0).tipoPrestamo().tasaInteres()).isEqualByComparingTo("0.018");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("buscarParaRevision debe paginar: respeta tamano y pagina y siempre informa el total")
    void buscarParaRevision_debePaginar() {
        for (int i = 0; i < 5; i++) {
            guardarConEstado("u" + i + "@example.com", "1000000", EstadoSolicitud.PENDIENTE_REVISION, "PERSONAL");
        }

        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(null, null, 0, 2)))
                .assertNext(pagina -> {
                    assertThat(pagina.elementos()).extracting(Solicitud::email).containsExactly("u0@example.com", "u1@example.com");
                    assertThat(pagina.totalElementos()).isEqualTo(5);
                    assertThat(pagina.totalPaginas()).isEqualTo(3);
                })
                .verifyComplete();
        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(null, null, 2, 2)))
                .assertNext(pagina -> assertThat(pagina.elementos()).extracting(Solicitud::email).containsExactly("u4@example.com"))
                .verifyComplete();
        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(null, null, 9, 2)))
                .assertNext(pagina -> {
                    assertThat(pagina.elementos()).isEmpty();
                    assertThat(pagina.totalElementos()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("buscarParaRevision debe filtrar por estado y por tipo de prestamo (sin distinguir mayusculas)")
    void buscarParaRevision_debeFiltrar() {
        guardarConEstado("a@example.com", "1000000", EstadoSolicitud.PENDIENTE_REVISION, "PERSONAL");
        var rechazadaVehiculo = guardarConEstado("b@example.com", "20000000", EstadoSolicitud.RECHAZADA, "VEHICULO");
        guardarConEstado("c@example.com", "3000000", EstadoSolicitud.RECHAZADA, "PERSONAL");

        StepVerifier.create(solicitudes.buscarParaRevision(
                        FiltroSolicitudes.de(List.of(EstadoSolicitud.RECHAZADA), "vehiculo", 0, 10)))
                .assertNext(pagina -> {
                    assertThat(pagina.elementos()).extracting(Solicitud::id).containsExactly(rechazadaVehiculo.id());
                    assertThat(pagina.totalElementos()).isEqualTo(1);
                })
                .verifyComplete();
        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(List.of(EstadoSolicitud.RECHAZADA), null, 0, 10)))
                .assertNext(pagina -> assertThat(pagina.totalElementos()).isEqualTo(2))
                .verifyComplete();
        StepVerifier.create(solicitudes.buscarParaRevision(FiltroSolicitudes.de(null, "HIPOTECARIO", 0, 10)))
                .assertNext(pagina -> assertThat(pagina.elementos()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("buscarAprobadasPorEmails debe devolver solo las aprobadas de los correos pedidos (sin distinguir mayusculas)")
    void buscarAprobadasPorEmails_debeDevolverSoloLasAprobadas() {
        var aprobadaMaria = guardarConEstado("maria@example.com", "5000000", EstadoSolicitud.APROBADA, "PERSONAL");
        guardarConEstado("maria@example.com", "1000000", EstadoSolicitud.PENDIENTE_REVISION, "PERSONAL");
        guardarConEstado("pedro@example.com", "1000000", EstadoSolicitud.APROBADA, "PERSONAL");
        guardarConEstado("ana@example.com", "1000000", EstadoSolicitud.APROBADA, "PERSONAL");

        StepVerifier.create(solicitudes.buscarAprobadasPorEmails(List.of("maria@example.com", "ana@example.com")).collectList())
                .assertNext(lista -> {
                    assertThat(lista).hasSize(2);
                    assertThat(lista).extracting(Solicitud::email).containsExactlyInAnyOrder("maria@example.com", "ana@example.com");
                    assertThat(lista).extracting(Solicitud::id).contains(aprobadaMaria.id());
                    assertThat(lista).allMatch(solicitud -> solicitud.estado() == EstadoSolicitud.APROBADA);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("la transaccion debe confirmar los cambios cuando la operacion termina bien")
    void transaccion_debeConfirmar() {
        StepVerifier.create(transaccion.ejecutar(solicitudes.guardar(nueva("ok@example.com"))))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(contarSolicitudes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("la transaccion debe revertir los cambios cuando la operacion falla despues de guardar")
    void transaccion_debeRevertir() {
        var operacion = solicitudes.guardar(nueva("rollback@example.com"))
                .then(Mono.<Solicitud>error(new IllegalStateException("fallo despues de guardar")));

        StepVerifier.create(transaccion.ejecutar(operacion))
                .expectErrorMessage("fallo despues de guardar")
                .verify();

        assertThat(contarSolicitudes()).isZero();
    }

    @Test
    @DisplayName("buscarPorNombre debe ignorar mayusculas, mapear todos los campos y devolver vacio si no existe")
    void tipoPrestamo_buscarPorNombre() {
        StepVerifier.create(tipos.buscarPorNombre("personal"))
                .assertNext(tipo -> {
                    assertThat(tipo.id()).isEqualTo(1L);
                    assertThat(tipo.nombre()).isEqualTo("PERSONAL");
                    assertThat(tipo.montoMinimo()).isEqualByComparingTo("500000");
                    assertThat(tipo.montoMaximo()).isEqualByComparingTo("50000000");
                    assertThat(tipo.tasaInteres()).isEqualByComparingTo("0.018");
                    assertThat(tipo.validacionAutomatica()).isTrue();
                })
                .verifyComplete();
        StepVerifier.create(tipos.buscarPorNombre("Vehiculo"))
                .assertNext(tipo -> assertThat(tipo.validacionAutomatica()).isFalse())
                .verifyComplete();
        StepVerifier.create(tipos.buscarPorNombre("INEXISTENTE")).verifyComplete();
    }
}
