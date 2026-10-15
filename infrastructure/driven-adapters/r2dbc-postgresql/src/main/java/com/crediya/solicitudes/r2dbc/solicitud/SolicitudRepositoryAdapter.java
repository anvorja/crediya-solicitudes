package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.gateways.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

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
}
