package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum EstadoSolicitud {
    PENDIENTE_REVISION("Pendiente de revision"),
    REVISION_MANUAL("Revision manual"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada");

    /**
     * HU4: estados que necesitan la decision de un asesor.
     */
    public static final Set<EstadoSolicitud> PARA_REVISION_MANUAL =
            EnumSet.of(PENDIENTE_REVISION, REVISION_MANUAL, RECHAZADA);

    private final String descripcion;

    EstadoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Convierte el nombre de un estado ignorando mayusculas y espacios.
     */
    public static EstadoSolicitud desde(String texto) {
        if (texto != null) {
            String normalizado = texto.trim().toUpperCase(Locale.ROOT);
            for (EstadoSolicitud estado : values()) {
                if (estado.name().equals(normalizado)) {
                    return estado;
                }
            }
        }
        throw new DatosSolicitudInvalidosException("estado", "debe ser uno de " + Arrays.toString(values()));
    }
}
