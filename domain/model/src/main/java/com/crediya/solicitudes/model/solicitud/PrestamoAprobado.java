package com.crediya.solicitudes.model.solicitud;

import java.math.BigDecimal;

/**
 * Un prestamo ya aprobado del solicitante: parte de su deuda mensual al evaluar una nueva solicitud.
 */
public record PrestamoAprobado(BigDecimal monto, int plazo, BigDecimal tasaInteres) {

    public static PrestamoAprobado de(Solicitud solicitud) {
        return new PrestamoAprobado(solicitud.monto(), solicitud.plazo(), solicitud.tipoPrestamo().tasaInteres());
    }

    public BigDecimal cuotaMensual() {
        return Amortizacion.cuotaMensual(monto, tasaInteres, plazo);
    }
}
