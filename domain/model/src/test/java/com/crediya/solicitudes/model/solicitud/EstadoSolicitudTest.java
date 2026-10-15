package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EstadoSolicitud")
class EstadoSolicitudTest {

    @Test
    @DisplayName("los estados que requieren revision manual deben ser pendiente, revision manual y rechazada (HU4)")
    void estadosParaRevisionManual() {
        assertThat(EstadoSolicitud.PARA_REVISION_MANUAL).containsExactlyInAnyOrder(
                EstadoSolicitud.PENDIENTE_REVISION, EstadoSolicitud.REVISION_MANUAL, EstadoSolicitud.RECHAZADA);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({"PENDIENTE_REVISION,PENDIENTE_REVISION", "revision_manual,REVISION_MANUAL", "' Aprobada ',APROBADA", "rechazada,RECHAZADA"})
    @DisplayName("desde debe ignorar mayusculas y espacios")
    void desde_debeIgnorarMayusculasYEspacios(String texto, EstadoSolicitud esperado) {
        assertThat(EstadoSolicitud.desde(texto)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "estado = [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "INEXISTENTE", "PENDIENTE"})
    @DisplayName("desde debe rechazar textos que no son un estado")
    void desde_debeRechazarTextosInvalidos(String texto) {
        assertThatThrownBy(() -> EstadoSolicitud.desde(texto))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .hasMessageContaining("estado");
    }
}
