package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.common.Pagina;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SolicitudRepositoryAdapter implements SolicitudRepository {

    private static final String INSERTAR = """
            INSERT INTO solicitud (monto, plazo, email, id_estado, id_tipo_prestamo)
            VALUES (:monto, :plazo, :email, :estado, :tipo)
            RETURNING id_solicitud
            """;

    private static final String DESDE_Y_FILTROS = """
            FROM solicitud s
            JOIN tipo_prestamo t ON t.id_tipo_prestamo = s.id_tipo_prestamo
            WHERE s.id_estado IN (:estados)
              AND (CAST(:tipo AS VARCHAR) IS NULL OR lower(t.nombre) = lower(CAST(:tipo AS VARCHAR)))
            """;

    private static final String LISTAR_PARA_REVISION = "SELECT " + SolicitudRowMapper.COLUMNAS + DESDE_Y_FILTROS + """
            ORDER BY s.creado_en ASC, s.id_solicitud ASC
            LIMIT :limite OFFSET :desplazamiento
            """;

    private static final String CONTAR_PARA_REVISION = "SELECT count(*) AS total " + DESDE_Y_FILTROS;

    private static final String APROBADAS_POR_EMAILS = "SELECT " + SolicitudRowMapper.COLUMNAS + """
            FROM solicitud s
            JOIN tipo_prestamo t ON t.id_tipo_prestamo = s.id_tipo_prestamo
            WHERE s.id_estado = :aprobada AND lower(s.email) IN (:emails)
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Solicitud> guardar(Solicitud solicitud) {
        // El id lo genera PostgreSQL (gen_random_uuid): evita SecureRandom en hilos reactivos
        return databaseClient.sql(INSERTAR)
                .bind("monto", solicitud.monto())
                .bind("plazo", solicitud.plazo())
                .bind("email", solicitud.email())
                .bind("estado", EstadoCatalogo.idDe(solicitud.estado()))
                .bind("tipo", solicitud.tipoPrestamo().id().shortValue())
                .map((fila, metadatos) -> fila.get("id_solicitud", UUID.class))
                .one()
                .map(solicitud::conId)
                .doOnSubscribe(s -> log.info("Guardando solicitud tipo={} estado={}",
                        solicitud.tipoPrestamo().nombre(), solicitud.estado()))
                .doOnNext(guardada -> log.info("Solicitud guardada id={}", guardada.id()));
    }

    @Override
    public Mono<Pagina<Solicitud>> buscarParaRevision(FiltroSolicitudes filtro) {
        Mono<List<Solicitud>> elementos = conFiltro(databaseClient.sql(LISTAR_PARA_REVISION), filtro)
                .bind("limite", filtro.tamano())
                .bind("desplazamiento", filtro.desplazamiento())
                .map((fila, metadatos) -> SolicitudRowMapper.aSolicitud(fila))
                .all()
                .collectList();
        Mono<Long> total = conFiltro(databaseClient.sql(CONTAR_PARA_REVISION), filtro)
                .map((fila, metadatos) -> fila.get("total", Long.class))
                .one();
        return Mono.zip(elementos, total,
                        (lista, cantidad) -> new Pagina<>(lista, filtro.pagina(), filtro.tamano(), cantidad))
                .doOnSubscribe(s -> log.debug("Listando solicitudes para revision pagina={} tamano={}", filtro.pagina(), filtro.tamano()));
    }

    @Override
    public Flux<Solicitud> buscarAprobadasPorEmails(Collection<String> emails) {
        return databaseClient.sql(APROBADAS_POR_EMAILS)
                .bind("aprobada", EstadoCatalogo.idDe(EstadoSolicitud.APROBADA))
                .bind("emails", List.copyOf(emails))
                .map((fila, metadatos) -> SolicitudRowMapper.aSolicitud(fila))
                .all();
    }

    private static DatabaseClient.GenericExecuteSpec conFiltro(DatabaseClient.GenericExecuteSpec consulta, FiltroSolicitudes filtro) {
        var conEstados = consulta.bind("estados", filtro.estados().stream().map(EstadoCatalogo::idDe).toList());
        return filtro.tipoPrestamo() == null
                ? conEstados.bindNull("tipo", String.class)
                : conEstados.bind("tipo", filtro.tipoPrestamo());
    }
}
