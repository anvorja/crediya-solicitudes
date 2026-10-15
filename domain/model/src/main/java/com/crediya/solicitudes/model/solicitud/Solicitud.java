package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.MontoFueraDeRangoException;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;

import java.math.BigDecimal;
import java.util.UUID;

public record Solicitud(
        UUID id,
        BigDecimal monto,
        int plazo,
        String email,
        EstadoSolicitud estado,
        TipoPrestamo tipoPrestamo
) {

    public static final int PLAZO_MINIMO_MESES = 1;
    public static final int PLAZO_MAXIMO_MESES = 360;

    /**
     * HU2: crea una solicitud nueva (sin id, lo asigna la persistencia) en estado "Pendiente de revision".
     */
    public static Solicitud crear(String email, BigDecimal monto, Integer plazo, TipoPrestamo tipoPrestamo) {
        if (email == null || email.isBlank()) {
            throw new DatosSolicitudInvalidosException("email", "es obligatorio");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new DatosSolicitudInvalidosException("monto", "debe ser mayor a cero");
        }
        if (plazo == null || plazo < PLAZO_MINIMO_MESES || plazo > PLAZO_MAXIMO_MESES) {
            throw new DatosSolicitudInvalidosException("plazo",
                    "debe estar entre " + PLAZO_MINIMO_MESES + " y " + PLAZO_MAXIMO_MESES + " meses");
        }
        if (!tipoPrestamo.admiteMonto(monto)) {
            throw new MontoFueraDeRangoException(tipoPrestamo.nombre(), tipoPrestamo.montoMinimo(), tipoPrestamo.montoMaximo());
        }
        return new Solicitud(null, monto, plazo, email.trim().toLowerCase(), EstadoSolicitud.PENDIENTE_REVISION, tipoPrestamo);
    }

    public Solicitud conId(UUID nuevoId) {
        return new Solicitud(nuevoId, monto, plazo, email, estado, tipoPrestamo);
    }
}
