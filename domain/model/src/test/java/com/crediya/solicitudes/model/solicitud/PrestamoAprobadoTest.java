package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.evento.ValidacionAutomaticaSolicitada;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamoFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrestamoAprobado y pedido de validacion automatica (HU7)")
class PrestamoAprobadoTest {

    @Test
    @DisplayName("se construye desde una solicitud aprobada y su cuota mensual usa el sistema frances")
    void desdeSolicitud() {
        var tipo = TipoPrestamoFixture.personal();
        var solicitud = new Solicitud(UUID.randomUUID(), new BigDecimal("5000000"), 24, "maria@example.com", EstadoSolicitud.APROBADA, tipo);

        var prestamo = PrestamoAprobado.de(solicitud);

        assertThat(prestamo.monto()).isEqualByComparingTo("5000000");
        assertThat(prestamo.plazo()).isEqualTo(24);
        assertThat(prestamo.tasaInteres()).isEqualByComparingTo(tipo.tasaInteres());
        assertThat(prestamo.cuotaMensual()).isEqualByComparingTo(Amortizacion.cuotaMensual(new BigDecimal("5000000"), tipo.tasaInteres(), 24));
    }

    @Test
    @DisplayName("el pedido de validacion copia la lista de prestamos y la deja vacia si no hay")
    void pedidoDeValidacion() {
        var solicitud = new Solicitud(UUID.randomUUID(), BigDecimal.TEN, 12, "a@b.co", EstadoSolicitud.PENDIENTE_REVISION, TipoPrestamoFixture.personal());

        var sinPrestamos = new ValidacionAutomaticaSolicitada(Instant.EPOCH, solicitud, null, BigDecimal.TEN, null);

        assertThat(sinPrestamos.prestamosAprobados()).isEmpty();
    }
}
