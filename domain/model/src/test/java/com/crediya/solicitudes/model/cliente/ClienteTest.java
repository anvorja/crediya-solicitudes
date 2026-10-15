package com.crediya.solicitudes.model.cliente;

import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cliente y excepciones asociadas")
class ClienteTest {

    @Test
    @DisplayName("debe componer el nombre completo con nombres y apellidos")
    void debeComponerNombreCompleto() {
        var cliente = new Cliente("1020304050", "Maria", "Gomez", "maria@example.com", new BigDecimal("4500000"));

        assertThat(cliente.nombreCompleto()).isEqualTo("Maria Gomez");
    }

    @Test
    @DisplayName("las excepciones deben exponer un codigo estable y un mensaje sin datos sensibles")
    void excepciones_debenExponerCodigo() {
        var noEncontrado = new ClienteNoEncontradoException();
        var noDisponible = new ServicioNoDisponibleException("autenticacion");

        assertThat(noEncontrado.getCode()).isEqualTo(ClienteNoEncontradoException.CODE);
        assertThat(noDisponible.getCode()).isEqualTo(ServicioNoDisponibleException.CODE);
        assertThat(noDisponible.getMessage()).contains("autenticacion");
    }
}
