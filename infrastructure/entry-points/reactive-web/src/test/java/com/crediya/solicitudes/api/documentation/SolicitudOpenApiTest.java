package com.crediya.solicitudes.api.documentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springdoc.core.fn.builders.operation.Builder.operationBuilder;

@DisplayName("SolicitudOpenApi")
class SolicitudOpenApiTest {

    private static List<String> codigos(Operation operacion) {
        return Arrays.stream(operacion.responses()).map(ApiResponse::responseCode).toList();
    }

    private static List<String> esquemasDeSeguridad(Operation operacion) {
        return Arrays.stream(operacion.security()).map(SecurityRequirement::name).toList();
    }

    @Test
    @DisplayName("crearSolicitud debe documentar el esquema Bearer y cada estado que puede devolver")
    void crearSolicitud_debeDocumentarEstados() {
        Operation operacion = SolicitudOpenApi.crearSolicitud(operationBuilder()).build().getOperation();

        assertThat(operacion.operationId()).isEqualTo("crearSolicitud");
        assertThat(esquemasDeSeguridad(operacion)).containsExactly("bearerAuth");
        assertThat(codigos(operacion)).containsExactlyInAnyOrder("201", "400", "401", "403", "404", "503", "500");
    }

    @Test
    @DisplayName("actualizarEstado debe documentar el esquema Bearer y cada estado que puede devolver")
    void actualizarEstado_debeDocumentarEstados() {
        Operation operacion = SolicitudOpenApi.actualizarEstado(operationBuilder()).build().getOperation();

        assertThat(operacion.operationId()).isEqualTo("actualizarEstadoSolicitud");
        assertThat(esquemasDeSeguridad(operacion)).containsExactly("bearerAuth");
        assertThat(codigos(operacion)).containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409");
    }

    @Test
    @DisplayName("listarSolicitudes debe documentar el esquema Bearer, sus cuatro parametros y cada estado que puede devolver")
    void listarSolicitudes_debeDocumentarEstadosYParametros() {
        Operation operacion = SolicitudOpenApi.listarSolicitudes(operationBuilder()).build().getOperation();

        assertThat(operacion.operationId()).isEqualTo("listarSolicitudes");
        assertThat(esquemasDeSeguridad(operacion)).containsExactly("bearerAuth");
        assertThat(codigos(operacion)).containsExactlyInAnyOrder("200", "400", "401", "403");
        assertThat(operacion.parameters()).extracting(parametro -> parametro.name())
                .containsExactlyInAnyOrder("estado", "tipo_prestamo", "pagina", "tamano");
    }

    @Test
    @DisplayName("todo error documentado debe usar el esquema ErrorResponse")
    void errores_debenUsarElEsquemaDeError() {
        Operation operacion = SolicitudOpenApi.actualizarEstado(operationBuilder()).build().getOperation();

        List<ApiResponse> errores = Arrays.stream(operacion.responses())
                .filter(respuesta -> Integer.parseInt(respuesta.responseCode()) >= 400)
                .toList();

        assertThat(errores).isNotEmpty()
                .allSatisfy(respuesta -> assertThat(respuesta.content()[0].schema().implementation().getSimpleName())
                        .isEqualTo("ErrorResponse"));
    }
}
