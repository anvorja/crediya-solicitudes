package com.crediya.solicitudes.model.tipoprestamo;

import java.math.BigDecimal;

public final class TipoPrestamoFixture {

    private TipoPrestamoFixture() {
    }

    public static TipoPrestamo personal() {
        return new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"), new BigDecimal("50000000"),
                new BigDecimal("0.018"), true);
    }

    public static TipoPrestamo vehiculo() {
        return new TipoPrestamo(3L, "VEHICULO", new BigDecimal("10000000"), new BigDecimal("200000000"),
                new BigDecimal("0.012"), false);
    }
}
