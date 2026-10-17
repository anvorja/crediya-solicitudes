package com.crediya.solicitudes.api.documentation;

import com.crediya.solicitudes.api.constants.ApiRoutes;
import com.crediya.solicitudes.api.dto.request.ActualizarEstadoRequest;
import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.dto.response.ErrorResponse;
import com.crediya.solicitudes.api.dto.response.PaginaResponse;
import com.crediya.solicitudes.api.dto.response.SolicitudResponse;
import com.crediya.solicitudes.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.securityrequirement.Builder.securityRequirementBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

/**
 * Documentacion OpenAPI separada de la logica: se pasa como parametro al router.
 */
public final class SolicitudOpenApi {

    private static final String TAG = "Solicitudes";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CREATED = String.valueOf(HttpStatus.CREATED.value());
    private static final String BAD_REQUEST = String.valueOf(HttpStatus.BAD_REQUEST.value());
    private static final String OK = String.valueOf(HttpStatus.OK.value());
    private static final String UNAUTHORIZED = String.valueOf(HttpStatus.UNAUTHORIZED.value());
    private static final String FORBIDDEN = String.valueOf(HttpStatus.FORBIDDEN.value());
    private static final String NOT_FOUND = String.valueOf(HttpStatus.NOT_FOUND.value());
    private static final String CONFLICT = String.valueOf(HttpStatus.CONFLICT.value());
    private static final String SERVER_ERROR = String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value());
    private static final String UNAVAILABLE = String.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value());

    private SolicitudOpenApi() {
    }

    public static Builder crearSolicitud(Builder builder) {
        return builder
                .operationId("crearSolicitud")
                .summary("Registrar una solicitud de prestamo")
                .description("HU2 + HU3: registra la solicitud en estado 'Pendiente de revision' validando que el cliente exista "
                        + "y que el tipo de prestamo sea uno de los existentes. Solo un CLIENTE puede crearla y unicamente a su nombre.")
                .tag(TAG)
                .security(securityRequirementBuilder().name(OpenApiConfig.ESQUEMA_BEARER))
                .response(responseBuilder().responseCode(UNAUTHORIZED).description("Falta el token o no es valido")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(FORBIDDEN).description("El rol no es CLIENTE o el documento no es el del usuario autenticado")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .requestBody(requestBodyBuilder()
                        .required(true)
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(CrearSolicitudRequest.class))))
                .response(responseBuilder().responseCode(CREATED).description("Solicitud registrada")
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(SolicitudResponse.class))))
                .response(responseBuilder().responseCode(BAD_REQUEST).description("Datos invalidos, tipo de prestamo inexistente o monto fuera de rango")
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(NOT_FOUND).description("El cliente no esta registrado")
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(UNAVAILABLE).description("El servicio de autenticacion no responde")
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(SERVER_ERROR).description("Error interno")
                        .content(contentBuilder().mediaType(JSON)
                                .schema(schemaBuilder().implementation(ErrorResponse.class))));
    }

    public static Builder actualizarEstado(Builder builder) {
        return builder
                .operationId("actualizarEstadoSolicitud")
                .summary("Aprobar o rechazar una solicitud")
                .description("HU6: un ASESOR decide una solicitud (APROBADA o RECHAZADA). El cambio y el aviso al solicitante se "
                        + "registran juntos y el correo se envia de forma asincrona. Una solicitud aprobada no puede volver a cambiar.")
                .tag(TAG)
                .security(securityRequirementBuilder().name(OpenApiConfig.ESQUEMA_BEARER))
                .requestBody(requestBodyBuilder().required(true)
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ActualizarEstadoRequest.class))))
                .response(responseBuilder().responseCode(OK).description("Solicitud con su nuevo estado")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(SolicitudResponse.class))))
                .response(responseBuilder().responseCode(BAD_REQUEST).description("Identificador o estado invalidos")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(UNAUTHORIZED).description("Falta el token o no es valido")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(FORBIDDEN).description("Solo un ASESOR puede decidir")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(NOT_FOUND).description("La solicitud no existe")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(CONFLICT).description("Transicion de estado no permitida o solicitud modificada por otro usuario")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))));
    }

    public static Builder listarSolicitudes(Builder builder) {
        return builder
                .operationId("listarSolicitudes")
                .summary("Listar solicitudes para revision manual")
                .description("HU4: lista paginada y filtrable de las solicitudes que necesitan la decision de un asesor "
                        + "(Pendiente de revision, Revision manual y Rechazada), de la mas antigua a la mas reciente. Incluye "
                        + "nombre y salario del solicitante y la deuda mensual de sus prestamos aprobados. Solo ASESOR.")
                .tag(TAG)
                .security(securityRequirementBuilder().name(OpenApiConfig.ESQUEMA_BEARER))
                .parameter(parameterBuilder().name(ApiRoutes.PARAM_ESTADO).in(ParameterIn.QUERY)
                        .description("Estados a incluir, repetidos o separados por coma. Por defecto los tres de revision.")
                        .example("PENDIENTE_REVISION,RECHAZADA").schema(schemaBuilder().implementation(String.class)))
                .parameter(parameterBuilder().name(ApiRoutes.PARAM_TIPO_PRESTAMO).in(ParameterIn.QUERY)
                        .description("Nombre del tipo de prestamo").example("PERSONAL").schema(schemaBuilder().implementation(String.class)))
                .parameter(parameterBuilder().name(ApiRoutes.PARAM_PAGINA).in(ParameterIn.QUERY)
                        .description("Pagina, empieza en 0").example("0").schema(schemaBuilder().implementation(Integer.class)))
                .parameter(parameterBuilder().name(ApiRoutes.PARAM_TAMANO).in(ParameterIn.QUERY)
                        .description("Elementos por pagina, de 1 a 50 (10 por defecto)").example("10").schema(schemaBuilder().implementation(Integer.class)))
                .response(responseBuilder().responseCode(OK).description("Pagina de solicitudes")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(PaginaResponse.class))))
                .response(responseBuilder().responseCode(BAD_REQUEST).description("Filtro, pagina o tamano invalidos")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(UNAUTHORIZED).description("Falta el token o no es valido")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))))
                .response(responseBuilder().responseCode(FORBIDDEN).description("Solo un ASESOR puede listar")
                        .content(contentBuilder().mediaType(JSON).schema(schemaBuilder().implementation(ErrorResponse.class))));
    }
}
