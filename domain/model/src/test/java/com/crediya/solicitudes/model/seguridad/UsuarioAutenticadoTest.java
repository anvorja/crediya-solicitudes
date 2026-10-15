package com.crediya.solicitudes.model.seguridad;

import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsuarioAutenticado y AccesoDenegadoException")
class UsuarioAutenticadoTest {

    @ParameterizedTest(name = "rol {0} -> esCliente {1}")
    @CsvSource({"CLIENTE,true", "ASESOR,false", "ADMIN,false"})
    @DisplayName("solo el rol CLIENTE debe considerarse cliente")
    void soloClienteEsCliente(Rol rol, boolean esperado) {
        var usuario = new UsuarioAutenticado(UUID.randomUUID(), "1020304050", "maria@example.com", rol);

        assertThat(usuario.esCliente()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("el acceso denegado debe exponer un codigo estable y un mensaje generico")
    void accesoDenegado_debeExponerCodigo() {
        var excepcion = new AccesoDenegadoException();

        assertThat(excepcion.getCode()).isEqualTo("ACCESO_DENEGADO");
        assertThat(excepcion.getMessage()).doesNotContain("ROLE_");
    }
}
