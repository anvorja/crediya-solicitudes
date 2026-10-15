package com.crediya.solicitudes.r2dbc.tipoprestamo;

import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.model.tipoprestamo.gateways.TipoPrestamoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TipoPrestamoRepositoryAdapter implements TipoPrestamoRepository {

    private static final String BUSCAR_POR_NOMBRE = """
            SELECT id_tipo_prestamo, nombre, monto_minimo, monto_maximo, tasa_interes, validacion_automatica
            FROM tipo_prestamo
            WHERE lower(nombre) = lower(:nombre)
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<TipoPrestamo> buscarPorNombre(String nombre) {
        return databaseClient.sql(BUSCAR_POR_NOMBRE)
                .bind("nombre", nombre)
                .map((fila, metadatos) -> TipoPrestamoRowMapper.aTipoPrestamo(fila))
                .one()
                .doOnSubscribe(s -> log.debug("Buscando tipo de prestamo"));
    }
}
