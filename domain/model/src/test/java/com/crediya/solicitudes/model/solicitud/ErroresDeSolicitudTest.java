package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.commands.ActualizarEstadoSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudModificadaException;
import com.crediya.solicitudes.model.solicitud.exceptions.SolicitudNoEncontradaException;
import com.crediya.solicitudes.model.solicitud.exceptions.TransicionEstadoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Errores y datos de HU6")
class ErroresDeSolicitudTest {

    @Test
    @DisplayName("cada error expone un codigo estable y un mensaje util para el usuario")
    void codigosYMensajes() {
        assertThat(new SolicitudNoEncontradaException().getCode()).isEqualTo("SOLICITUD_NO_ENCONTRADA");
        assertThat(new SolicitudModificadaException().getCode()).isEqualTo("SOLICITUD_MODIFICADA");
        assertThat(new SolicitudModificadaException().getMessage()).contains("modificada");
        var transicion = new TransicionEstadoInvalidaException(EstadoSolicitud.APROBADA, EstadoSolicitud.RECHAZADA);
        assertThat(transicion.getCode()).isEqualTo("TRANSICION_ESTADO_INVALIDA");
        assertThat(transicion.getMessage()).contains(EstadoSolicitud.APROBADA.getDescripcion()).contains(EstadoSolicitud.RECHAZADA.getDescripcion());
    }

    @Test
    @DisplayName("el comando y la cuota del plan conservan sus datos")
    void registros() {
        var comando = new ActualizarEstadoSolicitudCommand("id", "APROBADA");
        var cuota = new CuotaPlanDePago(3, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TWO);

        assertThat(comando.idSolicitud()).isEqualTo("id");
        assertThat(comando.estado()).isEqualTo("APROBADA");
        assertThat(cuota.numero()).isEqualTo(3);
        assertThat(cuota.saldo()).isEqualTo(BigDecimal.TWO);
    }
}
