package com.crediya.solicitudes.model.solicitud;

import java.math.BigDecimal;

/**
 * Una cuota del plan de pago, diferenciando el abono a capital del pago de intereses.
 */
public record CuotaPlanDePago(int numero, BigDecimal cuota, BigDecimal capital, BigDecimal interes, BigDecimal saldo) {
}
