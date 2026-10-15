package com.crediya.solicitudes.model.solicitud.queries;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;

import java.util.Collection;
import java.util.Set;

/**
 * HU4: criterios del listado de revision. Sin estados se usan los que requieren decision de un asesor.
 */
public record FiltroSolicitudes(Set<EstadoSolicitud> estados, String tipoPrestamo, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 10;
    public static final int TAMANO_MAXIMO = 50;

    public static FiltroSolicitudes de(Collection<EstadoSolicitud> estados, String tipoPrestamo, Integer pagina, Integer tamano) {
        Set<EstadoSolicitud> elegidos = estados == null || estados.isEmpty()
                ? EstadoSolicitud.PARA_REVISION_MANUAL
                : Set.copyOf(estados);
        if (!EstadoSolicitud.PARA_REVISION_MANUAL.containsAll(elegidos)) {
            throw new DatosSolicitudInvalidosException("estado",
                    "solo admite estados que requieren revision: " + EstadoSolicitud.PARA_REVISION_MANUAL);
        }
        int paginaElegida = pagina == null ? 0 : pagina;
        if (paginaElegida < 0) {
            throw new DatosSolicitudInvalidosException("pagina", "no puede ser negativa");
        }
        int tamanoElegido = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (tamanoElegido < 1 || tamanoElegido > TAMANO_MAXIMO) {
            throw new DatosSolicitudInvalidosException("tamano", "debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        String tipo = tipoPrestamo == null || tipoPrestamo.isBlank() ? null : tipoPrestamo.trim();
        return new FiltroSolicitudes(elegidos, tipo, paginaElegida, tamanoElegido);
    }

    public int desplazamiento() {
        return pagina * tamano;
    }
}
