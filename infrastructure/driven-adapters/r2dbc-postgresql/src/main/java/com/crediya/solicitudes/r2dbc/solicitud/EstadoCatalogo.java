package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;

/**
 * Traduce el estado del dominio al identificador sembrado en la tabla {@code estados}.
 */
final class EstadoCatalogo {

    private EstadoCatalogo() {
    }

    static short idDe(EstadoSolicitud estado) {
        return switch (estado) {
            case PENDIENTE_REVISION -> 1;
            case REVISION_MANUAL -> 2;
            case APROBADA -> 3;
            case RECHAZADA -> 4;
        };
    }

    static EstadoSolicitud estadoDe(short id) {
        return switch (id) {
            case 1 -> EstadoSolicitud.PENDIENTE_REVISION;
            case 2 -> EstadoSolicitud.REVISION_MANUAL;
            case 3 -> EstadoSolicitud.APROBADA;
            case 4 -> EstadoSolicitud.RECHAZADA;
            default -> throw new IllegalStateException("Estado desconocido en base de datos: " + id);
        };
    }
}
