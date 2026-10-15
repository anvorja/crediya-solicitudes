package com.crediya.solicitudes.r2dbc.tipoprestamo;

import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import io.r2dbc.spi.Row;

import java.math.BigDecimal;

/**
 * Lee las columnas de {@code tipo_prestamo} de una fila (sola o unida a otra tabla).
 */
public final class TipoPrestamoRowMapper {

    private TipoPrestamoRowMapper() {
    }

    public static TipoPrestamo aTipoPrestamo(Row fila) {
        return new TipoPrestamo(
                fila.get("id_tipo_prestamo", Short.class).longValue(),
                fila.get("nombre", String.class),
                fila.get("monto_minimo", BigDecimal.class),
                fila.get("monto_maximo", BigDecimal.class),
                fila.get("tasa_interes", BigDecimal.class),
                Boolean.TRUE.equals(fila.get("validacion_automatica", Boolean.class)));
    }
}
