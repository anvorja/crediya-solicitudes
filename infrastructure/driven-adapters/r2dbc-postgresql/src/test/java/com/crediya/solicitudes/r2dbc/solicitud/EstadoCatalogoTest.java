package com.crediya.solicitudes.r2dbc.solicitud;

import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EstadoCatalogo")
class EstadoCatalogoTest {

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(EstadoSolicitud.class)
    @DisplayName("debe convertir estado -> id -> estado sin perdida")
    void debeIdaYVuelta(EstadoSolicitud estado) {
        assertThat(EstadoCatalogo.estadoDe(EstadoCatalogo.idDe(estado))).isEqualTo(estado);
    }

    @Test
    @DisplayName("cuando el id no existe, debe fallar de forma explicita")
    void cuandoIdDesconocido_debeFallar() {
        assertThatThrownBy(() -> EstadoCatalogo.estadoDe((short) 99))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }
}
