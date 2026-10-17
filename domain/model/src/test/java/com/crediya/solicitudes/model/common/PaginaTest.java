package com.crediya.solicitudes.model.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pagina")
class PaginaTest {

    @ParameterizedTest(name = "total={0} tamano={1} -> {2} paginas")
    @CsvSource({"0,10,0", "1,10,1", "10,10,1", "11,10,2", "95,10,10", "5,0,0"})
    @DisplayName("debe calcular el total de paginas redondeando hacia arriba")
    void debeCalcularTotalDePaginas(long total, int tamano, int esperadas) {
        assertThat(new Pagina<>(List.of(), 0, tamano, total).totalPaginas()).isEqualTo(esperadas);
    }

    @Test
    @DisplayName("mapear debe transformar los elementos y conservar la paginacion")
    void mapear_debeConservarPaginacion() {
        var pagina = new Pagina<>(List.of(1, 2, 3), 2, 3, 20).mapear(numero -> "n" + numero);

        assertThat(pagina.elementos()).containsExactly("n1", "n2", "n3");
        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.tamano()).isEqualTo(3);
        assertThat(pagina.totalElementos()).isEqualTo(20);
    }
}
