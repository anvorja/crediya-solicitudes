package com.crediya.solicitudes.model.tipoprestamo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TipoPrestamo")
class TipoPrestamoTest {

    @ParameterizedTest(name = "monto {0} -> admitido {1}")
    @CsvSource({
            "500000,true",
            "50000000,true",
            "25000000.50,true",
            "499999.99,false",
            "50000000.01,false",
            "0,false"
    })
    @DisplayName("debe admitir montos dentro del rango inclusivo y rechazar los de fuera")
    void debeAdmitirMontosEnRangoInclusivo(String monto, boolean esperado) {
        assertThat(TipoPrestamoFixture.personal().admiteMonto(new BigDecimal(monto))).isEqualTo(esperado);
    }
}
