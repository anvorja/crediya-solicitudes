package com.crediya.solicitudes.model.solicitud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Amortizacion - cuota mensual del sistema frances")
class AmortizacionTest {

    /**
     * Valores de referencia calculados de forma independiente (Python, Decimal de 50 digitos).
     */
    @ParameterizedTest(name = "monto={0} tasa={1} plazo={2} -> cuota {3}")
    @CsvSource({
            "5000000,0.018,24,258404.18",
            "1000000,0.012,12,89975.43",
            "12000000,0.009,60,259713.77",
            "100000000,0.009,360,937241.90",
            "500000,0.022,1,511000.00",
            "600000,0,12,50000.00"
    })
    @DisplayName("debe coincidir con los valores de referencia, redondeando a centavos")
    void debeCoincidirConValoresDeReferencia(String monto, String tasa, int plazo, String esperada) {
        var cuota = Amortizacion.cuotaMensual(new BigDecimal(monto), new BigDecimal(tasa), plazo);

        assertThat(cuota).isEqualByComparingTo(esperada);
        assertThat(cuota.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("con tasa cero la cuota es el monto dividido por el plazo, redondeado a centavos")
    void conTasaCero_debeDividirElMontoEnPartesIguales() {
        assertThat(Amortizacion.cuotaMensual(new BigDecimal("1000"), BigDecimal.ZERO, 3)).isEqualByComparingTo("333.33");
    }

    @Test
    @DisplayName("el total pagado con cuota fija debe superar el monto prestado cuando hay interes")
    void totalPagado_debeSuperarElMontoConInteres() {
        var monto = new BigDecimal("5000000");
        var cuota = Amortizacion.cuotaMensual(monto, new BigDecimal("0.018"), 24);

        assertThat(cuota.multiply(BigDecimal.valueOf(24))).isGreaterThan(monto);
    }

    @Test
    @DisplayName("a mayor tasa la cuota debe ser mayor y a mayor plazo la cuota debe ser menor")
    void cuota_debeCrecerConLaTasaYBajarConElPlazo() {
        var monto = new BigDecimal("5000000");

        var baja = Amortizacion.cuotaMensual(monto, new BigDecimal("0.010"), 24);
        var alta = Amortizacion.cuotaMensual(monto, new BigDecimal("0.020"), 24);
        var corta = Amortizacion.cuotaMensual(monto, new BigDecimal("0.010"), 12);

        assertThat(alta).isGreaterThan(baja);
        assertThat(corta).isGreaterThan(baja);
    }
}
