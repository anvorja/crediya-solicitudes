package com.crediya.solicitudes.model.tipoprestamo;

import java.math.BigDecimal;

/**
 * Producto de credito. {@code tasaInteres} es la tasa mensual expresada como fraccion (0.015 = 1.5% mensual).
 */
public record TipoPrestamo(
        Long id,
        String nombre,
        BigDecimal montoMinimo,
        BigDecimal montoMaximo,
        BigDecimal tasaInteres,
        boolean validacionAutomatica
) {

    public boolean admiteMonto(BigDecimal monto) {
        return monto.compareTo(montoMinimo) >= 0 && monto.compareTo(montoMaximo) <= 0;
    }
}
