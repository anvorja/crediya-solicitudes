package com.crediya.solicitudes.model.evento;

import com.crediya.solicitudes.model.solicitud.CuotaPlanDePago;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamoFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Eventos de dominio")
class SolicitudDecididaTest {

    private static final Solicitud SOLICITUD = new Solicitud(UUID.randomUUID(), new BigDecimal("5000000"), 24,
            "maria@example.com", EstadoSolicitud.APROBADA, TipoPrestamoFixture.personal());

    @Test
    @DisplayName("SolicitudDecidida sin plan de pago expone una lista vacia")
    void sinPlan_listaVacia() {
        var evento = new SolicitudDecidida(Instant.EPOCH, SOLICITUD, "Maria Gomez", null);

        assertThat(evento.planDePago()).isEmpty();
        assertThat(evento.nombreCliente()).isEqualTo("Maria Gomez");
    }

    @Test
    @DisplayName("SolicitudDecidida copia el plan de pago: cambios posteriores a la lista original no la afectan y no es modificable")
    void planDePago_esInmutable() {
        var cuota = new CuotaPlanDePago(1, new BigDecimal("258404.18"), new BigDecimal("168404.18"),
                new BigDecimal("90000.00"), new BigDecimal("4831595.82"));
        var original = new ArrayList<>(java.util.List.of(cuota));

        var evento = new SolicitudDecidida(Instant.EPOCH, SOLICITUD, null, original);
        original.clear();

        assertThat(evento.planDePago()).containsExactly(cuota);
        assertThatThrownBy(() -> evento.planDePago().add(cuota)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("EventoSaliente conserva su destino, payload e intentos")
    void eventoSaliente() {
        var id = UUID.randomUUID();
        var saliente = new EventoSaliente(id, Destino.NOTIFICACIONES, "{}", 2);

        assertThat(saliente.id()).isEqualTo(id);
        assertThat(saliente.destino()).isEqualTo(Destino.NOTIFICACIONES);
        assertThat(saliente.payload()).isEqualTo("{}");
        assertThat(saliente.intentos()).isEqualTo(2);
        assertThat(Destino.valueOf("NOTIFICACIONES")).isSameAs(Destino.NOTIFICACIONES);
    }
}
