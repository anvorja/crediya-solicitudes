package com.crediya.solicitudes.r2dbc.tipoprestamo;

import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.model.tipoprestamo.gateways.TipoPrestamoRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

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
                .map((fila, metadatos) -> aTipoPrestamo(fila))
                .one()
                .doOnSubscribe(s -> log.debug("Buscando tipo de prestamo"));
    }

    static TipoPrestamo aTipoPrestamo(Row fila) {
        return new TipoPrestamo(
                fila.get("id_tipo_prestamo", Short.class).longValue(),
                fila.get("nombre", String.class),
                fila.get("monto_minimo", BigDecimal.class),
                fila.get("monto_maximo", BigDecimal.class),
                fila.get("tasa_interes", BigDecimal.class),
                Boolean.TRUE.equals(fila.get("validacion_automatica", Boolean.class)));
    }
}
