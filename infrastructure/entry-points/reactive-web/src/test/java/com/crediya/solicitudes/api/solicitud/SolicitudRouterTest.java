package com.crediya.solicitudes.api.solicitud;

import com.crediya.solicitudes.api.RouterRest;
import com.crediya.solicitudes.api.exception.GlobalErrorHandler;
import com.crediya.solicitudes.api.mapper.SolicitudRestMapperImpl;
import com.crediya.solicitudes.api.validation.RequestValidator;
import com.crediya.solicitudes.model.cliente.exceptions.ClienteNoEncontradoException;
import com.crediya.solicitudes.model.cliente.exceptions.ServicioNoDisponibleException;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.commands.CrearSolicitudCommand;
import com.crediya.solicitudes.model.solicitud.exceptions.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exceptions.MontoFueraDeRangoException;
import com.crediya.solicitudes.model.solicitud.exceptions.TipoPrestamoNoValidoException;
import com.crediya.solicitudes.model.tipoprestamo.TipoPrestamo;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("POST /api/v1/solicitud (HU2) - router, handler y manejo de errores")
class SolicitudRouterTest {

    private static final String RUTA = "/api/v1/solicitud";
    private static final String CUERPO_VALIDO = """
            {"documento_identidad":"1020304050","monto":5000000,"plazo":24,"tipo_prestamo":"PERSONAL"}""";
    private static final TipoPrestamo PERSONAL = new TipoPrestamo(1L, "PERSONAL", new BigDecimal("500000"),
            new BigDecimal("50000000"), new BigDecimal("0.018"), true);

