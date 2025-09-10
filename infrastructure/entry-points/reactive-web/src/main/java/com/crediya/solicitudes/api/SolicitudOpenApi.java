// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/SolicitudOpenApi.java
package com.crediya.solicitudes.api;

import lombok.experimental.UtilityClass;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

/**
 * Documentación OpenAPI para endpoints de Solicitudes
 * Separación de responsabilidades: documentación separada de lógica de negocio
 * HU2: Registrar una solicitud de préstamo
 */
@UtilityClass
public class SolicitudOpenApi {

    // Constantes para códigos de respuesta HTTP
    private static final String SUCCESS = "Success";
    private static final String CREATED = "Solicitud creada exitosamente";
    private static final String BAD_REQUEST = HttpStatus.BAD_REQUEST.getReasonPhrase();
    private static final String INTERNAL_ERROR = HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase();

    private static final String SUCCESS_CODE = String.valueOf(HttpStatus.OK.value());
    private static final String CREATED_CODE = String.valueOf(HttpStatus.CREATED.value());
    private static final String BAD_REQUEST_CODE = String.valueOf(HttpStatus.BAD_REQUEST.value());
    private static final String INTERNAL_ERROR_CODE = String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value());

    /**
     * Documentación para POST /api/v1/solicitud
     * HU2: Como cliente, quiero enviar mi solicitud de préstamo
     */
    public static Builder crearSolicitud(Builder builder) {
        return builder
                .operationId("crearSolicitud")
                .description("Registrar una nueva solicitud de préstamo. " +
                        "El cliente envía su información y detalles del préstamo deseado para evaluación.")
                .tag("Solicitudes")
                .requestBody(requestBodyBuilder()
                        .required(true)
                        .description("Datos de la solicitud de préstamo")
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest.class))))
                .response(responseBuilder()
                        .responseCode(CREATED_CODE)
                        .description(CREATED)
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(com.crediya.solicitudes.api.dto.response.SolicitudResponse.class))))
                .response(responseBuilder()
                        .responseCode(BAD_REQUEST_CODE)
                        .description("Datos de solicitud inválidos: " +
                                "- Campos obligatorios faltantes, " +
                                "- Tipo de préstamo no existente, " +
                                "- Cliente ya tiene solicitud activa")
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(ErrorResponse.class))))
                .response(responseBuilder()
                        .responseCode(INTERNAL_ERROR_CODE)
                        .description(INTERNAL_ERROR)
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(ErrorResponse.class))));
    }

    /**
     * Documentación para GET /api/v1/solicitud/{id}
     * Futuras HUs - consulta individual
     */
    public static Builder consultarSolicitud(Builder builder) {
        return builder
                .operationId("consultarSolicitud")
                .description("Consultar una solicitud específica por su ID")
                .tag("Solicitudes")
                .response(responseBuilder()
                        .responseCode(SUCCESS_CODE)
                        .description(SUCCESS)
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(com.crediya.solicitudes.api.dto.response.SolicitudResponse.class))))
                .response(responseBuilder()
                        .responseCode("404")
                        .description("Solicitud no encontrada")
                        .content(contentBuilder()
                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                .schema(schemaBuilder()
                                        .implementation(ErrorResponse.class))));
    }

    /**
     * DTO estándar para respuestas de error
     */
    public static class ErrorResponse {
        public String error;
        public String message;
        public String timestamp;
        public String path;

        public ErrorResponse() {}

        public ErrorResponse(String error, String message, String path) {
            this.error = error;
            this.message = message;
            this.timestamp = java.time.LocalDateTime.now().toString();
            this.path = path;
        }

        // Getters y setters omitidos por brevedad
    }
}