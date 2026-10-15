package com.crediya.solicitudes.model.solicitud;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Cuota fija de un prestamo con el sistema frances de amortizacion.
 * <p>
 * Cuota = P * i * (1 + i)^n / ((1 + i)^n - 1), con P = monto, i = tasa de interes mensual y n = plazo en meses.
 */
public final class Amortizacion {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final int ESCALA_MONEDA = 2;

    private Amortizacion() {
    }

    public static BigDecimal cuotaMensual(BigDecimal monto, BigDecimal tasaMensual, int plazoMeses) {
        if (tasaMensual.signum() == 0) {
            return monto.divide(BigDecimal.valueOf(plazoMeses), ESCALA_MONEDA, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(plazoMeses, PRECISION);
        return monto.multiply(tasaMensual).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), PRECISION)
                .setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
    }
}