    @Mock
    private CrearSolicitudUseCase useCase;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var handler = new SolicitudHandler(useCase, new SolicitudRestMapperImpl(),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()));
        var errorHandler = new GlobalErrorHandler(new DefaultErrorAttributes(), new WebProperties(),
                new StaticApplicationContext(), ServerCodecConfigurer.create());
        client = WebTestClient.bindToRouterFunction(new RouterRest().routerFunction(handler))
                .handlerStrategies(estrategiasConManejadorGlobal(errorHandler))
                .build();
    }

    /**
     * HandlerStrategies.builder() ya registra un manejador de ResponseStatusException que respondería antes
     * que el nuestro; en la aplicacion real el manejador global se ejecuta primero (@Order(-2)).
     */
    private static HandlerStrategies estrategiasConManejadorGlobal(GlobalErrorHandler errorHandler) {
        return HandlerStrategies.empty()
                .codecs(configurer -> configurer.registerDefaults(true))
                .exceptionHandler(errorHandler)
                .build();
    }

    private WebTestClient.ResponseSpec post(String cuerpo) {
        return client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo).exchange();
    }

    @Test
    @DisplayName("cuando el cuerpo es valido, debe responder 201 con la solicitud Pendiente de revision en snake_case")
    void cuandoCuerpoValido_debeResponder201() {
        var id = UUID.randomUUID();
        when(useCase.ejecutar(any())).thenReturn(Mono.just(new Solicitud(id, new BigDecimal("5000000"), 24,
                "maria@example.com", EstadoSolicitud.PENDIENTE_REVISION, PERSONAL)));

        post(CUERPO_VALIDO)
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id_solicitud").isEqualTo(id.toString())
                .jsonPath("$.monto").isEqualTo(5000000)
                .jsonPath("$.plazo").isEqualTo(24)
                .jsonPath("$.email").isEqualTo("maria@example.com")
                .jsonPath("$.estado").isEqualTo("PENDIENTE_REVISION")
                .jsonPath("$.tipo_prestamo").isEqualTo("PERSONAL")
                .jsonPath("$.tasa_interes").isEqualTo(0.018);

        var comando = ArgumentCaptor.forClass(CrearSolicitudCommand.class);
        verify(useCase).ejecutar(comando.capture());
        assertThat(comando.getValue().documentoIdentidad()).isEqualTo("1020304050");
        assertThat(comando.getValue().monto()).isEqualByComparingTo("5000000");
        assertThat(comando.getValue().plazo()).isEqualTo(24);
        assertThat(comando.getValue().tipoPrestamo()).isEqualTo("PERSONAL");
    }

    @Test
    @DisplayName("cuando faltan campos o son invalidos, debe responder 400 con el detalle por campo y sin llamar al caso de uso")
    void cuandoDatosInvalidos_debeResponder400ConDetalles() {
        post("""
                {"documento_identidad":"abc","monto":-10,"plazo":0}""")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("VALIDACION_FALLIDA")
                .jsonPath("$.detalles.documento_identidad").isNotEmpty()
                .jsonPath("$.detalles.monto").isNotEmpty()
                .jsonPath("$.detalles.plazo").isNotEmpty()
                .jsonPath("$.detalles.tipo_prestamo").isNotEmpty();

        verify(useCase, never()).ejecutar(any());
    }

    @Test
    @DisplayName("cuando el plazo supera 360 meses, debe responder 400")
    void cuandoPlazoSuperaMaximo_debeResponder400() {
        post(CUERPO_VALIDO.replace("\"plazo\":24", "\"plazo\":361"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detalles.plazo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando no hay cuerpo, debe responder 400 indicando que es obligatorio")
    void cuandoSinCuerpo_debeResponder400() {
        client.post().uri(RUTA).contentType(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detalles.cuerpo").isNotEmpty();
    }

    @Test
    @DisplayName("cuando el JSON esta mal formado, debe responder 400 sin detalles internos")
    void cuandoJsonMalFormado_debeResponder400() {
        post("{esto no es json")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("PETICION_INVALIDA");
    }

    @Test
    @DisplayName("cuando el tipo de contenido no es JSON, debe responder 415")
    void cuandoContentTypeNoSoportado_debeResponder415() {
        client.post().uri(RUTA).contentType(MediaType.TEXT_PLAIN).bodyValue("hola").exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    @DisplayName("cuando el cliente no existe, debe responder 404")
    void cuandoClienteNoExiste_debeResponder404() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new ClienteNoEncontradoException()));

        post(CUERPO_VALIDO)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CLIENTE_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("cuando el tipo de prestamo no existe, debe responder 400")
    void cuandoTipoNoExiste_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new TipoPrestamoNoValidoException("INEXISTENTE")));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("TIPO_PRESTAMO_NO_VALIDO")
                .jsonPath("$.mensaje").value(mensaje -> assertThat(mensaje.toString()).contains("INEXISTENTE"));
    }

    @Test
    @DisplayName("cuando el monto esta fuera del rango del tipo, debe responder 400")
    void cuandoMontoFueraDeRango_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(
                new MontoFueraDeRangoException("PERSONAL", new BigDecimal("500000"), new BigDecimal("50000000"))));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("MONTO_FUERA_DE_RANGO");
    }

    @Test
    @DisplayName("cuando el dominio rechaza los datos, debe responder 400 con el codigo del dominio")
    void cuandoDominioRechazaDatos_debeResponder400() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new DatosSolicitudInvalidosException("plazo", "es obligatorio")));

        post(CUERPO_VALIDO)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("DATOS_SOLICITUD_INVALIDOS");
    }

    @Test
    @DisplayName("cuando AUTENTICACION no responde, debe responder 503")
    void cuandoServicioNoDisponible_debeResponder503() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new ServicioNoDisponibleException("autenticacion")));

        post(CUERPO_VALIDO)
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.codigo").isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    @DisplayName("cuando ocurre un error inesperado, debe responder 500 con mensaje generico sin filtrar detalles")
    void cuandoErrorInesperado_debeResponder500Generico() {
        when(useCase.ejecutar(any())).thenReturn(Mono.error(new IllegalStateException("password=super-secreto")));

        post(CUERPO_VALIDO)
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(cuerpo -> assertThat(cuerpo)
                        .contains("ERROR_INTERNO")
                        .doesNotContain("super-secreto")
                        .doesNotContain("IllegalStateException"));
    }

    @Test
    @DisplayName("cuando la ruta no existe, debe responder 404 con el formato de error uniforme")
    void cuandoRutaInexistente_debeResponder404() {
        client.get().uri("/api/v1/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("PETICION_INVALIDA");
    }
}
