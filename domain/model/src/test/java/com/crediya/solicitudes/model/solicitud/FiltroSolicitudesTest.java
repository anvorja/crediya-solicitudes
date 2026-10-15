package com.crediya.solicitudes.model.solicitud;

import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FiltroSolicitudes (HU4)")
class FiltroSolicitudesTest {

    @Test
    @DisplayName("sin criterios debe usar los estados de revision manual, pagina 0 y tamano por defecto")
    void sinCriterios_debeUsarValoresPorDefecto() {
        var filtro = FiltroSolicitudes.de(null, null, null, null);

        assertThat(filtro.estados()).isEqualTo(EstadoSolicitud.PARA_REVISION_MANUAL);
        assertThat(filtro.tipoPrestamo()).isNull();
        assertThat(filtro.pagina()).isZero();
        assertThat(filtro.tamano()).isEqualTo(10);
        assertThat(filtro.desplazamiento()).isZero();
    }

    @Test
    @DisplayName("una lista de estados vacia equivale a no filtrar")
    void listaVacia_equivaleANoFiltrar() {
        assertThat(FiltroSolicitudes.de(List.of(), null, 0, 10).estados()).isEqualTo(EstadoSolicitud.PARA_REVISION_MANUAL);
    }

    @Test
    @DisplayName("debe respetar los estados, el tipo (sin espacios), la pagina y el tamano pedidos y calcular el desplazamiento")
    void debeRespetarLosCriterios() {
        var filtro = FiltroSolicitudes.de(List.of(EstadoSolicitud.RECHAZADA), "  PERSONAL ", 3, 25);

        assertThat(filtro.estados()).isEqualTo(Set.of(EstadoSolicitud.RECHAZADA));
        assertThat(filtro.tipoPrestamo()).isEqualTo("PERSONAL");
        assertThat(filtro.pagina()).isEqualTo(3);
        assertThat(filtro.tamano()).isEqualTo(25);
        assertThat(filtro.desplazamiento()).isEqualTo(75);
    }

    @Test
    @DisplayName("un tipo de prestamo en blanco equivale a no filtrar por tipo")
    void tipoEnBlanco_equivaleANoFiltrar() {
        assertThat(FiltroSolicitudes.de(null, "   ", 0, 10).tipoPrestamo()).isNull();
    }

    @Test
    @DisplayName("no debe admitir el estado Aprobada: el listado es solo de solicitudes pendientes de decision")
    void noDebeAdmitirAprobada() {
        assertThatThrownBy(() -> FiltroSolicitudes.de(List.of(EstadoSolicitud.APROBADA), null, 0, 10))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .hasMessageContaining("estado");
    }

    @Test
    @DisplayName("no debe admitir una pagina negativa")
    void noDebeAdmitirPaginaNegativa() {
        assertThatThrownBy(() -> FiltroSolicitudes.de(null, null, -1, 10))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .hasMessageContaining("pagina");
    }

    @ParameterizedTest(name = "tamano = {0}")
    @ValueSource(ints = {0, -5, 51, 1000})
    @DisplayName("no debe admitir un tamano fuera de 1 y 50 (acota el costo de la consulta)")
    void noDebeAdmitirTamanoFueraDeRango(int tamano) {
        assertThatThrownBy(() -> FiltroSolicitudes.de(null, null, 0, tamano))
                .isInstanceOf(DatosSolicitudInvalidosException.class)
                .hasMessageContaining("tamano");
    }

    @ParameterizedTest(name = "tamano = {0}")
    @ValueSource(ints = {1, 50})
    @DisplayName("debe admitir los tamanos limite")
    void debeAdmitirTamanosLimite(int tamano) {
        assertThat(FiltroSolicitudes.de(null, null, 0, tamano).tamano()).isEqualTo(tamano);
    }
}
