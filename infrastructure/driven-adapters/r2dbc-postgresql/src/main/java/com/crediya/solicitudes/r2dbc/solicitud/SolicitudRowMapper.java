package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.r2dbc.tipoprestamo.TipoPrestamoRowMapper;
import io.r2dbc.spi.Row;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Convierte una fila del join solicitud + tipo_prestamo en una solicitud del dominio.
 */
final class SolicitudRowMapper {

    /**
     * Columnas que lee {@link #aSolicitud}. Las consultas deben seleccionar estas columnas.
     */
    static final String COLUMNAS = """
            s.id_solicitud, s.monto, s.plazo, s.email, s.id_estado,
            t.id_tipo_prestamo, t.nombre, t.monto_minimo, t.monto_maximo, t.tasa_interes, t.validacion_automatica
            """;

    private SolicitudRowMapper() {
    }

    static Solicitud aSolicitud(Row fila) {
        return new Solicitud(
                fila.get("id_solicitud", UUID.class),
                fila.get("monto", BigDecimal.class),
                fila.get("plazo", Integer.class),
                fila.get("email", String.class),
                EstadoCatalogo.estadoDe(fila.get("id_estado", Short.class)),
                TipoPrestamoRowMapper.aTipoPrestamo(fila));
    }
}
